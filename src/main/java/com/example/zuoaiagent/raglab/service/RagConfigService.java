package com.example.zuoaiagent.raglab.service;

import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.raglab.entity.RagConfigDO;
import com.example.zuoaiagent.raglab.entity.RagConfigVersionDO;
import com.example.zuoaiagent.raglab.mapper.RagConfigMapper;
import com.example.zuoaiagent.raglab.mapper.RagConfigVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * RAG 配置管理服务
 *
 * <p>提供配置的 CRUD、版本管理、回滚等功能。
 * 每次修改配置时自动创建版本快照，支持回滚到任意历史版本。
 */
@Service
public class RagConfigService {

    private static final Logger log = LoggerFactory.getLogger(RagConfigService.class);

    private final RagConfigMapper configMapper;
    private final RagConfigVersionMapper versionMapper;
    private final RagConfigLoader configLoader;
    private final ObjectMapper objectMapper;

    public RagConfigService(RagConfigMapper configMapper,
                            RagConfigVersionMapper versionMapper,
                            RagConfigLoader configLoader,
                            ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.versionMapper = versionMapper;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取当前生效的配置。
     */
    public RagConfigDO getActiveConfig() {
        RagConfigDO config = configLoader.getActiveConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.RAG_CONFIG_NO_ACTIVE);
        }
        return config;
    }

    /**
     * 获取所有配置列表（含非生效的）。
     */
    public List<RagConfigDO> listAll() {
        return configMapper.selectList(
                new LambdaQueryWrapper<RagConfigDO>().orderByDesc(RagConfigDO::getCreateTime));
    }

    /**
     * 获取指定 ID 的配置。
     */
    public RagConfigDO getById(Long id) {
        RagConfigDO config = configMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.RAG_CONFIG_NOT_FOUND);
        }
        return config;
    }

    /**
     * 更新配置（自动创建版本快照）。
     *
     * @param config    更新后的配置对象
     * @param changeLog 变更说明
     * @return 更新后的配置
     */
    @Transactional
    public RagConfigDO updateConfig(RagConfigDO config, String changeLog) {
        RagConfigDO existing = configMapper.selectById(config.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.RAG_CONFIG_NOT_FOUND);
        }

        // 1. 创建版本快照
        createVersionSnapshot(existing.getId(), changeLog);

        // 2. 更新配置
        config.setUpdateTime(new Date());
        configMapper.updateById(config);

        // 3. 刷新缓存
        configLoader.refresh();

        log.info("[RagConfigService] 配置更新成功: id={}, changeLog={}", config.getId(), changeLog);
        return configMapper.selectById(config.getId());
    }

    /**
     * 保存新配置（不设为生效）。
     */
    public RagConfigDO saveNew(RagConfigDO config) {
        config.setIsActive((short) 0);
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());
        configMapper.insert(config);
        return config;
    }

    /**
     * 切换生效配置。
     *
     * @param configId 要设为生效的配置 ID
     */
    @Transactional
    public void activateConfig(Long configId) {
        // 取消当前生效
        LambdaQueryWrapper<RagConfigDO> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(RagConfigDO::getIsActive, (short) 1);
        List<RagConfigDO> actives = configMapper.selectList(activeWrapper);
        for (RagConfigDO active : actives) {
            active.setIsActive((short) 0);
            active.setUpdateTime(new Date());
            configMapper.updateById(active);
        }

        // 设为生效
        RagConfigDO config = configMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ErrorCode.RAG_CONFIG_NOT_FOUND);
        }
        config.setIsActive((short) 1);
        config.setUpdateTime(new Date());
        configMapper.updateById(config);

        // 刷新缓存
        configLoader.refresh();

        log.info("[RagConfigService] 配置切换生效: id={}, name={}", configId, config.getConfigName());
    }

    /**
     * 获取配置版本历史。
     * 如果没有历史记录，自动创建当前版本作为 V1 基线。
     */
    public List<RagConfigVersionDO> getVersionHistory(Long configId) {
        List<RagConfigVersionDO> versions = versionMapper.selectList(
                new LambdaQueryWrapper<RagConfigVersionDO>()
                        .eq(RagConfigVersionDO::getConfigId, configId)
                        .orderByDesc(RagConfigVersionDO::getVersionNo));

        // 如果没有历史版本，自动创建当前版本作为 V1 基线
        if (versions.isEmpty()) {
            RagConfigDO config = configMapper.selectById(configId);
            if (config != null) {
                try {
                    RagConfigVersionDO v1 = new RagConfigVersionDO();
                    v1.setConfigId(configId);
                    v1.setVersionNo(1);
                    v1.setSnapshotData(objectMapper.writeValueAsString(config));
                    v1.setChangeLog("初始版本（系统自动生成）");
                    v1.setCreateTime(new Date());
                    versionMapper.insert(v1);
                    versions.add(v1);
                    log.info("[RagConfigService] 为配置 {} 创建了初始版本 V1", configId);
                } catch (JsonProcessingException e) {
                    log.error("[RagConfigService] 创建初始版本快照失败: configId={}", configId, e);
                }
            }
        }

        return versions;
    }

    /**
     * 回滚配置到指定版本。
     *
     * @param configId 配置 ID
     * @param versionId 版本 ID
     * @return 回滚后的配置
     */
    @Transactional
    public RagConfigDO rollbackToVersion(Long configId, Long versionId) {
        RagConfigVersionDO version = versionMapper.selectById(versionId);
        if (version == null || !version.getConfigId().equals(configId)) {
            throw new BusinessException(ErrorCode.RAG_VERSION_NOT_FOUND);
        }

        try {
            RagConfigDO snapshot = objectMapper.readValue(version.getSnapshotData(), RagConfigDO.class);
            snapshot.setId(configId);
            snapshot.setUpdateTime(new Date());
            configMapper.updateById(snapshot);

            configLoader.refresh();
            log.info("[RagConfigService] 配置回滚成功: configId={}, versionId={}, versionNo={}",
                    configId, versionId, version.getVersionNo());
            return configMapper.selectById(configId);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本快照数据解析失败");
        }
    }

    // -------------------- 私有方法 --------------------

    private void createVersionSnapshot(Long configId, String changeLog) {
        RagConfigDO config = configMapper.selectById(configId);
        if (config == null) return;

        // 获取当前最大版本号
        LambdaQueryWrapper<RagConfigVersionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagConfigVersionDO::getConfigId, configId)
                .orderByDesc(RagConfigVersionDO::getVersionNo)
                .last("LIMIT 1");
        RagConfigVersionDO latest = versionMapper.selectOne(wrapper);
        int nextVersion = (latest != null) ? latest.getVersionNo() + 1 : 1;

        RagConfigVersionDO versionDO = new RagConfigVersionDO();
        versionDO.setConfigId(configId);
        versionDO.setVersionNo(nextVersion);
        try {
            versionDO.setSnapshotData(objectMapper.writeValueAsString(config));
        } catch (JsonProcessingException e) {
            log.error("[RagConfigService] 版本快照序列化失败: configId={}", configId, e);
            return;
        }
        versionDO.setChangeLog(changeLog);
        versionDO.setCreateTime(new Date());
        versionMapper.insert(versionDO);
    }
}

package com.example.zuoaiagent.raglab.service;

import com.example.zuoaiagent.raglab.entity.RagConfigDO;
import com.example.zuoaiagent.raglab.mapper.RagConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 配置加载器（热加载）
 *
 * <p>从数据库加载当前生效的 RAG 流水线配置，使用 ConcurrentHashMap 缓存，
 * 配置更新后通过 {@link #refresh()} 刷新缓存，无需重启服务。
 */
@Service
public class RagConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(RagConfigLoader.class);

    private final RagConfigMapper configMapper;
    private final ConcurrentHashMap<String, RagConfigDO> cache = new ConcurrentHashMap<>();

    public RagConfigLoader(RagConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * 获取当前生效的 RAG 配置（带缓存）。
     *
     * <p>首次调用或缓存被清除后从数据库读取，后续调用直接返回缓存对象。
     *
     * @return 当前生效的配置，无配置时返回 null
     */
    public RagConfigDO getActiveConfig() {
        return cache.computeIfAbsent("active", k -> loadActiveFromDb());
    }

    /**
     * 刷新配置缓存。
     *
     * <p>配置被修改后调用此方法，下次 {@link #getActiveConfig()} 将重新从数据库加载。
     */
    public void refresh() {
        cache.clear();
        log.info("[RagConfigLoader] 配置缓存已清除，下次访问将重新加载");
    }

    /**
     * 根据 ID 获取配置（不走缓存，用于版本回滚后加载指定配置）。
     */
    public RagConfigDO getById(Long id) {
        return configMapper.selectById(id);
    }

    // -------------------- 私有方法 --------------------

    private RagConfigDO loadActiveFromDb() {
        LambdaQueryWrapper<RagConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagConfigDO::getIsActive, (short) 1)
                .last("LIMIT 1");
        RagConfigDO config = configMapper.selectOne(wrapper);
        if (config == null) {
            log.warn("[RagConfigLoader] 数据库中无生效的 RAG 配置，将使用内置默认值");
        } else {
            log.info("[RagConfigLoader] 加载生效配置: id={}, name={}", config.getId(), config.getConfigName());
        }
        return config;
    }
}

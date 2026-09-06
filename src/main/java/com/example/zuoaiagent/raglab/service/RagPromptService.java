package com.example.zuoaiagent.raglab.service;

import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.raglab.entity.RagPromptTemplateDO;
import com.example.zuoaiagent.raglab.entity.RagPromptVersionDO;
import com.example.zuoaiagent.raglab.mapper.RagPromptTemplateMapper;
import com.example.zuoaiagent.raglab.mapper.RagPromptVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 提示词管理服务
 *
 * <p>管理所有 RAG 阶段的提示词模板，支持版本管理、回滚。
 * 提供模板缓存，供 {@link com.example.zuoaiagent.prompt.DbPromptTemplateLoader} 读取。
 */
@Service
public class RagPromptService {

    private static final Logger log = LoggerFactory.getLogger(RagPromptService.class);

    private final RagPromptTemplateMapper templateMapper;
    private final RagPromptVersionMapper versionMapper;
    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    public RagPromptService(RagPromptTemplateMapper templateMapper,
                            RagPromptVersionMapper versionMapper) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
    }

    /**
     * 获取所有提示词模板。
     */
    public List<RagPromptTemplateDO> listAll() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<RagPromptTemplateDO>()
                        .eq(RagPromptTemplateDO::getIsActive, (short) 1)
                        .orderByAsc(RagPromptTemplateDO::getPromptType));
    }

    /**
     * 根据类型获取模板。
     */
    public RagPromptTemplateDO getByType(String promptType) {
        LambdaQueryWrapper<RagPromptTemplateDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagPromptTemplateDO::getPromptType, promptType)
                .eq(RagPromptTemplateDO::getIsActive, (short) 1)
                .last("LIMIT 1");
        RagPromptTemplateDO template = templateMapper.selectOne(wrapper);
        if (template == null) {
            throw new BusinessException(ErrorCode.RAG_PROMPT_NOT_FOUND, "提示词类型: " + promptType);
        }
        return template;
    }

    /**
     * 获取指定类型的模板内容（用于渲染）。
     *
     * <p>优先从缓存读取，缓存未命中时从数据库加载。
     */
    public String getTemplateContent(String promptType) {
        return templateCache.computeIfAbsent(promptType, k -> {
            RagPromptTemplateDO template = getByType(k);
            return template.getTemplateContent();
        });
    }

    /**
     * 更新提示词模板（自动创建版本快照）。
     *
     * @param promptType  提示词类型
     * @param newContent  新的模板内容
     * @param templateName 模板名称（可更新）
     * @param changeLog   变更说明
     * @return 更新后的模板
     */
    @Transactional
    public RagPromptTemplateDO updateTemplate(String promptType, String newContent,
                                               String templateName, String changeLog) {
        RagPromptTemplateDO existing = getByType(promptType);

        // 1. 创建版本快照
        createVersionSnapshot(existing.getId(), changeLog);

        // 2. 更新模板
        existing.setTemplateContent(newContent);
        if (templateName != null && !templateName.isBlank()) {
            existing.setTemplateName(templateName);
        }
        existing.setUpdateTime(new Date());
        templateMapper.updateById(existing);

        // 3. 清除缓存
        templateCache.remove(promptType);

        log.info("[RagPromptService] 提示词更新成功: type={}, changeLog={}", promptType, changeLog);
        return templateMapper.selectById(existing.getId());
    }

    /**
     * 获取提示词版本历史。
     * 如果没有历史记录，自动创建当前版本作为 V1 基线。
     */
    public List<RagPromptVersionDO> getVersionHistory(Long promptId) {
        List<RagPromptVersionDO> versions = versionMapper.selectList(
                new LambdaQueryWrapper<RagPromptVersionDO>()
                        .eq(RagPromptVersionDO::getPromptId, promptId)
                        .orderByDesc(RagPromptVersionDO::getVersionNo));

        // 如果没有历史版本，自动创建当前版本作为 V1 基线
        if (versions.isEmpty()) {
            RagPromptTemplateDO template = templateMapper.selectById(promptId);
            if (template != null) {
                RagPromptVersionDO v1 = new RagPromptVersionDO();
                v1.setPromptId(promptId);
                v1.setVersionNo(1);
                v1.setTemplateContent(template.getTemplateContent());
                v1.setChangeLog("初始版本（系统自动生成）");
                v1.setCreateTime(new Date());
                versionMapper.insert(v1);
                versions.add(v1);
                log.info("[RagPromptService] 为提示词 {} 创建了初始版本 V1", promptId);
            }
        }

        return versions;
    }

    /**
     * 回滚提示词到指定版本。
     *
     * @param promptId  提示词 ID
     * @param versionId 版本 ID
     * @return 回滚后的模板
     */
    @Transactional
    public RagPromptTemplateDO rollbackToVersion(Long promptId, Long versionId) {
        RagPromptVersionDO version = versionMapper.selectById(versionId);
        if (version == null || !version.getPromptId().equals(promptId)) {
            throw new BusinessException(ErrorCode.RAG_VERSION_NOT_FOUND);
        }

        RagPromptTemplateDO template = templateMapper.selectById(promptId);
        if (template == null) {
            throw new BusinessException(ErrorCode.RAG_PROMPT_NOT_FOUND);
        }

        template.setTemplateContent(version.getTemplateContent());
        template.setUpdateTime(new Date());
        templateMapper.updateById(template);

        // 清除缓存
        templateCache.remove(template.getPromptType());

        log.info("[RagPromptService] 提示词回滚成功: promptId={}, versionId={}, versionNo={}",
                promptId, versionId, version.getVersionNo());
        return template;
    }

    /**
     * 清除模板缓存（外部调用，如配置热加载后同步清理）。
     */
    public void clearCache() {
        templateCache.clear();
        log.info("[RagPromptService] 模板缓存已清除");
    }

    // -------------------- 私有方法 --------------------

    private void createVersionSnapshot(Long promptId, String changeLog) {
        RagPromptTemplateDO template = templateMapper.selectById(promptId);
        if (template == null) return;

        LambdaQueryWrapper<RagPromptVersionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagPromptVersionDO::getPromptId, promptId)
                .orderByDesc(RagPromptVersionDO::getVersionNo)
                .last("LIMIT 1");
        RagPromptVersionDO latest = versionMapper.selectOne(wrapper);
        int nextVersion = (latest != null) ? latest.getVersionNo() + 1 : 1;

        RagPromptVersionDO versionDO = new RagPromptVersionDO();
        versionDO.setPromptId(promptId);
        versionDO.setVersionNo(nextVersion);
        versionDO.setTemplateContent(template.getTemplateContent());
        versionDO.setChangeLog(changeLog);
        versionDO.setCreateTime(new Date());
        versionMapper.insert(versionDO);
    }
}

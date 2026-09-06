package com.example.zuoaiagent.raglab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.raglab.entity.RagTestQuestionDO;
import com.example.zuoaiagent.raglab.entity.RagTestDocumentDO;
import com.example.zuoaiagent.raglab.mapper.RagTestQuestionMapper;
import com.example.zuoaiagent.raglab.mapper.RagTestDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 测试题库管理服务
 */
@Service
public class RagTestQuestionService {

    private static final Logger log = LoggerFactory.getLogger(RagTestQuestionService.class);

    private final RagTestQuestionMapper questionMapper;
    private final RagTestDocumentMapper documentMapper;

    public RagTestQuestionService(RagTestQuestionMapper questionMapper,
                                   RagTestDocumentMapper documentMapper) {
        this.questionMapper = questionMapper;
        this.documentMapper = documentMapper;
    }

    // ==================== 测试题目 ====================

    public List<RagTestQuestionDO> listQuestions() {
        return questionMapper.selectList(new LambdaQueryWrapper<RagTestQuestionDO>().orderByAsc(RagTestQuestionDO::getId));
    }

    public RagTestQuestionDO getQuestionById(Long id) {
        return questionMapper.selectById(id);
    }

    public RagTestQuestionDO createQuestion(RagTestQuestionDO question) {
        questionMapper.insert(question);
        return question;
    }

    public RagTestQuestionDO updateQuestion(RagTestQuestionDO question) {
        questionMapper.updateById(question);
        return questionMapper.selectById(question.getId());
    }

    public void deleteQuestion(Long id) {
        questionMapper.deleteById(id);
    }

    public List<RagTestQuestionDO> getQuestionsByIds(List<Long> ids) {
        return questionMapper.selectBatchIds(ids);
    }

    // ==================== 测试文档 ====================

    public List<RagTestDocumentDO> listDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<RagTestDocumentDO>().orderByAsc(RagTestDocumentDO::getId));
    }

    public RagTestDocumentDO getDocumentById(Long id) {
        return documentMapper.selectById(id);
    }

    public RagTestDocumentDO createDocument(RagTestDocumentDO document) {
        documentMapper.insert(document);
        return document;
    }

    public RagTestDocumentDO updateDocument(RagTestDocumentDO document) {
        documentMapper.updateById(document);
        return documentMapper.selectById(document.getId());
    }

    public void deleteDocument(Long id) {
        documentMapper.deleteById(id);
    }
}

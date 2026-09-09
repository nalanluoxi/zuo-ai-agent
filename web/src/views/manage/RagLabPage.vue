<template>
  <div class="rag-lab-page">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab 1: 流水线控制 -->
      <el-tab-pane label="流水线控制" name="pipeline">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">RAG 流水线参数配置</span>
              <div style="display:flex;gap:8px;align-items:center">
                <el-button size="small" @click="showConfigVersions">版本历史</el-button>
                <el-button size="small" type="warning" @click="createReleaseFromConfig">发起发布</el-button>
                <el-button size="small" type="primary" @click="saveConfig" :loading="configSaving">保存配置</el-button>
              </div>
            </div>
          </template>

          <el-form :model="configForm" label-width="180px" v-loading="configLoading">
            <!-- 意图分类 -->
            <el-divider content-position="left">意图分类</el-divider>
            <el-form-item label="意图识别置信度阈值">
              <el-slider v-model="configForm.intentConfidenceThreshold" :min="0" :max="1" :step="0.05" show-input />
              <div class="param-desc">低于此值时不走 RAG 通道，直接走闲聊</div>
            </el-form-item>

            <!-- 检索参数 -->
            <el-divider content-position="left">检索参数</el-divider>
            <el-form-item label="全局检索 TopK">
              <el-input-number v-model="configForm.retrieveGlobalTopK" :min="1" :max="20" />
              <div class="param-desc">全局向量通道召回数量</div>
            </el-form-item>
            <el-form-item label="意图检索 TopK">
              <el-input-number v-model="configForm.retrieveIntentTopK" :min="1" :max="20" />
              <div class="param-desc">意图导向向量通道召回数量</div>
            </el-form-item>
            <el-form-item label="全文检索 TopK">
              <el-input-number v-model="configForm.retrieveFulltextTopK" :min="1" :max="20" />
              <div class="param-desc">全文检索通道召回数量</div>
            </el-form-item>
            <el-form-item label="检索超时（秒）">
              <el-input-number v-model="configForm.retrieveTimeoutSec" :min="1" :max="30" />
            </el-form-item>
            <el-form-item label="RRF 融合参数 k">
              <el-input-number v-model="configForm.rrfK" :min="1" :max="200" :step="1" />
              <div class="param-desc">Reciprocal Rank Fusion 参数，值越大排名越平滑</div>
            </el-form-item>

            <!-- Rerank -->
            <el-divider content-position="left">Rerank 重排序</el-divider>
            <el-form-item label="Rerank TopK">
              <el-input-number v-model="configForm.rerankTopK" :min="1" :max="10" />
              <div class="param-desc">重排序后保留的文档数量</div>
            </el-form-item>
            <el-form-item label="Rerank 置信度阈值">
              <el-slider v-model="configForm.rerankConfidenceThreshold" :min="0" :max="1" :step="0.05" show-input />
              <div class="param-desc">低于此值的文档会被丢弃</div>
            </el-form-item>
            <el-form-item label="文档截断长度">
              <el-input-number v-model="configForm.rerankDocTruncate" :min="100" :max="5000" :step="100" />
              <div class="param-desc">送入 Rerank 模型前截断的字符数</div>
            </el-form-item>

            <!-- HyDE -->
            <el-divider content-position="left">HyDE 深度集成</el-divider>
            <el-form-item label="HyDE 开关">
              <el-switch v-model="configForm.hydeEnabled" :active-value="1" :inactive-value="0" />
              <div class="param-desc">启用后将假设文档 + 等价查询作为独立检索通道</div>
            </el-form-item>
            <el-form-item label="HyDE 等价查询数量">
              <el-input-number v-model="configForm.hydeEquivQueryCount" :min="1" :max="10" />
              <div class="param-desc">为每个问题生成的等价查询数量</div>
            </el-form-item>

            <!-- Token -->
            <el-divider content-position="left">Token 预算</el-divider>
            <el-form-item label="Token 预算上限">
              <el-input-number v-model="configForm.tokenBudget" :min="500" :max="8000" :step="100" />
              <div class="param-desc">送入 LLM 的最大 Token 数</div>
            </el-form-item>
            <el-form-item label="Token 估算系数">
              <el-slider v-model="configForm.tokenEstimateCoefficient" :min="0.1" :max="1.0" :step="0.05" show-input />
              <div class="param-desc">字符数 × 系数 ≈ Token 数</div>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 版本历史对话框 -->
        <el-dialog v-model="configVersionDialogVisible" title="配置版本历史" width="700px">
          <el-table :data="configVersions" border size="small" style="width:100%">
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column prop="changeLog" label="变更说明" />
            <el-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createTime || row.create_time) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="rollbackConfig(row)">回滚</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-dialog>
      </el-tab-pane>

      <!-- Tab 2: 提示词工坊 -->
      <el-tab-pane label="提示词工坊" name="prompts">
        <div style="display:flex;gap:16px;height:calc(100vh - 200px)">
          <!-- 左侧：类型列表 -->
          <div style="width:220px;flex-shrink:0">
            <el-card shadow="never" style="height:100%">
              <div style="font-weight:bold;margin-bottom:12px">提示词类型</div>
              <div
                v-for="p in prompts"
                :key="p.id"
                class="prompt-type-item"
                :class="{ active: selectedPromptId === p.id }"
                @click="selectPrompt(p)"
              >
                <div style="font-weight:500">{{ promptTypeLabel(p.promptType) }}</div>
                <div style="font-size:12px;color:#909399">{{ p.templateName }}</div>
              </div>
            </el-card>
          </div>

          <!-- 右侧：编辑器 + 版本历史 -->
          <div style="flex:1;display:flex;flex-direction:column;gap:12px">
            <el-card shadow="never" style="flex:1;display:flex;flex-direction:column">
              <template #header>
                <div style="display:flex;justify-content:space-between;align-items:center">
                  <span style="font-weight:bold">{{ promptTypeLabel(selectedPrompt?.promptType) }}</span>
                  <div style="display:flex;gap:8px">
                    <el-button size="small" @click="showPromptVersions">版本历史</el-button>
                    <el-button size="small" type="warning" @click="createReleaseFromPrompt">发起发布</el-button>
                    <el-button size="small" type="primary" @click="savePrompt" :loading="promptSaving">保存</el-button>
                  </div>
                </div>
              </template>
              <el-input v-model="promptEditName" placeholder="模板名称" style="margin-bottom:8px" />
              <el-input
                v-model="promptEditContent"
                type="textarea"
                :rows="16"
                placeholder="输入提示词模板内容，支持 {variable} 变量占位符"
                style="flex:1"
              />
              <el-input v-model="promptChangeLog" placeholder="变更说明（可选）" style="margin-top:8px" />
            </el-card>
          </div>
        </div>

        <!-- 提示词版本历史对话框 -->
        <el-dialog v-model="promptVersionDialogVisible" title="提示词版本历史" width="700px">
          <el-table :data="promptVersions" border size="small" style="width:100%">
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column prop="changeLog" label="变更说明" />
            <el-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createTime || row.create_time) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="viewPromptVersion(row)">查看</el-button>
                <el-button type="warning" link size="small" @click="rollbackPrompt(row)">回滚</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-dialog>

        <!-- 查看版本内容对话框 -->
        <el-dialog v-model="promptVersionContentVisible" title="版本内容" width="600px">
          <pre style="white-space:pre-wrap;font-size:13px;background:#f5f7fa;padding:16px;border-radius:4px;max-height:400px;overflow:auto">{{ promptVersionContent }}</pre>
        </el-dialog>
      </el-tab-pane>

      <!-- Tab 3: 实验计划 -->
      <el-tab-pane label="实验计划" name="experiment">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">实验计划管理</span>
              <div style="display:flex;gap:8px">
                <el-button size="small" @click="loadExperimentPlans">刷新</el-button>
                <el-button size="small" type="primary" @click="openCreatePlanDialog">创建实验计划</el-button>
              </div>
            </div>
          </template>

          <!-- 实验计划列表 -->
          <el-table :data="experimentPlans" border size="small" style="width:100%" v-loading="experimentPlansLoading">
            <el-table-column prop="planName" label="计划名称" min-width="150" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="planStatusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="知识库" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.knowledgeBaseIds" type="success" size="small">
                  {{ String(row.knowledgeBaseIds).split(',').length }}个
                </el-tag>
                <span v-else style="color:#c0c4cc">全局</span>
              </template>
            </el-table-column>
            <el-table-column label="题库" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.useGlobalQuestions" type="info" size="small">全局</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="自定义" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.useCustomQuestions" type="warning" size="small">自定义</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createTime || row.create_time) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" type="primary" link size="small" @click="executePlan(row.id)">执行</el-button>
                <el-button v-if="row.status === 'COMPLETED'" type="success" link size="small" @click="viewPlanResult(row)">查看结果</el-button>
                <el-button v-if="row.status === 'COMPLETED'" type="info" link size="small" @click="viewExperimentReport({id: row.id, experimentName: row.planName, ...parseResultSummary(row.resultSummary)})">详细报告</el-button>
                <el-button type="danger" link size="small" @click="deletePlan(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 测试题库管理区域 -->
        <el-card shadow="never" style="margin-top:16px">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">测试题库管理</span>
              <div style="display:flex;gap:8px">
                <el-button size="small" @click="loadTestQuestions">刷新</el-button>
                <el-button size="small" type="primary" @click="openCreateQuestionDialog">新增题目</el-button>
              </div>
            </div>
          </template>
          <el-table :data="testQuestions" border size="small" style="width:100%">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="questionText" label="问题" min-width="250" show-overflow-tooltip />
            <el-table-column label="期望意图" width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ getIntentLabel(row.expectedIntentNodeId) || row.expectedIntent || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="期望知识库" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ getKbName(row.expectedKbId) || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="期望文档" width="150" show-overflow-tooltip>
              <template #default="{ row }">
                {{ getDocNames(row.expectedDocIds) || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openEditQuestionDialog(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="deleteTestQuestion(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 创建实验计划对话框 -->
        <el-dialog v-model="createPlanDialogVisible" title="创建实验计划" width="650px" destroy-on-close>
          <el-form :model="planForm" label-width="120px">
            <el-form-item label="计划名称" required>
              <el-input v-model="planForm.planName" placeholder="如：HyDE效果对比实验" />
            </el-form-item>
            <el-form-item label="知识库选择">
              <div style="margin-bottom:8px;color:#909399;font-size:12px">选择实验时使用的知识库范围（可选，不选则使用全局配置）</div>
              <el-checkbox v-model="planSelectAllKb" @change="togglePlanKb">全选</el-checkbox>
              <div style="margin-top:8px;max-height:150px;overflow-y:auto;border:1px solid #ebeef5;border-radius:4px;padding:8px">
                <el-checkbox-group v-model="planForm.knowledgeBaseIds">
                  <el-checkbox v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id" :label="kb.name" style="display:block" />
                </el-checkbox-group>
                <div v-if="knowledgeBases.length === 0" style="color:#c0c4cc;text-align:center;padding:10px">暂无知识库</div>
              </div>
            </el-form-item>
            <el-divider content-position="left">题目来源</el-divider>
            <el-form-item label="题库选择">
              <el-checkbox v-model="planForm.useGlobalQuestions">使用全局测试题库</el-checkbox>
            </el-form-item>
            <el-form-item label="自定义提问">
              <el-checkbox v-model="planForm.useCustomQuestions">使用自定义提问</el-checkbox>
            </el-form-item>
            <el-form-item v-if="planForm.useCustomQuestions" label="自定义提问列表">
              <div v-for="(q, idx) in planForm.customQuestions" :key="idx" style="display:flex;gap:8px;margin-bottom:8px">
                <el-input v-model="q.questionText" placeholder="提问内容" style="flex:2" />
                <el-input v-model="q.expectedAnswer" placeholder="期望答案（可选）" style="flex:2" />
                <el-button type="danger" link @click="planForm.customQuestions.splice(idx, 1)">删除</el-button>
              </div>
              <el-button type="primary" link @click="planForm.customQuestions.push({questionText: '', expectedAnswer: ''})">+ 添加提问</el-button>
            </el-form-item>
            <el-form-item v-if="planForm.useGlobalQuestions" label="选择题目">
              <el-checkbox v-model="planSelectAllQuestions" @change="togglePlanQuestions">全选</el-checkbox>
              <div style="margin-top:8px;max-height:150px;overflow-y:auto">
                <el-checkbox-group v-model="planForm.questionIds">
                  <el-checkbox v-for="q in testQuestions" :key="q.id" :value="q.id" :label="q.questionText" style="display:block" />
                </el-checkbox-group>
              </div>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="createPlanDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitCreatePlan" :loading="planCreating">创建</el-button>
          </template>
        </el-dialog>

        <!-- 新增/编辑测试题目对话框 -->
        <el-dialog v-model="questionDialogVisible" :title="questionForm.id ? '编辑题目' : '新增题目'" width="600px">
          <el-form :model="questionForm" label-width="100px">
            <el-form-item label="问题内容" required>
              <el-input v-model="questionForm.questionText" type="textarea" :rows="3" placeholder="输入测试问题" />
            </el-form-item>
            <el-form-item label="期望意图">
              <el-select v-model="questionForm.expectedIntentNodeId" placeholder="选择意图节点（选填）" clearable filterable style="width:100%">
                <el-option v-for="node in intentNodes" :key="node.id" :label="node.pathLabel" :value="node.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="期望知识库">
              <el-select v-model="questionForm.expectedKbId" placeholder="选择知识库（选填）" clearable filterable style="width:100%" @change="onQuestionKbChange">
                <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="期望文档" v-if="kbDocuments.length > 0">
              <el-select v-model="questionDocIdsArray" placeholder="选择文档（选填，可多选）" clearable filterable multiple style="width:100%">
                <el-option v-for="doc in kbDocuments" :key="doc.id" :label="doc.docName" :value="String(doc.id)" />
              </el-select>
            </el-form-item>
            <el-form-item label="期望答案">
              <el-input v-model="questionForm.standardAnswer" type="textarea" :rows="3" placeholder="标准答案（选填）" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="questionDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitQuestion" :loading="questionSaving">保存</el-button>
          </template>
        </el-dialog>

        <!-- 实验计划结果对话框 -->
        <el-dialog v-model="planResultVisible" title="实验结果摘要" width="600px">
          <pre style="white-space:pre-wrap;font-size:13px;background:#f5f7fa;padding:16px;border-radius:4px;max-height:400px;overflow:auto">{{ planResultContent }}</pre>
        </el-dialog>

        <!-- 实验报告对话框 -->
        <el-dialog v-model="experimentReportVisible" title="实验报告" width="800px">
          <div v-if="experimentReport">
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="实验名称">{{ experimentReport.experimentName }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="experimentStatusType(experimentReport.status)" size="small">{{ experimentReport.status }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="耗时">{{ experimentReport.runDurationMs ? (experimentReport.runDurationMs / 1000).toFixed(1) + 's' : '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-divider content-position="left">指标汇总</el-divider>
            <el-table :data="experimentMetrics" border size="small" style="width:100%">
              <el-table-column prop="metric" label="指标" />
              <el-table-column prop="value" label="值" />
            </el-table>

            <el-divider content-position="left">逐题明细</el-divider>
            <el-table :data="experimentReport.details || []" border size="small" style="width:100%" max-height="300">
              <el-table-column prop="questionId" label="题号" width="60" />
              <el-table-column prop="question" label="问题" width="200" show-overflow-tooltip />
              <el-table-column prop="intentCorrect" label="意图正确" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.intentCorrect ? 'success' : 'danger'" size="small">{{ row.intentCorrect ? 'Y' : 'N' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="recallAt3" label="R@3" width="70">
                <template #default="{ row }">{{ row.recallAt3 != null ? (row.recallAt3 * 100).toFixed(0) + '%' : '-' }}</template>
              </el-table-column>
              <el-table-column prop="mrr" label="MRR" width="70">
                <template #default="{ row }">{{ row.mrr != null ? row.mrr.toFixed(2) : '-' }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-dialog>
      </el-tab-pane>

      <!-- Tab 4: 发布管理 -->
      <el-tab-pane label="发布管理" name="release">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">灰度发布计划</span>
              <el-button size="small" type="primary" @click="openCreateReleaseDialog()">创建发布计划</el-button>
            </div>
          </template>
          <el-table :data="releasePlans" stripe v-loading="releaseLoading">
            <el-table-column prop="planName" label="计划名称" width="180" />
            <el-table-column prop="componentType" label="组件" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="componentTypeTag(row.componentType)">{{ componentTypeLabel(row.componentType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发布对象" width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <template v-if="row.componentType === 'MODEL'">
                  {{ row.modelNames || '多个模型' }}
                </template>
                <template v-else>
                  {{ row.componentName || '-' }}
                </template>
              </template>
            </el-table-column>
            <el-table-column label="版本" width="120">
              <template #default="{ row }">{{ row.versionLabel || '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="grayRatio" label="灰度比例" width="100">
              <template #default="{ row }">{{ row.grayRatio != null ? (row.grayRatio * 100).toFixed(0) + '%' : '-' }}</template>
            </el-table-column>
            <el-table-column prop="grayMode" label="灰度模式" width="100" />
            <el-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'DRAFT'" size="small" type="success" @click="approvePlan(row.id)">审批通过</el-button>
                <el-button v-if="row.status === 'DRAFT'" size="small" type="danger" @click="rejectPlan(row.id)">拒绝</el-button>
                <el-button v-if="row.status === 'APPROVED'" size="small" type="warning" @click="startGray(row.id)">开始灰度</el-button>
                <el-button v-if="row.status === 'GRAYING'" size="small" type="success" @click="activatePlan(row.id)">全量发布</el-button>
                <el-button v-if="row.status === 'GRAYING'" size="small" type="danger" @click="rollbackPlan(row.id)">回滚</el-button>
                <el-button v-if="row.status === 'ACTIVE'" size="small" type="warning" @click="rollbackPlan(row.id)">回滚</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 创建发布计划对话框 -->
        <el-dialog v-model="createReleaseDialogVisible" title="创建灰度发布计划" width="600px" destroy-on-close>
          <!-- 组件信息（从配置页面跳转过来时自动填充） -->
          <el-alert
            v-if="releaseForm._componentInfo"
            :title="releaseForm._componentInfo"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom:16px"
          />

          <el-form :model="releaseForm" label-width="130px">
            <el-form-item label="计划名称" required>
              <el-input v-model="releaseForm.planName" placeholder="如：RAG 配置灰度-V3" />
            </el-form-item>

            <!-- 模型类型不需要选择发布对象 -->
            <template v-if="releaseForm.componentType !== 'MODEL'">
              <el-form-item label="组件类型" required>
                <el-select v-model="releaseForm.componentType" style="width:100%" @change="onComponentTypeChange">
                  <el-option label="流水线配置" value="CONFIG" />
                  <el-option label="提示词模板" value="PROMPT" />
                  <el-option label="模型配置" value="MODEL" />
                </el-select>
              </el-form-item>

              <el-form-item label="发布对象" required>
                <el-select v-model="releaseForm.componentId" style="width:100%" placeholder="选择要发布的组件" @change="onComponentChange">
                  <el-option
                    v-for="item in releaseComponentOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <div class="param-desc">{{ componentTypeHint(releaseForm.componentType) }}</div>
              </el-form-item>

              <el-form-item label="目标版本" v-if="releaseForm.componentId && releaseVersionOptions.length">
                <el-select v-model="releaseForm.toVersionId" style="width:100%" placeholder="选择目标版本">
                  <el-option
                    v-for="v in releaseVersionOptions"
                    :key="v.value"
                    :label="v.label"
                    :value="v.value"
                  />
                </el-select>
                <div class="param-desc">将发布此版本到灰度流量</div>
              </el-form-item>
            </template>

            <!-- 模型类型显示提示信息 -->
            <el-form-item v-else label="组件类型">
              <el-tag type="info">模型配置</el-tag>
              <div class="param-desc">模型配置从模型配置 Tab 发起发布</div>
            </el-form-item>

            <el-divider content-position="left">灰度策略</el-divider>

            <el-form-item label="灰度模式" required>
              <el-radio-group v-model="releaseForm.grayMode">
                <el-radio value="PERCENT">百分比</el-radio>
                <el-radio value="LIST">名单</el-radio>
                <el-radio value="BOTH">混合</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="灰度比例" v-if="releaseForm.grayMode !== 'LIST'">
              <el-slider v-model="releaseForm.grayRatio" :min="0" :max="1" :step="0.01" show-input />
              <div class="param-desc">灰度流量占总流量比例（0%~100%）</div>
            </el-form-item>

            <el-form-item label="用户 ID 列表" v-if="releaseForm.grayMode !== 'PERCENT'">
              <el-input v-model="releaseForm.grayUserIdsText" type="textarea" :rows="3" placeholder="每行一个用户ID，或逗号分隔" />
              <div class="param-desc">名单中的用户将固定走灰度流量</div>
            </el-form-item>
          </el-form>

          <template #footer>
            <el-button @click="createReleaseDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitCreateReleasePlan" :loading="releaseCreating">创建</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- Tab 5: 模型配置 -->
      <el-tab-pane label="模型配置" name="model">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">LLM 模型配置</span>
              <div style="display:flex;gap:8px">
                <el-button size="small" type="warning" @click="createReleaseFromModel">发起发布</el-button>
                <el-button size="small" type="primary" @click="openCreateModelDialog">新增模型</el-button>
              </div>
            </div>
          </template>
          <el-table :data="modelConfigs" stripe v-loading="modelLoading">
            <el-table-column prop="modelName" label="模型名称" width="180" />
            <el-table-column prop="provider" label="提供商" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.provider }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="modelId" label="模型 ID" width="200" show-overflow-tooltip />
            <el-table-column prop="baseUrl" label="Base URL" min-width="250" show-overflow-tooltip />
            <el-table-column prop="isActive" label="激活" width="80">
              <template #default="{ row }">
                <el-switch :model-value="row.isActive === 1" @change="toggleModelActive(row.id)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="openEditModelDialog(row)">编辑</el-button>
                <el-button size="small" type="success" @click="testModel(row.id)" :loading="row._testing">测试</el-button>
                <el-button size="small" type="danger" @click="deleteModel(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 新增模型配置对话框 -->
        <el-dialog v-model="createModelDialogVisible" title="新增 LLM 模型配置" width="580px" destroy-on-close>
          <el-form :model="modelForm" label-width="120px">
            <el-form-item label="模型名称" required>
              <el-input v-model="modelForm.modelName" placeholder="如：百炼 qwen-plus" />
            </el-form-item>
            <el-form-item label="提供商" required>
              <el-select v-model="modelForm.provider" placeholder="选择提供商" style="width:100%">
                <el-option v-for="p in modelProviders" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="Base URL" required>
              <el-input v-model="modelForm.baseUrl" placeholder="OpenAI 兼容 API 地址" />
              <div class="param-desc">所有提供商统一使用 OpenAI 兼容协议</div>
            </el-form-item>
            <el-form-item label="API Key" required>
              <el-input v-model="modelForm.apiKey" placeholder="sk-..." show-password />
            </el-form-item>
            <el-form-item label="模型 ID" required>
              <el-input v-model="modelForm.modelId" placeholder="如 qwen-plus / deepseek-chat" />
            </el-form-item>
            <el-form-item label="Max Tokens">
              <el-input-number v-model="modelForm.maxTokens" :min="256" :max="32768" :step="256" style="width:100%" />
            </el-form-item>
            <el-form-item label="Temperature">
              <el-slider v-model="modelForm.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="createModelDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitCreateModel" :loading="modelCreating">创建</el-button>
          </template>
        </el-dialog>

        <!-- 编辑模型配置对话框 -->
        <el-dialog v-model="editModelDialogVisible" title="编辑 LLM 模型配置" width="580px" destroy-on-close>
          <el-form :model="editModelForm" label-width="120px">
            <el-form-item label="模型名称" required>
              <el-input v-model="editModelForm.modelName" placeholder="如：百炼 qwen-plus" />
            </el-form-item>
            <el-form-item label="提供商" required>
              <el-select v-model="editModelForm.provider" placeholder="选择提供商" style="width:100%">
                <el-option v-for="p in modelProviders" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="Base URL" required>
              <el-input v-model="editModelForm.baseUrl" placeholder="OpenAI 兼容 API 地址" />
              <div class="param-desc">所有提供商统一使用 OpenAI 兼容协议</div>
            </el-form-item>
            <el-form-item label="API Key" required>
              <el-input v-model="editModelForm.apiKey" placeholder="sk-..." show-password />
            </el-form-item>
            <el-form-item label="模型 ID" required>
              <el-input v-model="editModelForm.modelId" placeholder="如 qwen-plus / deepseek-chat" />
            </el-form-item>
            <el-form-item label="Max Tokens">
              <el-input-number v-model="editModelForm.maxTokens" :min="256" :max="32768" :step="256" style="width:100%" />
            </el-form-item>
            <el-form-item label="Temperature">
              <el-slider v-model="editModelForm.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="editModelDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitEditModel" :loading="modelEditing">保存</el-button>
          </template>
        </el-dialog>

        <!-- 模型发布选择对话框（简化版：一步完成） -->
        <el-dialog v-model="modelReleaseSelectVisible" title="创建模型灰度发布计划" width="600px" destroy-on-close>
          <el-form label-width="130px">
            <el-form-item label="计划名称" required>
              <el-input v-model="modelReleasePlanName" placeholder="如：模型灰度-V1" />
            </el-form-item>

            <el-form-item label="选择模型" required>
              <el-checkbox-group v-model="selectedModelIds">
                <el-checkbox v-for="opt in modelReleaseOptions" :key="opt.value" :label="opt.value" style="display:block;margin:8px 0">
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
              <div class="param-desc">勾选要发布的模型配置</div>
            </el-form-item>

            <el-divider content-position="left">灰度策略</el-divider>

            <el-form-item label="灰度模式" required>
              <el-radio-group v-model="modelReleaseGrayMode">
                <el-radio value="PERCENT">百分比</el-radio>
                <el-radio value="LIST">名单</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="灰度比例" v-if="modelReleaseGrayMode !== 'LIST'">
              <el-slider v-model="modelReleaseGrayRatio" :min="0" :max="1" :step="0.01" show-input />
              <div class="param-desc">灰度流量占总流量比例（0%~100%）</div>
            </el-form-item>

            <el-form-item label="用户 ID 列表" v-if="modelReleaseGrayMode === 'LIST'">
              <el-input v-model="modelReleaseUserIdsText" type="textarea" :rows="3" placeholder="每行一个用户 ID，或逗号分隔" />
              <div class="param-desc">名单中的用户将固定走灰度流量</div>
            </el-form-item>
          </el-form>

          <template #footer>
            <el-button @click="modelReleaseSelectVisible = false">取消</el-button>
            <el-button type="primary" @click="submitModelReleasePlan" :loading="releaseCreating">创建发布计划</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- Tab 6: 数据回放 -->
      <el-tab-pane label="数据回放" name="replay">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">生产数据回放审批</span>
              <div style="display:flex;gap:8px">
                <el-button size="small" @click="loadReplayRequests">刷新</el-button>
                <el-button size="small" type="primary" @click="openManualReplayDialog">手动提交</el-button>
              </div>
            </div>
          </template>
          <el-table :data="replayRequests" stripe v-loading="replayLoading">
            <el-table-column prop="traceId" label="来源链路" width="180" show-overflow-tooltip />
            <el-table-column prop="questionText" label="提问内容" min-width="300" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" size="small" type="success" @click="approveReplay(row.id)">通过</el-button>
                <el-button v-if="row.status === 'PENDING'" size="small" type="danger" @click="rejectReplay(row.id)">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 手动提交回放申请对话框 -->
      <el-dialog v-model="manualReplayDialogVisible" title="手动提交回放申请" width="500px">
        <el-form :model="manualReplayForm" label-width="100px">
          <el-form-item label="traceId" required>
            <el-input v-model="manualReplayForm.traceId" placeholder="粘贴链路 traceId" />
          </el-form-item>
          <el-form-item label="提问内容" required>
            <el-input v-model="manualReplayForm.questionText" type="textarea" :rows="3" placeholder="输入用户提问" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="manualReplayDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitManualReplay" :loading="manualReplaySubmitting">提交</el-button>
        </template>
      </el-dialog>

    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { useAuthStore } from '../../stores/auth'

// ==================== 通用 ====================
const activeTab = ref('pipeline')
const auth = useAuthStore()

function formatTime(val: any): string {
  if (!val) return '-'
  const d = new Date(val)
  if (isNaN(d.getTime())) return String(val)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ==================== Tab 1: 流水线控制 ====================
const configForm = ref<any>({})
const configLoading = ref(false)
const configSaving = ref(false)
const configVersionDialogVisible = ref(false)
const configVersions = ref<any[]>([])

async function loadConfig() {
  configLoading.value = true
  try {
    const res = await request.get('/rag-lab/config') as any
    const data = res.data || res || {}
    configForm.value = { ...data }
  } catch (e) {
    console.error(e)
  } finally {
    configLoading.value = false
  }
}

async function saveConfig() {
  configSaving.value = true
  try {
    const changeLog = await ElMessageBox.prompt('请输入变更说明', '保存配置', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputPlaceholder: '如：调整 rerank 阈值至 0.6'
    }).then(r => r.value).catch(() => null)
    if (changeLog === null) return

    await request.put('/rag-lab/config', {
      config: configForm.value,
      changeLog
    })
    ElMessage.success('配置已保存')
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error(e)
      ElMessage.error('保存失败')
    }
  } finally {
    configSaving.value = false
  }
}

async function showConfigVersions() {
  try {
    const configId = configForm.value.id
    if (!configId) { ElMessage.warning('暂无配置'); return }
    const res = await request.get(`/rag-lab/config/${configId}/versions`) as any
    configVersions.value = (res.data || res || []).map((v: any) => ({
      ...v,
      versionNo: v.version_no ?? v.versionNo,
      changeLog: v.change_log ?? v.changeLog,
      createTime: v.create_time ?? v.createTime
    }))
    configVersionDialogVisible.value = true
  } catch (e) {
    console.error(e)
    ElMessage.error('获取版本历史失败')
  }
}

async function rollbackConfig(row: any) {
  try {
    await ElMessageBox.confirm(`确认回滚到版本 V${row.versionNo}？`, '回滚确认')
    const configId = configForm.value.id
    await request.post(`/rag-lab/config/${configId}/rollback/${row.id}`)
    ElMessage.success('回滚成功')
    configVersionDialogVisible.value = false
    await loadConfig()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

/** 从流水线控制页发起发布 */
function createReleaseFromConfig() {
  if (!configForm.value.id) {
    ElMessage.warning('请先加载配置')
    return
  }
  activeTab.value = 'release'
  openCreateReleaseDialog('CONFIG', configForm.value.id, `流水线配置 ID=${configForm.value.id}`)
}

// ==================== Tab 2: 提示词工坊 ====================
const prompts = ref<any[]>([])
const selectedPrompt = ref<any>(null)
const selectedPromptId = ref<number | null>(null)
const promptEditName = ref('')
const promptEditContent = ref('')
const promptChangeLog = ref('')
const promptSaving = ref(false)
const promptVersionDialogVisible = ref(false)
const promptVersions = ref<any[]>([])
const promptVersionContentVisible = ref(false)
const promptVersionContent = ref('')

const PROMPT_TYPE_LABELS: Record<string, string> = {
  QUERY_REWRITE: '查询改写',
  RERANK_SCORE: 'Rerank 评分',
  INTENT_CLASSIFY: '意图分类',
  SYSTEM_CHAT: '系统对话',
  HYDE_DOC: 'HyDE 假设文档',
  HYDE_EQUIV: 'HyDE 等价查询'
}

function promptTypeLabel(type: string): string {
  return PROMPT_TYPE_LABELS[type] || type
}

async function loadPrompts() {
  try {
    const res = await request.get('/rag-lab/prompts') as any
    prompts.value = (res.data || res || []).map((p: any) => ({
      ...p,
      promptType: p.prompt_type ?? p.promptType,
      templateName: p.template_name ?? p.templateName,
      templateContent: p.template_content ?? p.templateContent
    }))
    if (prompts.value.length > 0 && !selectedPrompt.value) {
      selectPrompt(prompts.value[0])
    }
  } catch (e) {
    console.error(e)
  }
}

function selectPrompt(p: any) {
  selectedPrompt.value = p
  selectedPromptId.value = p.id
  promptEditName.value = p.templateName || ''
  promptEditContent.value = p.templateContent || ''
  promptChangeLog.value = ''
}

async function savePrompt() {
  if (!selectedPrompt.value) return
  promptSaving.value = true
  try {
    const type = selectedPrompt.value.promptType
    await request.put(`/rag-lab/prompts/${type}`, {
      templateContent: promptEditContent.value,
      templateName: promptEditName.value,
      changeLog: promptChangeLog.value || '通过页面更新'
    })
    ElMessage.success('提示词已保存')
    await loadPrompts()
  } catch (e) {
    console.error(e)
    ElMessage.error('保存失败')
  } finally {
    promptSaving.value = false
  }
}

async function showPromptVersions() {
  if (!selectedPrompt.value) return
  try {
    const res = await request.get(`/rag-lab/prompts/${selectedPrompt.value.id}/versions`) as any
    promptVersions.value = (res.data || res || []).map((v: any) => ({
      ...v,
      versionNo: v.version_no ?? v.versionNo,
      changeLog: v.change_log ?? v.changeLog,
      createTime: v.create_time ?? v.createTime,
      templateContent: v.template_content ?? v.templateContent
    }))
    promptVersionDialogVisible.value = true
  } catch (e) {
    console.error(e)
    ElMessage.error('获取版本历史失败')
  }
}

function viewPromptVersion(row: any) {
  promptVersionContent.value = row.templateContent || ''
  promptVersionContentVisible.value = true
}

async function rollbackPrompt(row: any) {
  try {
    await ElMessageBox.confirm(`确认回滚提示词到版本 V${row.versionNo}？`, '回滚确认')
    const promptId = selectedPrompt.value.id
    await request.post(`/rag-lab/prompts/${promptId}/rollback/${row.id}`)
    ElMessage.success('回滚成功')
    promptVersionDialogVisible.value = false
    await loadPrompts()
    if (selectedPrompt.value) {
      const refreshed = prompts.value.find(p => p.id === selectedPrompt.value.id)
      if (refreshed) selectPrompt(refreshed)
    }
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

/** 从提示词工坊页发起发布 */
function createReleaseFromPrompt() {
  if (!selectedPrompt.value) {
    ElMessage.warning('请先选择一个提示词')
    return
  }
  activeTab.value = 'release'
  openCreateReleaseDialog('PROMPT', selectedPrompt.value.id, `提示词「${promptTypeLabel(selectedPrompt.value.promptType)}」ID=${selectedPrompt.value.id}`)
}

// ==================== Tab 3: 实验计划 ====================
const experimentPlans = ref<any[]>([])
const experimentPlansLoading = ref(false)
const createPlanDialogVisible = ref(false)
const planCreating = ref(false)
const planForm = ref<any>({
  planName: '',
  useGlobalQuestions: true,
  useCustomQuestions: false,
  questionIds: [] as number[],
  knowledgeBaseIds: [] as number[],
  customQuestions: [] as any[]
})
const planSelectAllQuestions = ref(false)
const planSelectAllKb = ref(false)
const planResultVisible = ref(false)
const planResultContent = ref('')

// 知识库列表
const knowledgeBases = ref<any[]>([])

// 意图节点列表
const intentNodes = ref<any[]>([])

// 当前选中知识库的文档列表
const kbDocuments = ref<any[]>([])

// 测试题目管理
const questionDialogVisible = ref(false)
const questionSaving = ref(false)
const questionForm = ref<any>({
  id: null,
  questionText: '',
  expectedIntentNodeId: null,
  expectedIntent: '',
  expectedKbId: null,
  expectedDocIds: '',
  standardAnswer: ''
})

// 计算属性：文档 ID 多选数组（用于 el-select multiple）
const questionDocIdsArray = computed({
  get: () => {
    if (!questionForm.value.expectedDocIds) return []
    try {
      const parsed = JSON.parse(questionForm.value.expectedDocIds)
      return Array.isArray(parsed) ? parsed.map(String) : []
    } catch {
      return questionForm.value.expectedDocIds.split(',').filter(Boolean).map(String)
    }
  },
  set: (val: string[]) => {
    questionForm.value.expectedDocIds = val.length > 0 ? JSON.stringify(val) : ''
  }
})

// 实验报告相关
const experimentReportVisible = ref(false)
const experimentReport = ref<any>(null)
const testQuestions = ref<any[]>([])

function getIntentLabel(nodeId: number): string {
  if (!nodeId) return ''
  const node = intentNodes.value.find((n: any) => n.id === nodeId)
  return node ? node.pathLabel : String(nodeId)
}

function getKbName(kbId: number): string {
  if (!kbId) return ''
  const kb = knowledgeBases.value.find((k: any) => k.id === kbId)
  return kb ? kb.name : String(kbId)
}

function getDocNames(docIdsStr: string): string {
  if (!docIdsStr) return ''
  let ids: string[] = []
  try {
    const parsed = JSON.parse(docIdsStr)
    ids = Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    ids = docIdsStr.split(',').filter(Boolean)
  }
  return ids.map(id => {
    const doc = kbDocuments.value.find((d: any) => String(d.id) === id)
    return doc ? doc.docName : id
  }).join(', ')
}

function planStatusType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'RUNNING') return ''
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function togglePlanQuestions(val: any) {
  if (val) {
    planForm.value.questionIds = testQuestions.value.map(q => q.id)
  } else {
    planForm.value.questionIds = []
  }
}

function togglePlanKb(val: any) {
  if (val) {
    planForm.value.knowledgeBaseIds = knowledgeBases.value.map(kb => kb.id)
  } else {
    planForm.value.knowledgeBaseIds = []
  }
}

async function loadKnowledgeBases() {
  try {
    const res = await request.get('/knowledge-base/page', { params: { pageNum: 1, pageSize: 100 } }) as any
    const data = res.data || res || {}
    knowledgeBases.value = (data.records || data.list || []).map((kb: any) => ({
      id: kb.id,
      name: kb.name,
      description: kb.description
    }))
  } catch (e) {
    console.error(e)
  }
}

// 加载意图节点（复用已有的 GET /intent/nodes API）
async function loadIntentNodes() {
  try {
    const res = await request.get('/intent/nodes') as any
    const nodes = res.data || res || []
    // 构建树形路径标签，如 "金融 > 金融工程"
    intentNodes.value = nodes.map((n: any) => {
      let pathLabel = n.label || ''
      if (n.parentId) {
        const parent = nodes.find((p: any) => p.id === n.parentId)
        if (parent) {
          const grandparent = nodes.find((g: any) => g.id === parent.parentId)
          pathLabel = (grandparent ? grandparent.label + ' > ' : '') + parent.label + ' > ' + n.label
        }
      }
      return { ...n, pathLabel }
    })
  } catch (e) {
    console.error('加载意图节点失败', e)
  }
}

// 知识库切换时加载文档列表
async function onQuestionKbChange(kbId: number) {
  kbDocuments.value = []
  questionForm.value.expectedDocIds = ''  // 重置文档选择
  if (!kbId) return
  try {
    const res = await request.get(`/knowledge-base/${kbId}/docs`, { params: { current: 1, pageSize: 100 } }) as any
    const data = res.data || res || {}
    kbDocuments.value = (data.records || data.list || []).map((d: any) => ({
      id: d.id,
      docName: d.doc_name ?? d.docName
    }))
  } catch (e) {
    console.error('加载文档列表失败', e)
  }
}

function parseResultSummary(summary: string): any {
  if (!summary) return {}
  try {
    return JSON.parse(summary)
  } catch {
    return {}
  }
}

async function loadExperimentPlans() {
  experimentPlansLoading.value = true
  try {
    const res = await request.get('/rag-lab/experiment-plan/list') as any
    experimentPlans.value = (res.data || res || []).map((p: any) => ({
      ...p,
      planName: p.plan_name ?? p.planName,
      useGlobalQuestions: p.use_global_questions ?? p.useGlobalQuestions,
      useCustomQuestions: p.use_custom_questions ?? p.useCustomQuestions,
      knowledgeBaseIds: p.knowledge_base_ids ?? p.knowledgeBaseIds,
      createTime: p.create_time ?? p.createTime
    }))
  } catch (e) {
    console.error(e)
  } finally {
    experimentPlansLoading.value = false
  }
}

function openCreatePlanDialog() {
  planForm.value = {
    planName: '',
    useGlobalQuestions: true,
    useCustomQuestions: false,
    questionIds: [],
    knowledgeBaseIds: [],
    customQuestions: []
  }
  planSelectAllQuestions.value = false
  planSelectAllKb.value = false
  loadKnowledgeBases()
  createPlanDialogVisible.value = true
}

async function submitCreatePlan() {
  if (!planForm.value.planName.trim()) {
    ElMessage.warning('请输入计划名称')
    return
  }
  if (!planForm.value.useGlobalQuestions && !planForm.value.useCustomQuestions) {
    ElMessage.warning('请至少选择一种题库')
    return
  }
  planCreating.value = true
  try {
    await request.post('/rag-lab/experiment-plan', {
      planName: planForm.value.planName,
      useGlobalQuestions: planForm.value.useGlobalQuestions,
      useCustomQuestions: planForm.value.useCustomQuestions,
      questionIds: planForm.value.useGlobalQuestions ? planForm.value.questionIds : [],
      knowledgeBaseIds: planForm.value.knowledgeBaseIds,
      customQuestions: planForm.value.useCustomQuestions ? planForm.value.customQuestions.filter((q: any) => q.questionText?.trim()) : []
    })
    ElMessage.success('实验计划已创建')
    createPlanDialogVisible.value = false
    await loadExperimentPlans()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    planCreating.value = false
  }
}

async function executePlan(id: number) {
  try {
    await ElMessageBox.confirm('确认执行此实验计划？', '执行确认')
    await request.post(`/rag-lab/experiment-plan/${id}/execute`)
    ElMessage.success('实验已开始执行')
    await loadExperimentPlans()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '执行失败')
  }
}

async function deletePlan(id: number) {
  try {
    await ElMessageBox.confirm('确认删除此实验计划？', '删除确认')
    await request.delete(`/rag-lab/experiment-plan/${id}`)
    ElMessage.success('已删除')
    await loadExperimentPlans()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function viewPlanResult(row: any) {
  planResultContent.value = row.resultSummary || '无结果数据'
  planResultVisible.value = true
}

// 测试题目管理方法
function openCreateQuestionDialog() {
  questionForm.value = {
    id: null, questionText: '', expectedIntentNodeId: null,
    expectedIntent: '', expectedKbId: null, expectedDocIds: '', standardAnswer: ''
  }
  kbDocuments.value = []
  questionDialogVisible.value = true
}

function openEditQuestionDialog(row: any) {
  questionForm.value = {
    id: row.id,
    questionText: row.questionText,
    expectedIntentNodeId: row.expectedIntentNodeId || null,
    expectedIntent: row.expectedIntent || '',
    expectedKbId: row.expectedKbId || null,
    expectedDocIds: row.expectedDocIds || '',
    standardAnswer: row.standardAnswer || ''
  }
  kbDocuments.value = []
  // 如果有知识库，加载对应的文档
  if (row.expectedKbId) {
    onQuestionKbChange(row.expectedKbId)
  }
  questionDialogVisible.value = true
}

async function submitQuestion() {
  if (!questionForm.value.questionText.trim()) {
    ElMessage.warning('请输入问题内容')
    return
  }
  questionSaving.value = true
  try {
    const payload = {
      questionText: questionForm.value.questionText,
      expectedIntentNodeId: questionForm.value.expectedIntentNodeId,
      expectedIntent: questionForm.value.expectedIntent,
      expectedKbId: questionForm.value.expectedKbId,
      expectedDocIds: questionForm.value.expectedDocIds,
      standardAnswer: questionForm.value.standardAnswer
    }
    if (questionForm.value.id) {
      await request.put(`/rag-lab/test-questions/${questionForm.value.id}`, payload)
    } else {
      await request.post('/rag-lab/test-questions', payload)
    }
    ElMessage.success(questionForm.value.id ? '已更新' : '已创建')
    questionDialogVisible.value = false
    await loadTestQuestions()
  } catch (e: any) {
    ElMessage.error('操作失败')
  } finally {
    questionSaving.value = false
  }
}

async function deleteTestQuestion(id: number) {
  try {
    await ElMessageBox.confirm('确认删除此题目？', '删除确认')
    await request.delete(`/rag-lab/test-questions/${id}`)
    ElMessage.success('已删除')
    await loadTestQuestions()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const experimentMetrics = computed(() => {
  if (!experimentReport.value) return []
  const r = experimentReport.value
  return [
    { metric: '意图准确率', value: r.intentAccuracy != null ? (r.intentAccuracy * 100).toFixed(1) + '%' : '-' },
    { metric: '改写正确率', value: r.rewriteAccuracy != null ? (r.rewriteAccuracy * 100).toFixed(1) + '%' : '-' },
    { metric: 'HyDE 相关性', value: r.hydeRelevance != null ? r.hydeRelevance.toFixed(3) : '-' },
    { metric: 'Recall@3', value: r.recallAt3 != null ? (r.recallAt3 * 100).toFixed(1) + '%' : '-' },
    { metric: 'Recall@5', value: r.recallAt5 != null ? (r.recallAt5 * 100).toFixed(1) + '%' : '-' },
    { metric: 'Recall@10', value: r.recallAt10 != null ? (r.recallAt10 * 100).toFixed(1) + '%' : '-' },
    { metric: 'MRR', value: r.mrr != null ? r.mrr.toFixed(3) : '-' },
    { metric: 'NDCG@3', value: r.rerankNdcgAt3 != null ? r.rerankNdcgAt3.toFixed(3) : '-' },
    { metric: 'NDCG@5', value: r.rerankNdcgAt5 != null ? r.rerankNdcgAt5.toFixed(3) : '-' },
    { metric: '忠实度', value: r.answerFaithfulness != null ? (r.answerFaithfulness * 100).toFixed(1) + '%' : '-' },
    { metric: '完整性', value: r.answerCompleteness != null ? (r.answerCompleteness * 100).toFixed(1) + '%' : '-' },
    { metric: '幻觉率', value: r.hallucinationRate != null ? (r.hallucinationRate * 100).toFixed(1) + '%' : '-' }
  ]
})

function experimentStatusType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'RUNNING') return ''
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

async function loadTestQuestions() {
  try {
    const res = await request.get('/rag-lab/test-questions') as any
    testQuestions.value = (res.data || res || []).map((q: any) => ({
      ...q,
      questionText: q.question_text ?? q.questionText,
      expectedIntent: q.expected_intent ?? q.expectedIntent,
      expectedDocIds: q.expected_doc_ids ?? q.expectedDocIds
    }))
  } catch (e) {
    console.error(e)
  }
}

async function viewExperimentReport(row: any) {
  try {
    const res = await request.get(`/rag-lab/experiments/${row.id}/report`) as any
    const data = res.data || res || {}
    experimentReport.value = {
      ...data,
      experimentName: data.experiment_name ?? data.experimentName,
      runDurationMs: data.run_duration_ms ?? data.runDurationMs,
      intentAccuracy: data.intent_accuracy ?? data.intentAccuracy,
      recallAt3: data.recall_at_3 ?? data.recallAt3,
      recallAt5: data.recall_at_5 ?? data.recallAt5,
      recallAt10: data.recall_at_10 ?? data.recallAt10,
      hydeRelevance: data.hyde_relevance ?? data.hydeRelevance,
      rerankNdcgAt3: data.rerank_ndcg_at_3 ?? data.rerankNdcgAt3,
      rerankNdcgAt5: data.rerank_ndcg_at_5 ?? data.rerankNdcgAt5,
      answerFaithfulness: data.answer_faithfulness ?? data.answerFaithfulness,
      answerCompleteness: data.answer_completeness ?? data.answerCompleteness,
      hallucinationRate: data.hallucination_rate ?? data.hallucinationRate,
      rewriteAccuracy: data.rewrite_accuracy ?? data.rewriteAccuracy,
      details: data.details || []
    }
    experimentReportVisible.value = true
  } catch (e) {
    console.error(e)
    ElMessage.error('获取报告失败')
  }
}

// ==================== Tab 4: 发布管理 ====================
const releasePlans = ref<any[]>([])
const releaseLoading = ref(false)
const createReleaseDialogVisible = ref(false)
const releaseCreating = ref(false)
const releaseForm = ref<any>({
  planName: '',
  componentType: 'CONFIG',
  componentId: null,
  toVersionId: null,
  grayMode: 'PERCENT',
  grayRatio: 0.1,
  grayUserIdsText: '',
  _componentInfo: ''  // 内部用，不提交到后端
})
const releaseComponentOptions = ref<any[]>([])
const releaseVersionOptions = ref<any[]>([])

async function loadReleasePlans() {
  releaseLoading.value = true
  try {
    const res = await request.get('/rag-lab/gray-release/list') as any
    releasePlans.value = (res.data || res || []).map((p: any) => {
      // 构建版本标签
      const versionLabel = p.fromVersionId
        ? `V${p.fromVersionId}`
        : '-'
      // 构建组件名称
      const componentName = buildComponentName(p)
      return {
        ...p,
        versionLabel,
        componentName
      }
    })
  } catch (e) {
    console.error(e)
  } finally {
    releaseLoading.value = false
  }
}

function buildComponentName(row: any): string {
  if (!row.componentId) return '-'
  if (row.componentType === 'CONFIG') return `流水线配置 #${row.componentId}`
  if (row.componentType === 'PROMPT') {
    const p = prompts.value.find((x: any) => x.id === row.componentId)
    return p ? promptTypeLabel(p.promptType) : `提示词 #${row.componentId}`
  }
  if (row.componentType === 'MODEL') {
    const m = modelConfigs.value.find((x: any) => x.id === row.componentId)
    return m ? m.modelName : `模型 #${row.componentId}`
  }
  return '#' + row.componentId
}

function componentTypeLabel(type: string): string {
  const map: Record<string, string> = { CONFIG: '配置', PROMPT: '提示词', MODEL: '模型' }
  return map[type] || type
}

function componentTypeTag(type: string): string {
  const map: Record<string, string> = { CONFIG: '', PROMPT: 'warning', MODEL: 'info' }
  return map[type] || 'info'
}

function componentTypeHint(type: string): string {
  const map: Record<string, string> = {
    CONFIG: '选择要发布的流水线配置（当前激活的配置）',
    PROMPT: '选择要发布的提示词类型',
    MODEL: '选择要发布的模型配置'
  }
  return map[type] || ''
}

function getStatusType(status: string): string {
  const map: Record<string, string> = {
    'DRAFT': 'info', 'APPROVED': '', 'GRAYING': 'warning',
    'ACTIVE': 'success', 'ROLLED_BACK': 'danger', 'REJECTED': 'danger'
  }
  return map[status] || 'info'
}

/**
 * 打开发布计划对话框
 * @param componentType 预填的组件类型（从配置页面跳转时使用）
 * @param componentId 预填的组件 ID
 * @param componentInfo 提示文字（可选）
 * @param modelConfigIds 模型配置 ID 列表（MODEL 类型时使用）
 */
async function openCreateReleaseDialog(componentType?: string, componentId?: number, componentInfo?: string, modelConfigIds?: number[]) {
  releaseForm.value = {
    planName: '',
    componentType: componentType || 'CONFIG',
    componentId: componentId || null,
    toVersionId: null,
    grayMode: 'PERCENT',
    grayRatio: 0.1,
    grayUserIdsText: '',
    _componentInfo: componentInfo || '',
    _modelConfigIds: modelConfigIds || []
  }
  releaseComponentOptions.value = []
  releaseVersionOptions.value = []

  // 加载对应组件类型的选项
  await loadReleaseComponentOptions()
  if (componentId) {
    await onComponentChange(componentId)
  }

  createReleaseDialogVisible.value = true
}

async function loadReleaseComponentOptions() {
  const type = releaseForm.value.componentType
  releaseComponentOptions.value = []
  if (type === 'CONFIG') {
    try {
      const res = await request.get('/rag-lab/config') as any
      const data = res.data || res
      if (data && data.id) {
        releaseComponentOptions.value = [{ value: data.id, label: `流水线配置 #${data.id}（当前激活）` }]
      }
    } catch (e) {
      console.error(e)
    }
  } else if (type === 'PROMPT') {
    releaseComponentOptions.value = prompts.value.map((p: any) => ({
      value: p.id,
      label: `${promptTypeLabel(p.promptType)} — ${p.templateName || ''}`
    }))
  } else if (type === 'MODEL') {
    releaseComponentOptions.value = modelConfigs.value.map((m: any) => ({
      value: m.id,
      label: `${m.modelName} (${m.modelId})`
    }))
  }
}

async function onComponentTypeChange() {
  releaseForm.value.componentId = null
  releaseForm.value.toVersionId = null
  releaseVersionOptions.value = []
  await loadReleaseComponentOptions()
}

async function onComponentChange(componentId: any) {
  releaseForm.value.toVersionId = null
  releaseVersionOptions.value = []
  const type = releaseForm.value.componentType

  if (type === 'CONFIG') {
    try {
      const res = await request.get(`/rag-lab/config/${componentId}/versions`) as any
      const versions = res.data || res || []
      releaseVersionOptions.value = versions.map((v: any) => ({
        value: v.id,
        label: `V${v.version_no ?? v.versionNo} — ${v.change_log ?? v.changeLog ?? '无说明'}`
      }))
    } catch (e) {
      console.error(e)
    }
  } else if (type === 'PROMPT') {
    try {
      const res = await request.get(`/rag-lab/prompts/${componentId}/versions`) as any
      const versions = res.data || res || []
      releaseVersionOptions.value = versions.map((v: any) => ({
        value: v.id,
        label: `V${v.version_no ?? v.versionNo} — ${v.change_log ?? v.changeLog ?? '无说明'}`
      }))
    } catch (e) {
      console.error(e)
    }
  } else if (type === 'MODEL') {
    // 模型配置暂无版本管理，直接用当前配置
    releaseVersionOptions.value = [{ value: 0, label: '当前配置（模型配置无版本管理）' }]
    releaseForm.value.toVersionId = 0
  }
}

async function submitCreateReleasePlan() {
  if (!releaseForm.value.planName.trim()) { ElMessage.warning('请填写计划名称'); return }
  if (releaseForm.value.componentType === 'MODEL') {
    ElMessage.warning('模型配置请从模型配置 Tab 发起发布')
    return
  }
  if (!releaseForm.value.componentId) { ElMessage.warning('请选择发布对象'); return }
  releaseCreating.value = true
  try {
    const userIds = releaseForm.value.grayUserIdsText
      .split(/[\n,，]/).map((s: string) => s.trim()).filter((s: string) => s && !isNaN(Number(s))).map(Number)
    const payload: any = {
      planName: releaseForm.value.planName,
      componentType: releaseForm.value.componentType,
      componentId: releaseForm.value.componentId,
      grayMode: releaseForm.value.grayMode,
      grayRatio: releaseForm.value.grayMode !== 'LIST' ? releaseForm.value.grayRatio : null,
      grayUserIds: releaseForm.value.grayMode !== 'PERCENT' ? JSON.stringify(userIds) : null
    }
    if (releaseForm.value.toVersionId && releaseForm.value.toVersionId > 0) {
      payload.toVersionId = releaseForm.value.toVersionId
    }
    await request.post('/rag-lab/gray-release', payload)
    ElMessage.success('发布计划已创建')
    createReleaseDialogVisible.value = false
    await loadReleasePlans()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    releaseCreating.value = false
  }
}

async function approvePlan(id: number) {
  try {
    await request.post(`/rag-lab/gray-release/${id}/approve`, null, { params: { approvedBy: 1 } })
    ElMessage.success('审批通过')
    await loadReleasePlans()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function rejectPlan(id: number) {
  try {
    await request.post(`/rag-lab/gray-release/${id}/reject`, null, { params: { approvedBy: 1 } })
    ElMessage.success('已拒绝')
    await loadReleasePlans()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function startGray(id: number) {
  try {
    await request.post(`/rag-lab/gray-release/${id}/start-gray`)
    ElMessage.success('开始灰度')
    await loadReleasePlans()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function activatePlan(id: number) {
  try {
    await request.post(`/rag-lab/gray-release/${id}/activate`)
    ElMessage.success('全量发布完成')
    await loadReleasePlans()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function rollbackPlan(id: number) {
  const plan = releasePlans.value.find((p: any) => p.id === id)
  const statusText = plan?.status === 'ACTIVE' ? '全量发布' : '灰度'
  try {
    await ElMessageBox.confirm(`确认回滚此发布计划？将恢复到${statusText}前的版本。`, '回滚确认')
    await request.post(`/rag-lab/gray-release/${id}/rollback`)
    ElMessage.success('已回滚到发布前版本')
    await loadReleasePlans()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

// ==================== Tab 5: 模型配置 ====================
const modelConfigs = ref<any[]>([])
const modelLoading = ref(false)
const createModelDialogVisible = ref(false)
const editModelDialogVisible = ref(false)
const modelCreating = ref(false)
const modelEditing = ref(false)
const modelForm = ref<any>({
  modelName: '',
  provider: 'bailian',
  baseUrl: '',
  apiKey: '',
  modelId: '',
  maxTokens: 4096,
  temperature: 0.7
})
const editModelForm = ref<any>({
  id: null,
  modelName: '',
  provider: 'bailian',
  baseUrl: '',
  apiKey: '',
  modelId: '',
  maxTokens: 4096,
  temperature: 0.7
})

// 模型发布选择相关
const modelReleaseSelectVisible = ref(false)
const modelReleaseOptions = ref<any[]>([])
const selectedModelIds = ref<number[]>([])
const modelReleasePlanName = ref('')
const modelReleaseGrayMode = ref('PERCENT')
const modelReleaseGrayRatio = ref(0.5)
const modelReleaseUserIdsText = ref('')

const modelProviders = [
  { value: 'bailian', label: '百炼 (阿里云 DashScope)' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'siliconflow', label: '硅基流动' },
  { value: 'openai', label: 'OpenAI' },
  { value: 'agnes', label: 'Agnes AI' },
  { value: 'custom', label: '自定义（OpenAI 兼容）' }
]

const PROVIDER_BASE_URLS: Record<string, string> = {
  bailian: 'https://dashscope.aliyuncs.com/compatible-mode',
  deepseek: 'https://api.deepseek.com',
  siliconflow: 'https://api.siliconflow.cn',
  openai: 'https://api.openai.com',
  agnes: 'https://api.agnes-ai.cn',
  custom: ''
}

async function loadModelConfigs() {
  modelLoading.value = true
  try {
    const res = await request.get('/rag-lab/model-config/list') as any
    modelConfigs.value = res.data || res || []
  } catch (e) {
    console.error(e)
  } finally {
    modelLoading.value = false
  }
}

function openCreateModelDialog() {
  modelForm.value = {
    modelName: '',
    provider: 'bailian',
    baseUrl: PROVIDER_BASE_URLS['bailian'],
    apiKey: '',
    modelId: '',
    maxTokens: 4096,
    temperature: 0.7
  }
  createModelDialogVisible.value = true
}

async function submitCreateModel() {
  const f = modelForm.value
  if (!f.modelName.trim()) { ElMessage.warning('请填写模型名称'); return }
  if (!f.baseUrl.trim()) { ElMessage.warning('请填写 Base URL'); return }
  if (!f.apiKey.trim()) { ElMessage.warning('请填写 API Key'); return }
  if (!f.modelId.trim()) { ElMessage.warning('请填写模型 ID'); return }
  modelCreating.value = true
  try {
    await request.post('/rag-lab/model-config', {
      modelName: f.modelName,
      provider: f.provider,
      baseUrl: f.baseUrl,
      apiKey: f.apiKey,
      modelId: f.modelId,
      maxTokens: f.maxTokens,
      temperature: f.temperature
    })
    ElMessage.success('模型配置已创建')
    createModelDialogVisible.value = false
    await loadModelConfigs()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    modelCreating.value = false
  }
}

/** 从模型配置页发起发布 */
function createReleaseFromModel() {
  // 收集所有激活的模型 ID
  const activeModels = modelConfigs.value.filter((m: any) => m.isActive === 1)
  if (activeModels.length === 0) {
    ElMessage.warning('没有已激活的模型，请先激活至少一个模型')
    return
  }
  // 初始化表单
  modelReleasePlanName.value = ''
  modelReleaseGrayMode.value = 'PERCENT'
  modelReleaseGrayRatio.value = 0.5
  modelReleaseUserIdsText.value = ''
  selectedModelIds.value = []
  modelReleaseOptions.value = activeModels.map((m: any) => ({
    value: m.id,
    label: `${m.modelName} (${m.modelId})`
  }))
  // 打开模型选择对话框
  modelReleaseSelectVisible.value = true
}

/** 提交模型发布计划（简化版：一步完成） */
async function submitModelReleasePlan() {
  if (!modelReleasePlanName.value.trim()) {
    ElMessage.warning('请填写计划名称')
    return
  }
  if (selectedModelIds.value.length === 0) {
    ElMessage.warning('请至少选择一个模型')
    return
  }
  releaseCreating.value = true
  try {
    const userIds = modelReleaseUserIdsText.value
      .split(/[\n,，]/).map((s: string) => s.trim()).filter((s: string) => s && !isNaN(Number(s))).map(Number)
    const payload = {
      plan: {
        planName: modelReleasePlanName.value,
        componentType: 'MODEL',
        componentId: null,
        grayMode: modelReleaseGrayMode.value,
        grayRatio: modelReleaseGrayMode.value !== 'LIST' ? modelReleaseGrayRatio.value : null,
        grayUserIds: modelReleaseGrayMode.value !== 'PERCENT' ? JSON.stringify(userIds) : null
      },
      modelConfigIds: selectedModelIds.value
    }
    await request.post('/rag-lab/gray-release/model', payload)
    ElMessage.success('模型发布计划已创建')
    modelReleaseSelectVisible.value = false
    await loadReleasePlans()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    releaseCreating.value = false
  }
}

async function toggleModelActive(id: number) {
  try {
    await request.post(`/rag-lab/model-config/${id}/toggle-active`)
    ElMessage.success('已切换')
    await loadModelConfigs()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function testModel(id: number) {
  const model = modelConfigs.value.find((m: any) => m.id === id)
  if (model) model._testing = true
  try {
    const res = await request.post(`/rag-lab/model-config/${id}/test`)
    if (res) {
      ElMessage.success('连通性测试通过')
    } else {
      ElMessage.error('连通性测试失败')
    }
  } catch (e) {
    ElMessage.error('测试异常')
  } finally {
    if (model) model._testing = false
  }
}

async function deleteModel(id: number) {
  try {
    await ElMessageBox.confirm('确认删除此模型配置？', '删除确认')
    await request.delete(`/rag-lab/model-config/${id}`)
    ElMessage.success('已删除')
    await loadModelConfigs()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

function openEditModelDialog(row: any) {
  editModelForm.value = {
    id: row.id,
    modelName: row.modelName,
    provider: row.provider,
    baseUrl: row.baseUrl,
    apiKey: row.apiKey || '',
    modelId: row.modelId,
    maxTokens: row.maxTokens || 4096,
    temperature: row.temperature || 0.7
  }
  editModelDialogVisible.value = true
}

async function submitEditModel() {
  const f = editModelForm.value
  if (!f.modelName.trim()) { ElMessage.warning('请填写模型名称'); return }
  if (!f.baseUrl.trim()) { ElMessage.warning('请填写 Base URL'); return }
  if (!f.apiKey.trim()) { ElMessage.warning('请填写 API Key'); return }
  if (!f.modelId.trim()) { ElMessage.warning('请填写模型 ID'); return }
  modelEditing.value = true
  try {
    await request.put('/rag-lab/model-config', {
      id: f.id,
      modelName: f.modelName,
      provider: f.provider,
      baseUrl: f.baseUrl,
      apiKey: f.apiKey,
      modelId: f.modelId,
      maxTokens: f.maxTokens,
      temperature: f.temperature
    })
    ElMessage.success('模型配置已更新')
    editModelDialogVisible.value = false
    await loadModelConfigs()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '更新失败')
  } finally {
    modelEditing.value = false
  }
}

// ==================== Tab 6: 数据回放 ====================
const replayRequests = ref<any[]>([])
const replayLoading = ref(false)

// 手动提交回放
const manualReplayDialogVisible = ref(false)
const manualReplaySubmitting = ref(false)
const manualReplayForm = ref({ traceId: '', questionText: '' })

async function loadReplayRequests() {
  replayLoading.value = true
  try {
    const res = await request.get('/rag-lab/data-replay/list') as any
    replayRequests.value = res.data || res || []
  } catch (e) {
    console.error(e)
  } finally {
    replayLoading.value = false
  }
}

async function approveReplay(id: number) {
  try {
    await request.post(`/rag-lab/data-replay/${id}/approve`, null, { params: { approvedBy: 1 } })
    ElMessage.success('审批通过，已写入测试题库')
    await loadReplayRequests()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function rejectReplay(id: number) {
  try {
    await request.post(`/rag-lab/data-replay/${id}/reject`, null, { params: { approvedBy: auth.userInfo?.id || 1 } })
    ElMessage.success('已拒绝')
    await loadReplayRequests()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

function openManualReplayDialog() {
  manualReplayForm.value = { traceId: '', questionText: '' }
  manualReplayDialogVisible.value = true
}

async function submitManualReplay() {
  if (!manualReplayForm.value.traceId.trim() || !manualReplayForm.value.questionText.trim()) {
    ElMessage.warning('请填写 traceId 和提问内容')
    return
  }
  manualReplaySubmitting.value = true
  try {
    await request.post('/rag-lab/data-replay', {
      traceId: manualReplayForm.value.traceId,
      questionText: manualReplayForm.value.questionText,
      createUserId: auth.userInfo?.id
    })
    ElMessage.success('回放申请已提交')
    manualReplayDialogVisible.value = false
    await loadReplayRequests()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '提交失败')
  } finally {
    manualReplaySubmitting.value = false
  }
}

// ==================== 初始化 ====================
onMounted(async () => {
  await loadConfig()
  await loadPrompts()
  await loadTestQuestions()
  await loadExperimentPlans()
  await loadReleasePlans()
  await loadModelConfigs()
  await loadReplayRequests()
  await loadIntentNodes()
  await loadKnowledgeBases()
})
</script>

<style scoped>
.rag-lab-page {
  padding: 16px;
}
.param-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
.prompt-type-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
  margin-bottom: 4px;
}
.prompt-type-item:hover {
  background-color: #f0f2f5;
}
.prompt-type-item.active {
  background-color: #ecf5ff;
  border-left: 3px solid #409eff;
}
</style>

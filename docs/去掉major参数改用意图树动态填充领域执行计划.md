# 去掉 major 参数、改用意图树动态填充领域 —— 执行计划

> 日期：2026-05-17

## 一、背景与目标

当前 `streamChatWithRagPipeline` 和 `streamChatSmart` 两个接口均接受一个 `major`（专业领域）参数，系统提示词 `SYSTEM_MASTER_PROMPT` 中有 `{name}` 和 `{major}` 两个占位符。

改造目标：
1. 去掉所有层级的 `major` 入参（Controller / Service 接口 / Service 实现）
2. 修改 `SYSTEM_MASTER_PROMPT`，将 `{major}` 改为 `{domain}`
3. 在流水线内通过意图分类结果动态填充 `{domain}`；unknown 或 isSystem=true 时默认"各领域"
4. `name` 参数保持不变

## 二、改动文件清单

| 序号 | 文件 | 改动类型 |
|------|------|----------|
| 1 | `constants/SystemConstants.java` | 修改常量，{major} → {domain} |
| 2 | `service/ChatService.java` | 接口签名去掉 major |
| 3 | `service/impl/ChatServiceImpl.java` | 实现去掉 major，注入 IntentClassifier，动态填充 domain |
| 4 | `controller/ChatController.java` | 端点入参去掉 major |

## 三、改动顺序

步骤1：SystemConstants.java → 步骤2：ChatService.java → 步骤3：ChatServiceImpl.java → 步骤4：ChatController.java

## 四、domain 取值规则

- 意图分类命中具体节点（confidence > 0，isSystem=false）→ 使用 IntentResult.getLabel()
- unknown（confidence=0）或 isSystem=true → "各领域"
- chatWithRag / streamChatWithRag 旧接口（无意图分类）→ 固定"各领域"

## 五、测试要点（供哈吉霞参考）

1. streamChatSmart 不传 major，正常访问无报错
2. 意图命中具体节点，系统提示词领域 = 节点 label
3. 意图 unknown，领域 = "各领域"
4. 意图 isSystem=true，领域 = "各领域"
5. name 参数正确替换
6. Swagger 确认 major 参数已消失

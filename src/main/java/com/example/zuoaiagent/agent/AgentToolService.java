package com.example.zuoaiagent.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AgentToolService {

    @Tool(description = "申请开通新租户。用户需要提供租户名称和申请理由")
    public String applyTenant(
            @ToolParam(description = "租户名称") String tenantName,
            @ToolParam(description = "申请理由") String reason) {
        return "已收到租户开通申请：租户名称=" + tenantName + "，申请理由=" + reason + "。申请已提交，请等待管理员审批。";
    }

    @Tool(description = "申请角色权限。用户需要指定角色名称和申请理由")
    public String applyRolePermission(
            @ToolParam(description = "角色名称") String roleName,
            @ToolParam(description = "申请理由") String reason) {
        return "已收到角色权限申请：角色=" + roleName + "，申请理由=" + reason + "。申请已提交，请等待管理员审批。";
    }

    @Tool(description = "申请知识库访问权限。用户需要指定知识库名称和申请理由")
    public String applyKnowledgeBaseAccess(
            @ToolParam(description = "知识库名称") String kbName,
            @ToolParam(description = "申请理由") String reason) {
        return "已收到知识库访问申请：知识库=" + kbName + "，申请理由=" + reason + "。申请已提交，请等待知识库owner审批。";
    }
}
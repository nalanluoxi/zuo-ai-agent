package com.example.zuoaiagent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler()));
        return interceptor;
    }

    static class TenantLineHandler implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {
        @Override
        public String getTenantIdColumn() {
            return "tenant_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            return java.util.Set.of("t_tenant", "t_role", "t_permission", "t_audit_log", "t_intent_node",
                    "t_rag_trace_run", "t_rag_trace_node", "t_chat_memory", "t_chat_message_raw",
                    "t_chat_message_compression", "vector_store",
                    "t_rag_config", "t_rag_config_version", "t_rag_prompt_template",
                    "t_rag_prompt_version", "t_rag_test_question", "t_rag_test_document",
                    "t_rag_experiment", "t_gray_release_plan", "t_gray_release_plan_model").contains(tableName);
        }

        @Override
        public Expression getTenantId() {
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId == null) {
                tenantId = 1L;
            }
            return new LongValue(tenantId);
        }
    }
}
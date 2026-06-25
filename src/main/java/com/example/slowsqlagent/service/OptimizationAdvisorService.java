package com.example.slowsqlagent.service;

import com.example.slowsqlagent.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class OptimizationAdvisorService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public OptimizationAdvisorService(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public AnalysisReport advise(SqlAnalysisContext context) {
        AnalysisReport report = baseReport(context);
        try {
            String response = chatClient
                    .prompt()
                    .system("""
                            你是一个谨慎的数据库性能诊断助手。只给读取与诊断建议，不允许建议自动执行变更。
                            必须用中文回答，并严格输出 JSON，不要输出 Markdown。
                            """)
                    .user(buildPrompt(context))
                    .call()
                    .content();
            mergeAiResponse(report, response);
            if (report.getRecommendations().isEmpty()) {
                applyFallbackAdvice(context, report, "AI 未返回可解析的优化方案，已补充本地规则建议。");
            }
            return report;
        } catch (Exception ex) {
            applyFallbackAdvice(context, report, "AI 分析失败：" + ex.getMessage());
            return report;
        }
    }

    private AnalysisReport baseReport(SqlAnalysisContext context) {
        AnalysisReport report = new AnalysisReport();
        report.setExecutionPlan(context.getExecutionPlan());
        report.getTableRefs().addAll(context.getTableRefs());
        report.getTableMetadata().addAll(context.getTableMetadata());
        report.getWarnings().addAll(context.getWarnings());
        return report;
    }

    private String buildPrompt(SqlAnalysisContext context) {
        String template = String.join(System.lineSeparator(),
                "请基于下面的慢 SQL 诊断上下文，输出 JSON：",
                "{",
                "  \"summary\": \"一句话概括问题\",",
                "  \"rootCause\": \"可能根因\",",
                "  \"confidence\": \"high|medium|low\",",
                "  \"recommendations\": [",
                "    {",
                "      \"priority\": 1,",
                "      \"title\": \"方案标题\",",
                "      \"strategy\": \"具体优化策略，包含 SQL 改写或索引建议，但不要说可以自动执行\",",
                "      \"expectedBenefit\": \"预计收益\",",
                "      \"riskLevel\": \"low|medium|high\",",
                "      \"validationSql\": \"验证效果用的 EXPLAIN 或查询语句\",",
                "      \"rollbackHint\": \"如果涉及索引或结构变更，说明回滚思路；纯 SQL 改写则说明无需结构回滚\"",
                "    }",
                "  ],",
                "  \"warnings\": [\"必要的风险提醒\"]",
                "}",
                "",
                "慢 SQL:",
                "%s",
                "",
                "统计信息:",
                "schema=%s, source=%s, executions=%d, avgMs=%.2f, maxMs=%.2f, rowsExamined=%d, rowsSent=%d",
                "",
                "涉及表:",
                "%s",
                "",
                "表结构和索引:",
                "%s",
                "",
                "执行计划:",
                "%s");
        return String.format(Locale.ROOT, template,
                trim(context.getSlowSqlRecord().getSqlText(), 4000),
                context.getSlowSqlRecord().getSchemaName(),
                context.getSlowSqlRecord().getSource(),
                context.getSlowSqlRecord().getExecutionCount(),
                context.getSlowSqlRecord().getAvgLatencyMs(),
                context.getSlowSqlRecord().getMaxLatencyMs(),
                context.getSlowSqlRecord().getRowsExamined(),
                context.getSlowSqlRecord().getRowsSent(),
                context.getTableRefs().stream().map(TableRef::displayName).collect(Collectors.joining(", ")),
                trim(formatMetadata(context.getTableMetadata()), 8000),
                trim(formatPlan(context), 8000));
    }

    private void mergeAiResponse(AnalysisReport report, String response) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(response));
        report.setSummary(text(root, "summary"));
        report.setRootCause(text(root, "rootCause"));
        report.setConfidence(text(root, "confidence"));
        JsonNode recommendations = root.path("recommendations");
        if (recommendations.isArray()) {
            for (JsonNode item : recommendations) {
                OptimizationAdvice advice = new OptimizationAdvice();
                advice.setPriority(item.path("priority").asInt(report.getRecommendations().size() + 1));
                advice.setTitle(text(item, "title"));
                advice.setStrategy(text(item, "strategy"));
                advice.setExpectedBenefit(text(item, "expectedBenefit"));
                advice.setRiskLevel(text(item, "riskLevel"));
                advice.setValidationSql(text(item, "validationSql"));
                advice.setRollbackHint(text(item, "rollbackHint"));
                report.getRecommendations().add(advice);
            }
        }
        JsonNode warnings = root.path("warnings");
        if (warnings.isArray()) {
            warnings.forEach(item -> report.getWarnings().add(item.asText()));
        }
    }

    private void applyFallbackAdvice(SqlAnalysisContext context, AnalysisReport report, String warning) {
        report.getWarnings().add(warning);
        report.setSummary("已完成慢 SQL 上下文采集，可先从执行计划、索引覆盖和扫描行数入手排查。");
        report.setRootCause(inferRootCause(context));
        report.setConfidence("medium");

        OptimizationAdvice first = new OptimizationAdvice();
        first.setPriority(1);
        first.setTitle("检查 WHERE / JOIN / ORDER BY 字段的联合索引");
        first.setStrategy("结合执行计划中的访问类型、扫描行数和排序信息，确认过滤条件、关联条件、排序字段是否能被同一个高选择性的联合索引覆盖。新增索引前应先评估表大小、写入频率和现有冗余索引。");
        first.setExpectedBenefit("通常可减少全表扫描、回表和 filesort，适合读多写少且过滤条件稳定的查询。");
        first.setRiskLevel("medium");
        first.setValidationSql("EXPLAIN FORMAT=JSON " + context.getSlowSqlRecord().getSqlText());
        first.setRollbackHint("如果新增索引，应准备 DROP INDEX 回滚语句，并在低峰期验证其他 SQL 的执行计划变化。");
        report.getRecommendations().add(first);

        OptimizationAdvice second = new OptimizationAdvice();
        second.setPriority(2);
        second.setTitle("减少返回列和扫描范围");
        second.setStrategy("避免 SELECT *，只返回业务必需字段；对深分页、宽范围时间查询和低选择性条件增加更精确的过滤条件或改造成基于游标的分页。");
        second.setExpectedBenefit("可降低网络传输、回表成本和临时结果集大小。");
        second.setRiskLevel("low");
        second.setValidationSql("对比优化前后的 EXPLAIN FORMAT=JSON 与实际耗时。");
        second.setRollbackHint("SQL 改写无需结构回滚，但必须通过业务语义测试确认结果一致。");
        report.getRecommendations().add(second);
    }

    private String inferRootCause(SqlAnalysisContext context) {
        String plan = formatPlan(context).toLowerCase();
        if (plan.contains("filesort")) {
            return "执行计划出现 filesort，排序字段可能没有被合适的索引顺序支持。";
        }
        if (plan.contains("\"access_type\": \"all\"") || plan.contains("table scan")) {
            return "执行计划疑似全表扫描，过滤条件可能缺少有效索引或索引选择性不足。";
        }
        if (context.getSlowSqlRecord().getRowsExamined() > Math.max(1000, context.getSlowSqlRecord().getRowsSent() * 100)) {
            return "扫描行数明显高于返回行数，可能存在过滤效率低或索引未命中的问题。";
        }
        return "需要结合执行计划和表统计信息进一步确认，当前慢查询可能与索引选择、排序、回表或返回数据量有关。";
    }

    private String formatMetadata(List<TableMetadata> metadataList) {
        StringBuilder builder = new StringBuilder();
        for (TableMetadata metadata : metadataList) {
            builder.append("TABLE ").append(metadata.getTableRef().displayName()).append('\n');
            if (StringUtils.hasText(metadata.getError())) {
                builder.append("ERROR: ").append(metadata.getError()).append("\n\n");
                continue;
            }
            builder.append("estimatedRows=").append(metadata.getEstimatedRows())
                    .append(", dataLengthBytes=").append(metadata.getDataLengthBytes())
                    .append(", indexLengthBytes=").append(metadata.getIndexLengthBytes())
                    .append('\n');
            builder.append(metadata.getCreateTableSql()).append('\n');
            builder.append("INDEXES:\n");
            for (IndexMetadata index : metadata.getIndexes()) {
                builder.append("- ").append(index.getIndexName())
                        .append("(").append(index.getSequence()).append(":").append(index.getColumnName()).append(")")
                        .append(", unique=").append(index.isUnique())
                        .append(", cardinality=").append(index.getCardinality())
                        .append(", type=").append(index.getIndexType())
                        .append('\n');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private String formatPlan(SqlAnalysisContext context) {
        if (context.getExecutionPlan() == null) {
            return "";
        }
        if (StringUtils.hasText(context.getExecutionPlan().getError())) {
            return "ERROR: " + context.getExecutionPlan().getError();
        }
        return context.getExecutionPlan().getContent();
    }

    private String extractJson(String response) {
        if (response == null) {
            return "{}";
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...内容已截断...";
    }
}

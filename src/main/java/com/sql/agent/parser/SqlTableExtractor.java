package com.sql.agent.parser;

import com.sql.agent.config.AgentProperties;
import com.sql.agent.domain.TableRef;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlTableExtractor {

    private static final Pattern FALLBACK_TABLE_PATTERN = Pattern.compile(
            "(?i)\\b(from|join|update|into)\\s+`?([A-Za-z0-9_$]+)`?(?:\\.`?([A-Za-z0-9_$]+)`?)?");

    private final AgentProperties properties;

    public SqlTableExtractor(AgentProperties properties) {
        this.properties = properties;
    }

    public List<TableRef> extract(String sqlText, String preferredSchema) {
        Set<TableRef> refs = new LinkedHashSet<>();
        try {
            Statement statement = CCJSqlParserUtil.parse(sqlText);
            TablesNamesFinder finder = new TablesNamesFinder();
            for (String tableName : finder.getTableList(statement)) {
                refs.add(toTableRef(tableName, preferredSchema));
            }
        } catch (Exception ignored) {
            refs.addAll(fallbackExtract(sqlText, preferredSchema));
        }
        return new ArrayList<>(refs);
    }

    private List<TableRef> fallbackExtract(String sqlText, String preferredSchema) {
        List<TableRef> refs = new ArrayList<>();
        if (!StringUtils.hasText(sqlText)) {
            return refs;
        }
        Matcher matcher = FALLBACK_TABLE_PATTERN.matcher(sqlText);
        while (matcher.find()) {
            String first = matcher.group(2);
            String second = matcher.group(3);
            if (StringUtils.hasText(second)) {
                refs.add(new TableRef(clean(first), clean(second)));
            } else {
                refs.add(new TableRef(resolveSchema(preferredSchema), clean(first)));
            }
        }
        return refs;
    }

    private TableRef toTableRef(String rawTableName, String preferredSchema) {
        String cleaned = rawTableName.replace("`", "").trim();
        String[] parts = cleaned.split("\\.");
        if (parts.length >= 2) {
            return new TableRef(clean(parts[parts.length - 2]), clean(parts[parts.length - 1]));
        }
        return new TableRef(resolveSchema(preferredSchema), clean(cleaned));
    }

    private String resolveSchema(String preferredSchema) {
        if (StringUtils.hasText(preferredSchema)) {
            return preferredSchema;
        }
        return properties.getDatabase().getDefaultSchema();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }
}

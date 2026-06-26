package com.sql.agent.util;

import com.sql.agent.domain.SlowSqlRecord;

import java.util.Locale;

public final class SlowSqlKeys {

    private SlowSqlKeys() {
    }

    public static String deduplicateKey(SlowSqlRecord record) {
        return normalizeSchema(record.getSchemaName()) + "|" + fingerprintSql(record.getSqlText());
    }

    public static String normalizeSchema(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    public static String fingerprintSql(String value) {
        if (value == null) {
            return "";
        }
        String normalized = stripLeadingComments(value).toLowerCase(Locale.ROOT).trim();
        normalized = normalized.replace('`', ' ');
        normalized = normalized.replaceAll("'([^'\\\\]|\\\\.)*'", "?");
        normalized = normalized.replaceAll("\"([^\"\\\\]|\\\\.)*\"", "?");
        normalized = normalized.replaceAll("\\b\\d+(?:\\.\\d+)?\\b", "?");
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("\\s*([(),=<>+\\-*/%])\\s*", "$1");
        normalized = normalized.replace("count(?)", "count(*)");
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.trim();
    }

    public static String stripLeadingComments(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.trim();
        boolean changed;
        do {
            changed = false;
            if (stripped.startsWith("/*")) {
                int end = stripped.indexOf("*/");
                if (end >= 0) {
                    stripped = stripped.substring(end + 2).trim();
                    changed = true;
                }
            } else if (stripped.startsWith("--")) {
                int end = stripped.indexOf('\n');
                if (end >= 0) {
                    stripped = stripped.substring(end + 1).trim();
                    changed = true;
                } else {
                    return "";
                }
            } else if (stripped.startsWith("#")) {
                int end = stripped.indexOf('\n');
                if (end >= 0) {
                    stripped = stripped.substring(end + 1).trim();
                    changed = true;
                } else {
                    return "";
                }
            }
        } while (changed);
        return stripped;
    }
}

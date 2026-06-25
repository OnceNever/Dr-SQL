(function () {
    const keywords = new Set([
        "ADD", "ALTER", "AND", "AS", "ASC", "BETWEEN", "BY", "CASE", "CREATE", "CROSS",
        "CURRENT_TIMESTAMP", "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "END",
        "EXISTS", "EXPLAIN", "FALSE", "FOR", "FOREIGN", "FROM", "FULL", "GROUP", "HAVING",
        "IN", "INDEX", "INNER", "INSERT", "INTERVAL", "INTO", "IS", "JOIN", "KEY", "LEFT",
        "LIKE", "LIMIT", "NOT", "NULL", "ON", "OR", "ORDER", "OUTER", "PRIMARY", "REFERENCES",
        "RIGHT", "SELECT", "SET", "TABLE", "THEN", "TRUE", "UNION", "UNIQUE", "UPDATE",
        "VALUES", "WHEN", "WHERE", "WITH"
    ]);

    const dataTypes = new Set([
        "BIGINT", "BINARY", "BIT", "BLOB", "CHAR", "DATE", "DATETIME", "DECIMAL", "DOUBLE",
        "ENUM", "FLOAT", "INT", "INTEGER", "JSON", "LONGTEXT", "MEDIUMINT", "MEDIUMTEXT",
        "SMALLINT", "TEXT", "TIME", "TIMESTAMP", "TINYINT", "VARCHAR"
    ]);

    const functions = new Set([
        "AVG", "COALESCE", "COUNT", "DATE_FORMAT", "IFNULL", "MAX", "MIN", "NOW", "SUM"
    ]);

    const tokenPattern = /\/\*[\s\S]*?\*\/|--[^\n\r]*|#[^\n\r]*|'(?:''|\\'|[^'])*'|"(?:\\"|[^"])*"|`[^`]*`|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b|[(),.;=*<>!+\-/%]/g;

    function escapeHtml(value) {
        return value
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function classForToken(token) {
        const upper = token.toUpperCase();
        if (token.startsWith("'") || token.startsWith("\"")) {
            return "sql-string";
        }
        if (token.startsWith("`")) {
            return "sql-identifier";
        }
        if (token.startsWith("--") || token.startsWith("#") || token.startsWith("/*")) {
            return "sql-comment";
        }
        if (/^\d/.test(token)) {
            return "sql-number";
        }
        if (keywords.has(upper)) {
            return "sql-keyword";
        }
        if (dataTypes.has(upper)) {
            return "sql-type";
        }
        if (functions.has(upper)) {
            return "sql-function";
        }
        if (/^[(),.;=*<>!+\-/%]$/.test(token)) {
            return "sql-operator";
        }
        return "";
    }

    function highlight(sql) {
        let html = "";
        let cursor = 0;
        sql.replace(tokenPattern, function (token, offset) {
            html += escapeHtml(sql.slice(cursor, offset));
            const tokenClass = classForToken(token);
            const escaped = escapeHtml(token);
            html += tokenClass ? `<span class="${tokenClass}">${escaped}</span>` : escaped;
            cursor = offset + token.length;
            return token;
        });
        html += escapeHtml(sql.slice(cursor));
        return html;
    }

    function formatSqlForDisplay(sql) {
        const normalized = sql.trim();
        if (normalized.indexOf("\n") >= 0 || normalized.length < 90) {
            return sql;
        }
        return normalized
            .replace(/\s+/g, " ")
            .replace(/\b(FROM|WHERE|GROUP\s+BY|HAVING|ORDER\s+BY|LIMIT|UNION|VALUES)\b/gi, "\n$1")
            .replace(/\b((?:LEFT|RIGHT|INNER|OUTER|CROSS|FULL)\s+)?JOIN\b/gi, "\n$1JOIN")
            .replace(/\b(AND|OR)\b/gi, "\n  $1")
            .replace(/,\s*/g, ",\n  ")
            .replace(/\(\s*SELECT\b/gi, "(\nSELECT")
            .trim();
    }

    document.querySelectorAll("pre.sql-code").forEach(function (element) {
        element.innerHTML = highlight(formatSqlForDisplay(element.textContent));
    });
})();

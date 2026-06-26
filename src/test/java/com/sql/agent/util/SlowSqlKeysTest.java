package com.sql.agent.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlowSqlKeysTest {

    @Test
    void fingerprintsRawSqlAndPerformanceSchemaDigestTheSame() {
        String rawSql = "SELECT COUNT(*) FROM user_info WHERE province = 'Zhejiang'";
        String digestSql = "SELECT COUNT ( ? ) FROM `user_info` WHERE `province` = ?";

        assertThat(SlowSqlKeys.fingerprintSql(rawSql))
                .isEqualTo(SlowSqlKeys.fingerprintSql(digestSql));
    }

    @Test
    void ignoresLeadingClientCommentsInFingerprint() {
        String rawSql = "/* ApplicationName=DataGrip 2026.1.3 */ SELECT * FROM orders WHERE amount > 100";
        String digestSql = "SELECT * FROM `orders` WHERE `amount` > ?";

        assertThat(SlowSqlKeys.fingerprintSql(rawSql))
                .isEqualTo(SlowSqlKeys.fingerprintSql(digestSql));
    }

    @Test
    void stripsLeadingClientCommentsForDisplay() {
        assertThat(SlowSqlKeys.stripLeadingComments("""
                /* ApplicationName=DataGrip 2026.1.3 */
                -- local note
                SELECT * FROM orders
                """)).isEqualTo("SELECT * FROM orders");
    }

    @Test
    void fingerprintsFunctionCallsTheSame() {
        String rawSql = "SELECT SLEEP(2)";
        String digestSql = "SELECT `SLEEP` ( ? )";

        assertThat(SlowSqlKeys.fingerprintSql(rawSql))
                .isEqualTo(SlowSqlKeys.fingerprintSql(digestSql));
    }
}

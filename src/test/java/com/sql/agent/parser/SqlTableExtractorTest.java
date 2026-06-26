package com.sql.agent.parser;

import com.sql.agent.config.AgentProperties;
import com.sql.agent.domain.TableRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlTableExtractorTest {

    @Test
    void extractsTablesFromJoinQuery() {
        AgentProperties properties = new AgentProperties();
        properties.getDatabase().setDefaultSchema("shop");
        SqlTableExtractor extractor = new SqlTableExtractor(properties);

        List<TableRef> refs = extractor.extract("""
                SELECT o.id, u.nickname
                FROM orders o
                JOIN users u ON u.id = o.user_id
                WHERE o.status = 'PAID'
                """, "shop");

        assertThat(refs).extracting(TableRef::displayName)
                .containsExactlyInAnyOrder("shop.orders", "shop.users");
    }
}

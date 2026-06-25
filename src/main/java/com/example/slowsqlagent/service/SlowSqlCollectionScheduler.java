package com.example.slowsqlagent.service;

import com.example.slowsqlagent.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlowSqlCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlCollectionScheduler.class);

    private final AgentProperties properties;
    private final SlowSqlCollectionService collectionService;

    public SlowSqlCollectionScheduler(AgentProperties properties, SlowSqlCollectionService collectionService) {
        this.properties = properties;
        this.collectionService = collectionService;
    }

    @Scheduled(fixedDelayString = "${agent.collection.fixed-delay-ms:300000}")
    public void collectIfEnabled() {
        if (!properties.getCollection().isEnabled()) {
            return;
        }
        try {
            int count = collectionService.collect().size();
            log.info("Collected {} slow SQL records.", count);
        } catch (Exception ex) {
            log.warn("Slow SQL collection failed: {}", ex.getMessage());
        }
    }
}

package com.sql.agent.service;

import com.sql.agent.config.AgentProperties;
import com.sql.agent.domain.AnalysisStatus;
import com.sql.agent.domain.SlowSqlRecord;
import com.sql.agent.util.SlowSqlKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SlowSqlRepository {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlRepository.class);

    private final Map<String, SlowSqlRecord> records = new ConcurrentHashMap<>();
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public SlowSqlRepository(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadSnapshot() {
        Path path = storagePath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            List<SlowSqlRecord> loaded = objectMapper.readValue(path.toFile(), new TypeReference<>() {
            });
            for (SlowSqlRecord record : loaded) {
                normalizeLoadedRecord(record);
                records.put(record.getId(), record);
            }
            log.info("Loaded {} slow SQL records from {}.", records.size(), path.toAbsolutePath());
        } catch (Exception ex) {
            log.warn("Failed to load slow SQL snapshot from {}: {}", path.toAbsolutePath(), ex.getMessage(), ex);
        }
    }

    public List<SlowSqlRecord> findAll() {
        return records.values().stream()
                .sorted(Comparator.comparing(
                                SlowSqlRecord::getCollectedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(Comparator.comparing(SlowSqlRecord::getAvgLatencyMs).reversed()))
                .toList();
    }

    public Optional<SlowSqlRecord> findById(String id) {
        return Optional.ofNullable(records.get(id));
    }

    public synchronized void save(SlowSqlRecord record) {
        records.merge(record.getId(), record, this::merge);
        persistSnapshot();
    }

    public synchronized SaveStats saveAll(List<SlowSqlRecord> newRecords) {
        int insertedCount = 0;
        int updatedCount = 0;
        for (SlowSqlRecord record : newRecords) {
            List<SlowSqlRecord> duplicates = findDuplicates(record);
            boolean idExists = records.containsKey(record.getId());
            boolean sameSqlExists = !duplicates.isEmpty();
            if (!shouldSave(record, duplicates)) {
                continue;
            }
            inheritAnalysis(record, duplicates);
            removeLowerPriorityDuplicates(record, duplicates);
            if (idExists || sameSqlExists) {
                updatedCount++;
            } else {
                insertedCount++;
            }
            records.merge(record.getId(), record, this::merge);
        }
        persistSnapshot();
        return new SaveStats(insertedCount, updatedCount);
    }

    public int count() {
        return records.size();
    }

    public synchronized void clear() {
        records.clear();
        persistSnapshot();
    }

    private SlowSqlRecord merge(SlowSqlRecord existing, SlowSqlRecord incoming) {
        if (existing.getAnalysisReport() != null) {
            incoming.setAnalysisReport(existing.getAnalysisReport());
        }
        incoming.setAnalysisStatus(existing.getAnalysisStatus());
        incoming.setAnalysisError(existing.getAnalysisError());
        incoming.setAnalysisStartedAt(existing.getAnalysisStartedAt());
        incoming.setAnalysisCompletedAt(existing.getAnalysisCompletedAt());
        if (incoming.getCollectedAt() == null) {
            incoming.setCollectedAt(existing.getCollectedAt());
        }
        return incoming;
    }

    private void normalizeLoadedRecord(SlowSqlRecord record) {
        if (record.getCollectedAt() == null) {
            record.setCollectedAt(record.getLastSeen() == null ? record.getFirstSeen() : record.getLastSeen());
        }
        if (isSlowLog(record)) {
            record.setSqlText(SlowSqlKeys.stripLeadingComments(record.getSqlText()));
        }
        if (record.getAnalysisStatus() == AnalysisStatus.ANALYZING) {
            record.setAnalysisStatus(AnalysisStatus.FAILED);
            record.setAnalysisError("应用重启导致上次分析中断，可重新分析。");
            record.setAnalysisCompletedAt(Instant.now());
        }
    }

    private void persistSnapshot() {
        Path path = storagePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), findAll());
            moveSnapshot(temp, path);
        } catch (IOException ex) {
            log.warn("Failed to persist slow SQL snapshot: {}", ex.getMessage(), ex);
        }
    }

    private void moveSnapshot(Path temp, Path path) throws IOException {
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path storagePath() {
        return Path.of(properties.getStorage().getFile());
    }

    private List<SlowSqlRecord> findDuplicates(SlowSqlRecord incoming) {
        String incomingKey = deduplicateKey(incoming);
        return records.values().stream()
                .filter(existing -> !existing.getId().equals(incoming.getId()))
                .filter(existing -> deduplicateKey(existing).equals(incomingKey))
                .toList();
    }

    private boolean shouldSave(SlowSqlRecord incoming, List<SlowSqlRecord> duplicates) {
        if (isSlowLog(incoming)) {
            return true;
        }
        return duplicates.stream().noneMatch(this::isSlowLog);
    }

    private void inheritAnalysis(SlowSqlRecord incoming, List<SlowSqlRecord> duplicates) {
        if (incoming.getAnalysisReport() != null || duplicates.isEmpty()) {
            return;
        }
        SlowSqlRecord analyzedDuplicate = duplicates.stream()
                .filter(SlowSqlRecord::hasAnalysis)
                .findFirst()
                .orElse(duplicates.get(0));
        incoming.setAnalysisReport(analyzedDuplicate.getAnalysisReport());
        incoming.setAnalysisStatus(analyzedDuplicate.getAnalysisStatus());
        incoming.setAnalysisError(analyzedDuplicate.getAnalysisError());
        incoming.setAnalysisStartedAt(analyzedDuplicate.getAnalysisStartedAt());
        incoming.setAnalysisCompletedAt(analyzedDuplicate.getAnalysisCompletedAt());
    }

    private void removeLowerPriorityDuplicates(SlowSqlRecord incoming, List<SlowSqlRecord> duplicates) {
        List<String> idsToRemove = new ArrayList<>();
        for (SlowSqlRecord duplicate : duplicates) {
            if (isSlowLog(incoming) || !isSlowLog(duplicate)) {
                idsToRemove.add(duplicate.getId());
            }
        }
        idsToRemove.forEach(records::remove);
    }

    private boolean isSlowLog(SlowSqlRecord record) {
        return record.getSource() != null
                && ("slow_log".equalsIgnoreCase(record.getSource())
                || "slow_log_table".equalsIgnoreCase(record.getSource()));
    }

    private String deduplicateKey(SlowSqlRecord record) {
        return SlowSqlKeys.deduplicateKey(record);
    }

    public record SaveStats(int insertedCount, int updatedCount) {
    }
}

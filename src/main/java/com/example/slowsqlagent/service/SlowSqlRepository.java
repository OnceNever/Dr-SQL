package com.example.slowsqlagent.service;

import com.example.slowsqlagent.config.AgentProperties;
import com.example.slowsqlagent.domain.AnalysisStatus;
import com.example.slowsqlagent.domain.SlowSqlRecord;
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
                .sorted(Comparator.comparing(SlowSqlRecord::getAvgLatencyMs).reversed())
                .toList();
    }

    public Optional<SlowSqlRecord> findById(String id) {
        return Optional.ofNullable(records.get(id));
    }

    public synchronized void save(SlowSqlRecord record) {
        records.merge(record.getId(), record, this::merge);
        persistSnapshot();
    }

    public synchronized void saveAll(List<SlowSqlRecord> newRecords) {
        newRecords.forEach(record -> records.merge(record.getId(), record, this::merge));
        persistSnapshot();
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
}

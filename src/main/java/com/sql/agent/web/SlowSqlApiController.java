package com.sql.agent.web;

import com.sql.agent.domain.SlowSqlRecord;
import com.sql.agent.service.SampleDataService;
import com.sql.agent.service.SlowSqlAnalysisService;
import com.sql.agent.service.SlowSqlCollectionService;
import com.sql.agent.service.SlowSqlRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SlowSqlApiController {

    private final SlowSqlRepository repository;
    private final SlowSqlCollectionService collectionService;
    private final SlowSqlAnalysisService analysisService;
    private final SampleDataService sampleDataService;

    public SlowSqlApiController(SlowSqlRepository repository,
                                SlowSqlCollectionService collectionService,
                                SlowSqlAnalysisService analysisService,
                                SampleDataService sampleDataService) {
        this.repository = repository;
        this.collectionService = collectionService;
        this.analysisService = analysisService;
        this.sampleDataService = sampleDataService;
    }

    @GetMapping("/slow-sql")
    public List<SlowSqlRecord> list() {
        return repository.findAll();
    }

    @PostMapping("/collect")
    public List<SlowSqlRecord> collect() {
        return collectionService.collect().records();
    }

    @PostMapping("/sample")
    public SlowSqlRecord sample() {
        return sampleDataService.loadSample();
    }

    @PostMapping("/slow-sql/{id}/analyze")
    public ResponseEntity<SlowSqlRecord> analyze(@PathVariable String id) {
        boolean started = analysisService.startAnalysis(id);
        return repository.findById(id)
                .map(record -> started ? ResponseEntity.accepted().body(record) : ResponseEntity.ok(record))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/slow-sql/{id}")
    public ResponseEntity<SlowSqlRecord> detail(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

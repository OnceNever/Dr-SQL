package com.example.slowsqlagent.web;

import com.example.slowsqlagent.domain.SlowSqlRecord;
import com.example.slowsqlagent.service.SampleDataService;
import com.example.slowsqlagent.service.SlowSqlAnalysisService;
import com.example.slowsqlagent.service.SlowSqlCollectionService;
import com.example.slowsqlagent.service.SlowSqlRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SlowSqlController {

    private final SlowSqlRepository repository;
    private final SlowSqlCollectionService collectionService;
    private final SlowSqlAnalysisService analysisService;
    private final SampleDataService sampleDataService;

    public SlowSqlController(SlowSqlRepository repository,
                             SlowSqlCollectionService collectionService,
                             SlowSqlAnalysisService analysisService,
                             SampleDataService sampleDataService) {
        this.repository = repository;
        this.collectionService = collectionService;
        this.analysisService = analysisService;
        this.sampleDataService = sampleDataService;
    }

    @GetMapping("/")
    public String index(Model model) {
        var records = repository.findAll();
        model.addAttribute("records", records);
        model.addAttribute("count", repository.count());
        model.addAttribute("hasAnalyzing", records.stream().anyMatch(SlowSqlRecord::isAnalysisInProgress));
        return "index";
    }

    @PostMapping("/collect")
    public String collect(RedirectAttributes redirectAttributes) {
        try {
            int count = collectionService.collect().size();
            redirectAttributes.addFlashAttribute("message", "采集完成，新增或更新 " + count + " 条慢 SQL。");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "采集失败：" + ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/sample")
    public String sample(RedirectAttributes redirectAttributes) {
        SlowSqlRecord record = sampleDataService.loadSample();
        redirectAttributes.addFlashAttribute("message", "已加载示例慢 SQL。");
        return "redirect:/slow-sql/" + record.getId();
    }

    @GetMapping("/slow-sql/{id}")
    public String detail(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        return repository.findById(id)
                .map(record -> {
                    model.addAttribute("record", record);
                    return "detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "慢 SQL 不存在：" + id);
                    return "redirect:/";
                });
    }

    @PostMapping("/slow-sql/{id}/analyze")
    public String analyze(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            boolean started = analysisService.startAnalysis(id);
            if (started) {
                redirectAttributes.addFlashAttribute("message", "已开始分析，稍后刷新页面查看结果。");
            } else {
                redirectAttributes.addFlashAttribute("message", "这条 SQL 正在分析中，请稍后查看结果。");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "启动分析失败：" + ex.getMessage());
        }
        return "redirect:/slow-sql/" + id;
    }
}

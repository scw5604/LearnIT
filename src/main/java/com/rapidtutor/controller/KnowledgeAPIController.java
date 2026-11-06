package com.rapidtutor.controller;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.repository.KnowledgeArticleRepository;
import com.rapidtutor.service.KnowledgeBankService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeAPIController {

    private final KnowledgeArticleRepository knowledgeRepository;

    public KnowledgeAPIController(KnowledgeArticleRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    @GetMapping("/search")
    public List<KnowledgeArticle> searchKnowledgeBank(@RequestParam String topic, @RequestParam String course) {
        String canonicalCourse = Courses.canonical(course);
        if (canonicalCourse == null || topic == null || topic.isBlank()) {
            return List.of();
        }
        String query = topic.trim();
        Optional<KnowledgeArticle> triage = knowledgeRepository
                .findFirstByCourseIgnoreCaseAndKindIgnoreCaseAndTopicContainingIgnoreCaseOrderByCreatedAtDesc(
                        canonicalCourse, KnowledgeBankService.TRIAGE, query);
        if (triage.isPresent()) {
            return List.of(triage.get());
        }
        Optional<KnowledgeArticle> seeded = knowledgeRepository
                .findFirstByCourseIgnoreCaseAndTopicContainingIgnoreCaseAndKindIsNullOrderByCreatedAtDesc(
                        canonicalCourse, query);
        return seeded.map(List::of).orElseGet(List::of);
    }
}

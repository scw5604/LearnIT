package com.rapidtutor.controller;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.repository.KnowledgeArticleRepository;
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
        Optional<KnowledgeArticle> article = knowledgeRepository
                .findFirstByCourseIgnoreCaseAndTopicContainingIgnoreCase(canonicalCourse, topic.trim());
        return article.map(List::of).orElseGet(List::of);
    }
}

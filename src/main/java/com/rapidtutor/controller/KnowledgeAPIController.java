package com.rapidtutor.controller;

import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.repository.KnowledgeArticleRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeAPIController {

    private final KnowledgeArticleRepository knowledgeRepository;

    public KnowledgeAPIController(KnowledgeArticleRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    @GetMapping("/search")
    public List<KnowledgeArticle> searchKnowledgeBank(@RequestParam String topic) {
        return knowledgeRepository.findByCourseIgnoreCaseAndTopicContainingIgnoreCase("HCDD 412", topic);
    }
}

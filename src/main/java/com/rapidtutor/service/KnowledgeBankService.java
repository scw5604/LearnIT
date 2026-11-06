package com.rapidtutor.service;

import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.repository.KnowledgeArticleRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class KnowledgeBankService {

    public static final String TRIAGE = "triage";

    private static final int TEXT_LIMIT = 2000;

    private final KnowledgeArticleRepository knowledgeArticleRepository;

    public KnowledgeBankService(KnowledgeArticleRepository knowledgeArticleRepository) {
        this.knowledgeArticleRepository = knowledgeArticleRepository;
    }

    /** Stores the course, topic, and diagnostic note. The student's own writing is not copied here. */
    public KnowledgeArticle saveTriage(String course, String topic, String note) {
        String safeTopic = clip(topic, 240);
        String safeNote = clip(note, TEXT_LIMIT);
        Optional<KnowledgeArticle> existing = knowledgeArticleRepository
                .findFirstByCourseIgnoreCaseAndTopicIgnoreCaseAndKindIgnoreCaseOrderByCreatedAtDesc(
                        course, safeTopic, TRIAGE);
        if (existing.isPresent()) {
            KnowledgeArticle article = existing.get();
            article.setVerifiedSolution(safeNote);
            return knowledgeArticleRepository.save(article);
        }
        KnowledgeArticle article = new KnowledgeArticle(course, safeTopic, safeTopic, safeNote);
        article.setKind(TRIAGE);
        return knowledgeArticleRepository.save(article);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }
}

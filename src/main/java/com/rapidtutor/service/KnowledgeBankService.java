package com.rapidtutor.service;

import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.repository.KnowledgeArticleRepository;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBankService {

    public static final String TRIAGE = "triage";

    private static final int TEXT_LIMIT = 2000;

    private final KnowledgeArticleRepository knowledgeArticleRepository;

    public KnowledgeBankService(KnowledgeArticleRepository knowledgeArticleRepository) {
        this.knowledgeArticleRepository = knowledgeArticleRepository;
    }

    /**
     * Stores the course, topic, and diagnostic note. The student's own writing is not copied here.
     * Returns false when nothing was stored: the note is the offline text, or that course and topic
     * already have a note, which is kept so one student cannot replace what others read.
     */
    public boolean saveTriage(String course, String topic, String note) {
        if (note == null || note.isBlank() || SocraticNotes.isOffline(note)) {
            return false;
        }
        String safeTopic = clip(topic, 240);
        boolean exists = knowledgeArticleRepository
                .findFirstByCourseIgnoreCaseAndTopicIgnoreCaseAndKindIgnoreCaseOrderByCreatedAtDesc(
                        course, safeTopic, TRIAGE)
                .isPresent();
        if (exists) {
            return false;
        }
        KnowledgeArticle article = new KnowledgeArticle(course, safeTopic, safeTopic, clip(note, TEXT_LIMIT));
        article.setKind(TRIAGE);
        knowledgeArticleRepository.save(article);
        return true;
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

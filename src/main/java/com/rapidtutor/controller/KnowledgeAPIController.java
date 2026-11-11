package com.rapidtutor.controller;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.repository.KnowledgeArticleRepository;
import com.rapidtutor.service.DiagnosticNoteChecker;
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
    public List<ArticleMatch> searchKnowledgeBank(@RequestParam String topic, @RequestParam String course) {
        String canonicalCourse = Courses.canonical(course);
        if (canonicalCourse == null || topic == null || topic.isBlank()) {
            return List.of();
        }
        String query = topic.trim();
        Optional<KnowledgeArticle> match = knowledgeRepository
                .findFirstByCourseIgnoreCaseAndKindIgnoreCaseAndTopicContainingIgnoreCaseOrderByCreatedAtDesc(
                        canonicalCourse, KnowledgeBankService.TRIAGE, query)
                .or(() -> knowledgeRepository
                        .findFirstByCourseIgnoreCaseAndTopicContainingIgnoreCaseAndKindIsNullOrderByCreatedAtDesc(
                                canonicalCourse, query));
        return match.map(article -> List.of(ArticleMatch.of(article))).orElseGet(List::of);
    }

    /** Sections are empty when the note does not have all three labeled parts. */
    public record ArticleMatch(Long id, String kind, String topic, String problemDescription, String noteText,
                               List<DiagnosticNoteChecker.Section> sections) {

        static ArticleMatch of(KnowledgeArticle article) {
            return new ArticleMatch(article.getId(), article.getKind(), article.getTopic(),
                    article.getProblemDescription(), article.getNoteText(),
                    DiagnosticNoteChecker.sections(article.getNoteText()));
        }
    }
}

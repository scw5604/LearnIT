package com.rapidtutor.repository;

import com.rapidtutor.model.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {
    List<KnowledgeArticle> findByCourseIgnoreCaseAndTopicContainingIgnoreCase(String course, String topic);
}

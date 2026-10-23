package com.rapidtutor.repository;

import com.rapidtutor.model.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {
    List<KnowledgeArticle> findByCourseIgnoreCaseAndTopicContainingIgnoreCase(String course, String topic);

    List<KnowledgeArticle> findByCourseIgnoreCaseAndTopicContainingIgnoreCaseOrderByTopicAsc(String course, String topic);

    List<KnowledgeArticle> findByCourseIgnoreCaseOrderByTopicAsc(String course);

    List<KnowledgeArticle> findByTopicContainingIgnoreCaseOrderByCourseAscTopicAsc(String topic);

    List<KnowledgeArticle> findAllByOrderByCourseAscTopicAsc();

    @Modifying
    @Transactional
    @Query("delete from KnowledgeArticle article where article.kind = :kind")
    void deleteByKind(String kind);

    long countByCourse(String course);

    long countByCourseAndTopic(String course, String topic);
}

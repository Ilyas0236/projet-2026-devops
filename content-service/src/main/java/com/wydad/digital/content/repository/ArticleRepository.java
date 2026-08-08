package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.Article;
import com.wydad.digital.content.model.SportSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findBySport(SportSection sport);
    List<Article> findByPublishedTrue();
    List<Article> findBySportAndPublishedTrue(SportSection sport);
}
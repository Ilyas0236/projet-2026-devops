package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    Optional<Category> findByNameIgnoreCase(String name);
}
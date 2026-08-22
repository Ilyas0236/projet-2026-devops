package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.Product;
import com.wydad.digital.shop.enums.SportSection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    Page<Product> findBySportSectionAndActiveTrue(SportSection sportSection, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    List<Product> findByCustomizableTrueAndActiveTrue();

    List<Product> findByActiveTrueAndCategoryId(Long categoryId);
}
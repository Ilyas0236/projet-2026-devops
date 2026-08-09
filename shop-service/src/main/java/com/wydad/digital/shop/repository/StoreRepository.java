package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    List<Store> findByActiveTrue();
    Optional<Store> findByNameIgnoreCase(String name);
}
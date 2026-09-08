package com.foodhub.springaitest.repository;

import com.foodhub.springaitest.entity.TaxArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxArticleRepository extends JpaRepository<TaxArticle, Long> {

    List<TaxArticle> findByActiveTrueOrderByOrderIndexAsc();

    List<TaxArticle> findBySyncedToVectorStoreFalseAndActiveTrue();

    long countByActiveTrue();
}
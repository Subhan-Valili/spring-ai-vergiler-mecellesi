package com.foodhub.springaitest.repository;

import com.foodhub.springaitest.entity.TaxSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaxSectionRepository extends JpaRepository<TaxSection, Long> {

    @Query("SELECT ts FROM TaxSection ts JOIN FETCH ts.article a " +
            "WHERE ts.syncedToVectorStore = false AND a.active = true")
    List<TaxSection> findBySyncedToVectorStoreFalseAndArticle_ActiveTrue();
}

package com.polybezev.currencybot.repository;

import com.polybezev.currencybot.entity.NewsAnalysisCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link NewsAnalysisCache} entities.
 */
@Repository
public interface NewsAnalysisCacheRepository extends JpaRepository<NewsAnalysisCache, Long> {

    /**
     * Finds the cached translation+summary for a news item by its guid.
     *
     * @param guid RSS guid (or link fallback) of the news item
     * @return the cache entry, or empty if this item has not been analysed yet
     */
    Optional<NewsAnalysisCache> findByGuid(String guid);
}

package com.foodhub.springaitest.service;

import com.foodhub.springaitest.entity.TaxSection;
import com.foodhub.springaitest.repository.TaxSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSyncService {

    private final TaxSectionRepository sectionRepository;
    private final VectorStore vectorStore;

    private static final int BATCH_SIZE = 100;

    @Transactional
    public int syncUnsyncedSections() {
        List<TaxSection> pending = sectionRepository.findBySyncedToVectorStoreFalseAndArticle_ActiveTrue();
        log.info("{} section Qdrant-a sync ediləcək", pending.size());

        List<Document> batch = new ArrayList<>();
        for (TaxSection section : pending) {
            Document doc = buildDocument(section);
            section.setVectorId(doc.getId());
            batch.add(doc);

            if (batch.size() == BATCH_SIZE) {
                vectorStore.add(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            vectorStore.add(batch);
        }

        pending.forEach(section -> section.setSyncedToVectorStore(true));
        sectionRepository.saveAll(pending);

        log.info("Sync tamamlandı: {} section.", pending.size());
        return pending.size();
    }

    @Transactional
    public void markForResync(Long sectionId) {
        sectionRepository.findById(sectionId).ifPresent(section -> {
            if (section.getVectorId() != null) {
                vectorStore.delete(List.of(section.getVectorId()));
            }
            section.setSyncedToVectorStore(false);
            section.setVectorId(null);
            sectionRepository.save(section);
        });
    }

    private Document buildDocument(TaxSection section) {
        var article = section.getArticle();

        // Alt-bəndlər üçün valideyn (parent) bəndin başlığını tapırıq
        String parentContext = getParentSectionContent(section);

        String fullContent = parentContext.isBlank()
                ? section.getContent()
                : parentContext + " -> " + section.getContent();

        String enriched = String.format(
                "Sənəd: Azərbaycan Respublikasının Vergi Məcəlləsi\nFəsil: %s\nMaddə %s. %s\nBənd %s: %s",
                article.getChapter(), article.getArticleNumber(), article.getTitle(),
                section.getSectionNumber(), fullContent
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("article", article.getArticleNumber());
        metadata.put("article_title", article.getTitle());
        metadata.put("section", section.getSectionNumber());
        metadata.put("chapter", article.getChapter());
        metadata.put("db_article_id", article.getId().toString());
        metadata.put("db_section_id", section.getId().toString());

        String seed = "db_section_" + section.getId();
        String deterministicId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();

        return new Document(deterministicId, enriched, metadata);
    }

    private String getParentSectionContent(TaxSection section) {
        String secNum = section.getSectionNumber();
        int lastDot = secNum.lastIndexOf('.');
        if (lastDot > 0) {
            String parentSecNum = secNum.substring(0, lastDot);
            return section.getArticle().getSections().stream()
                    .filter(s -> s.getSectionNumber().equals(parentSecNum))
                    .findFirst()
                    .map(TaxSection::getContent)
                    .orElse("");
        }
        return "";
    }
}
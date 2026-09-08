package com.foodhub.springaitest.service;

import com.foodhub.springaitest.entity.TaxArticle;
import com.foodhub.springaitest.repository.TaxArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSyncService {

    private final TaxArticleRepository articleRepository;
    private final VectorStore vectorStore;

    private static final int BATCH_SIZE = 100;

    // DB-də yoxlanmış maddələri Qdrant-a köçürəndə uzun maddələri bölmək üçün
    private final TokenTextSplitter subSplitter = new TokenTextSplitter(800, 150, 5, 10000, true);

    @Transactional
    public int syncUnsyncedArticles() {
        List<TaxArticle> pending = articleRepository.findBySyncedToVectorStoreFalseAndActiveTrue();
        log.info("{} maddə Qdrant-a sync ediləcək", pending.size());

        for (TaxArticle article : pending) {
            List<Document> docs = toDocuments(article);
            for (int i = 0; i < docs.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, docs.size());
                vectorStore.add(docs.subList(i, end));
            }
            article.setSyncedToVectorStore(true);
        }

        articleRepository.saveAll(pending);
        log.info("Sync tamamlandı: {} maddə.", pending.size());
        return pending.size();
    }

    /**
     * Bir maddəni redaktə edəndən sonra çağır: köhnə vektoru silib yenidən sync üçün işarələyir.
     */
    @Transactional
    public void markForResync(Long articleId) {
        articleRepository.findById(articleId).ifPresent(article -> {
            // Köhnə vektor(lar)ı sil ki, dublikat qalmasın
            List<String> oldIds = deterministicIdsFor(article);
            vectorStore.delete(oldIds);

            article.setSyncedToVectorStore(false);
            articleRepository.save(article);
        });
    }

    private List<Document> toDocuments(TaxArticle article) {
        String text = article.getContent();

        if (text.length() <= 1500) {
            return List.of(buildDocument(article, text, 0, 1));
        }

        Document tempDoc = new Document(text);
        List<Document> subDocs = subSplitter.apply(List.of(tempDoc));

        return java.util.stream.IntStream.range(0, subDocs.size())
                .mapToObj(idx -> buildDocument(article, subDocs.get(idx).getText(), idx, subDocs.size()))
                .toList();
    }

    private Document buildDocument(TaxArticle article, String contentPart, int subIdx, int subTotal) {
        String enriched;
        if (subTotal > 1) {
            enriched = String.format(
                    "Sənəd: Azərbaycan Respublikasının Vergi Məcəlləsi\nFəsil: %s\n%s (Hissə %d/%d)\n%s",
                    article.getChapter(), article.getArticleNumber(), subIdx + 1, subTotal, contentPart
            );
        } else {
            enriched = String.format(
                    "Sənəd: Azərbaycan Respublikasının Vergi Məcəlləsi\nFəsil: %s\n%s",
                    article.getChapter(), contentPart
            );
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", article.getSourceTitle());
        metadata.put("article", article.getArticleNumber());
        metadata.put("chapter", article.getChapter());
        metadata.put("db_id", article.getId().toString());
        metadata.put("chunk_index", subIdx);

        String seed = "db_article_" + article.getId() + "_chunk_" + subIdx;
        String deterministicId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();

        return new Document(deterministicId, enriched, metadata);
    }

    private List<String> deterministicIdsFor(TaxArticle article) {
        // Sadəlik üçün ilk 10 mümkün sub-chunk id-sini silməyə çalışırıq (əksəriyyəti 1-3 chunk-dır)
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(idx -> {
                    String seed = "db_article_" + article.getId() + "_chunk_" + idx;
                    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
                })
                .toList();
    }
}
package com.foodhub.springaitest.service;

import com.foodhub.springaitest.entity.TaxArticle;
import com.foodhub.springaitest.repository.TaxArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final VectorStore vectorStore;
    private final TaxArticleRepository taxArticleRepository;
    private static final int BATCH_SIZE = 100;

    /**
     * YENİ AXIN: PDF-i birbaşa Qdrant-a yox, əvvəlcə Postgres-ə (tax_articles) yazır.
     * Bura yazıldıqdan sonra maddələri DB-də manual yoxlayıb/düzəldib,
     * sonra ayrıca bir sync addımı ilə Qdrant-a köçürmək mümkündür.
     */
    @Async
    @Transactional
    public void ingestPdfToDb(byte[] pdfBytes, String filename) {
        log.info("PDF -> DB emalı başlandı: {}", filename);

        Resource pdfResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
        List<Document> rawPages = pdfReader.get();

        StringBuilder fullTextBuilder = new StringBuilder();
        for (Document page : rawPages) {
            fullTextBuilder.append(page.getText()).append("\n");
        }

        String cleanedText = cleanText(fullTextBuilder.toString());

        List<TaxArticle> articles = splitIntoArticleEntities(cleanedText, filename);
        taxArticleRepository.saveAll(articles);

        log.info("PDF -> DB emalı tamamlandı: {} ({} maddə yazıldı, cədvəl: tax_articles)", filename, articles.size());
        log.info("İndi DB-dəki maddələri yoxlayıb düzəldə bilərsən, sonra sync ilə Qdrant-a köçür.");
    }

    @Async
    public void ingestText(String rawText, String docTitle) {
        log.info("Mətn emalı başlandı: {}", docTitle);

        String cleanedText = cleanText(rawText);
        List<Document> chunks = splitIntoArticleChunks(cleanedText, docTitle);
        List<Document> processedChunks = prepareChunksWithDeterministicIds(chunks, docTitle);

        saveInBatches(processedChunks);
        log.info("Mətn emalı uğurla başa çatdı: {}", docTitle);
    }

    @Async
    public void ingestPdf(byte[] pdfBytes, String filename) {
        log.info("PDF emalı arxa fonda başlandı: {}", filename);

        Resource pdfResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
        List<Document> rawPages = pdfReader.get();

        // 1. Bütün PDF səhifələrinin mətnini vahid mətnə yığırıq
        StringBuilder fullTextBuilder = new StringBuilder();
        for (Document page : rawPages) {
            fullTextBuilder.append(page.getText()).append("\n");
        }

        // 2. Mətni haşiyələrdən (footnotes) və dağınıq boşluqlardan təmizləyirik
        String cleanedText = cleanText(fullTextBuilder.toString());

        // 3. Mətni maddə-maddə bölürük və kontekstlə zənginləşdiririk
        List<Document> chunks = splitIntoArticleChunks(cleanedText, filename);
        List<Document> processedChunks = prepareChunksWithDeterministicIds(chunks, filename);

        // 4. Qdrant bazasına paket şəklində yazırıq
        saveInBatches(processedChunks);
        log.info("PDF emalı və Qdrant-a yazılması uğurla tamamlandı: {}", filename);
    }

    public void deleteByIds(List<String> docIds) {
        vectorStore.delete(docIds);
    }

    /**
     * PDF-dəki səhifə altı qanun dəyişikliyi haşiyələrini və lazımsız tab/boşluqları təmizləyir.
     */
    private String cleanText(String text) {
        if (text == null) return "";

        // 1) Sadə haşiyə/tab təmizliyi (əvvəlki kimi qalır)
        text = text.replaceAll("[ \\t]+", " ");

        // 2) Paraqraflara böl (boş sətirlərlə ayrılmış bloklar)
        String[] paragraphs = text.split("\\n\\s*\\n");

        Pattern footnoteSignature = Pattern.compile(
                "(?i)(qəzeti|qanunvericilik toplusu|əvəz edilmişdir|əlavə edilmişdir|" +
                        "çıxarılmışdır|ləğv edilmişdir|yeni redaksiyada verilmişdir|" +
                        "hesab edilmişdir|IIQD nömrəli|IIIQD nömrəli|VIQD nömrəli)"
        );
        Pattern oldRedactionMarker = Pattern.compile("(?i)(ə)?vvəlki redaksiya");

        StringBuilder result = new StringBuilder();
        boolean skipNext = false;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (skipNext) {
                // "Əvvəlki redaksiyada deyilirdi:" -dən sonra gələn köhnə mətn
                skipNext = false;
                continue;
            }

            if (oldRedactionMarker.matcher(trimmed).find()) {
                skipNext = true; // növbəti paraqraf da köhnə redaksiya mətnidir, onu da at
                continue;
            }

            if (footnoteSignature.matcher(trimmed).find()) {
                continue; // dəyişiklik aktı istinadı - real maddə mətni deyil
            }

            result.append(trimmed).append("\n\n");
        }

        return result.toString()
                .replaceAll("(?m)^\\s*$[\n\r]*", "\n")
                .trim();
    }

    /**
     * Vergi Məcəlləsini kor-koranə simvol sayına görə yox, Maddə-Maddə bölür.
     */
    private List<Document> splitIntoArticleChunks(String text, String sourceTitle) {
        List<Document> chunks = new ArrayList<>();
        String[] articles = text.split("(?=\\bMaddə\\s+\\d+)");

        String currentChapter = "Ümumi Müddəalar";
        Pattern chapterPattern = Pattern.compile("(?i)(IX|IV|V?I{0,3})\\s+fəsil\\.\\s*([^\\n]+)");

        // İri maddələri bölmək üçün ikinci dərəcəli splitter (maks 1000 simvol)
        TokenTextSplitter subSplitter = new TokenTextSplitter(800, 150, 5, 10000, true);

        for (String articleRaw : articles) {
            String articleText = articleRaw.trim();
            if (articleText.isEmpty()) continue;

            Matcher chapterMatcher = chapterPattern.matcher(articleText);
            if (chapterMatcher.find()) {
                currentChapter = chapterMatcher.group(0).trim();
            }

            String articleNumber = extractArticleNumber(articleText);

            // Əgər maddə çox uzundursa, onu alt-parçalara (sub-chunks) bölürük
            if (articleText.length() > 1500) {
                Document tempDoc = new Document(articleText);
                List<Document> subDocs = subSplitter.apply(List.of(tempDoc));

                for (int subIdx = 0; subIdx < subDocs.size(); subIdx++) {
                    String enrichedContent = String.format(
                            "Sənəd: Azərbaycan Respublikasının Vergi Məcəlləsi\n" +
                                    "Fəsil: %s\n" +
                                    "Maddə: %s (Hissə %d/%d)\n" +
                                    "%s",
                            currentChapter, articleNumber, (subIdx + 1), subDocs.size(), subDocs.get(subIdx).getText()
                    );

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", sourceTitle);
                    metadata.put("article", articleNumber);
                    metadata.put("chapter", currentChapter);

                    chunks.add(new Document(enrichedContent, metadata));
                }
            } else {
                // Qısa maddələri olduğu kimi saxlayırıq
                String enrichedContent = String.format(
                        "Sənəd: Azərbaycan Respublikasının Vergi Məcəlləsi\n" +
                                "Fəsil: %s\n" +
                                "%s",
                        currentChapter, articleText
                );

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", sourceTitle);
                metadata.put("article", articleNumber);
                metadata.put("chapter", currentChapter);

                chunks.add(new Document(enrichedContent, metadata));
            }
        }

        return chunks;
    }

    /**
     * DB-yə yazmaq üçün: hər maddəni AYRICA, TAM mətn kimi (alt-parçalara bölmədən) saxlayır.
     * Beləliklə DB-də bir sətir = bir maddə, manual yoxlamaq/düzəltmək asan olur.
     * Uzun maddələr üçün alt-bölmə yalnız Qdrant-a sync zamanı edilə bilər.
     */
    private List<TaxArticle> splitIntoArticleEntities(String text, String sourceTitle) {
        List<TaxArticle> articles = new ArrayList<>();
        String[] rawArticles = text.split("(?=\\bMaddə\\s+\\d+)");

        String currentChapter = "Ümumi Müddəalar";
        Pattern chapterPattern = Pattern.compile("(?i)(IX|IV|V?I{0,3})\\s+fəsil\\.\\s*([^\\n]+)");

        int orderIndex = 0;
        for (String articleRaw : rawArticles) {
            String articleText = articleRaw.trim();
            if (articleText.isEmpty()) continue;

            Matcher chapterMatcher = chapterPattern.matcher(articleText);
            if (chapterMatcher.find()) {
                currentChapter = chapterMatcher.group(0).trim();
            }

            String articleNumber = extractArticleNumber(articleText);

            TaxArticle article = new TaxArticle();
            article.setArticleNumber(articleNumber);
            article.setChapter(currentChapter);
            article.setSourceTitle(sourceTitle);
            article.setOrderIndex(orderIndex++);
            article.setContent(articleText);
            article.setActive(true);
            article.setSyncedToVectorStore(false);

            articles.add(article);
        }

        return articles;
    }

    private String extractArticleNumber(String text) {
        Matcher matcher = Pattern.compile("\\bMaddə\\s+\\d+").matcher(text);
        return matcher.find() ? matcher.group() : "Göstərilməyib";
    }

    private void saveInBatches(List<Document> documents) {
        int total = documents.size();
        log.info("Ümumi {} chunk emal olunacaq (Paket ölçüsü: {}).", total, BATCH_SIZE);

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            log.info("Paket yazıldı: {}-{} / {}", i + 1, end, total);
        }
    }

    private List<Document> prepareChunksWithDeterministicIds(List<Document> chunks, String sourceKey) {
        List<Document> prepared = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);

            String uniqueSeed = sourceKey + "_chunk_" + i;
            String deterministicId = UUID.nameUUIDFromBytes(uniqueSeed.getBytes(StandardCharsets.UTF_8)).toString();

            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("source", sourceKey);
            metadata.put("chunk_index", i);

            Document updatedDoc = new Document(deterministicId, chunk.getText(), metadata);
            prepared.add(updatedDoc);
        }

        return prepared;
    }
}
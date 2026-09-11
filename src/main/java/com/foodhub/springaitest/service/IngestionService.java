package com.foodhub.springaitest.service;

import com.foodhub.springaitest.entity.TaxArticle;
import com.foodhub.springaitest.entity.TaxSection;
import com.foodhub.springaitest.repository.TaxArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final VectorStore vectorStore;
    private final TaxArticleRepository taxArticleRepository;

    private static final Pattern CLAUSE_START = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)*)\\.\\s");
    private static final Pattern ARTICLE_NUMBER = Pattern.compile("\\bMaddə\\s+(\\d+)");
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("(?i)(IX|IV|V?I{0,3})\\s+fəsil\\.\\s*([^\\n]+)");

//    @Async
//    @Transactional
//    public void ingestPdfToDb(byte[] pdfBytes, String filename) {
//        log.info("PDF -> DB emalı başlandı: {}", filename);
//
//        Resource pdfResource = new ByteArrayResource(pdfBytes) {
//            @Override
//            public String getFilename() {
//                return filename;
//            }
//        };
//
//        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
//        List<Document> rawPages = pdfReader.get();
//
//        StringBuilder fullTextBuilder = new StringBuilder();
//        for (Document page : rawPages) {
//            fullTextBuilder.append(page.getText()).append("\n");
//        }
//
//        String cleanedText = cleanText(fullTextBuilder.toString());
//
//        List<TaxArticle> articles = splitIntoArticleEntities(cleanedText);
//        taxArticleRepository.saveAll(articles);
//
//        int totalSections = articles.stream().mapToInt(a -> a.getSections().size()).sum();
//        log.info("PDF -> DB emalı tamamlandı: {} ({} maddə, {} bənd yazıldı)", filename, articles.size(), totalSections);
//        log.info("İndi DB-dəki maddələri/bəndləri yoxlayıb düzəldə bilərsən, sonra VectorSyncService ilə Qdrant-a köçür.");
//    }
//
//    public void deleteByIds(List<String> docIds) {
//        vectorStore.delete(docIds);
//    }

//    private String cleanText(String text) {
//        if (text == null) return "";
//
//        text = text.replaceAll("[ \\t]+", " ");
//
//        String[] paragraphs = text.split("\\n\\s*\\n");
//
//        Pattern footnoteSignature = Pattern.compile(
//                "(?i)(qəzeti|qanunvericilik toplusu|əvəz edilmişdir|əlavə edilmişdir|" +
//                        "çıxarılmışdır|ləğv edilmişdir|yeni redaksiyada verilmişdir|" +
//                        "hesab edilmişdir|IIQD nömrəli|IIIQD nömrəli|VIQD nömrəli)"
//        );
//        Pattern oldRedactionMarker = Pattern.compile("(?i)(ə)?vvəlki redaksiya");
//
//        StringBuilder result = new StringBuilder();
//        boolean skipNext = false;
//
//        for (String para : paragraphs) {
//            String trimmed = para.trim();
//            if (trimmed.isEmpty()) continue;
//
//            if (skipNext) {
//                skipNext = false;
//                continue;
//            }
//
//            if (oldRedactionMarker.matcher(trimmed).find()) {
//                skipNext = true;
//                continue;
//            }
//
//            if (footnoteSignature.matcher(trimmed).find()) {
//                continue;
//            }
//
//            result.append(trimmed).append("\n\n");
//        }
//
//        return result.toString()
//                .replaceAll("(?m)^\\s*$[\n\r]*", "\n")
//                .trim();
//    }

//    private List<TaxArticle> splitIntoArticleEntities(String text) {
//        List<TaxArticle> articles = new ArrayList<>();
//        String[] rawArticles = text.split("(?=\\bMaddə\\s+\\d+)");
//
//        String currentChapter = "Ümumi Müddəalar";
//
//        for (String articleRaw : rawArticles) {
//            String articleText = articleRaw.trim();
//            if (articleText.isEmpty()) continue;
//
//            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(articleText);
//            if (chapterMatcher.find()) {
//                currentChapter = chapterMatcher.group(0).trim();
//            }
//
//            Matcher articleNumMatcher = ARTICLE_NUMBER.matcher(articleText);
//            if (!articleNumMatcher.find()) {
//                continue;
//            }
//            String articleNumberFull = articleNumMatcher.group();   // "Maddə 177" - basliqdan title ayirmaq ucun
//            String articleNumber = articleNumMatcher.group(1);      // "177" - DB-de bu formada saxlanir
//
//            String[] paragraphs = articleText.split("\\n\\n+");
//            String firstParagraph = paragraphs[0].trim();
//            String title = extractTitleAfterArticleNumber(firstParagraph, articleNumberFull);
//
//            TaxArticle article = new TaxArticle();
//            article.setArticleNumber(articleNumber);
//            article.setTitle(title);
//            article.setChapter(currentChapter);
//            article.setActive(true);
//
//            List<TaxSection> sections = buildSectionsFromClauses(article, paragraphs, articleNumber, title);
//            article.setSections(sections);
//
//            articles.add(article);
//        }
//
//        return articles;
//    }

//    private String extractTitleAfterArticleNumber(String firstParagraph, String articleNumberRaw) {
//        int idx = firstParagraph.indexOf(articleNumberRaw);
//        if (idx < 0) return firstParagraph;
//        String rest = firstParagraph.substring(idx + articleNumberRaw.length()).trim();
//        if (rest.startsWith(".")) rest = rest.substring(1).trim();
//        return rest;
//    }
//
//    private List<TaxSection> buildSectionsFromClauses(TaxArticle article, String[] paragraphs,
//                                                      String articleNumber, String title) {
//        List<TaxSection> sections = new ArrayList<>();
//        int fallbackCounter = 0;
//        TaxSection current = null;
//
//        for (int i = 1; i < paragraphs.length; i++) {
//            String para = paragraphs[i].trim();
//            if (para.isEmpty()) continue;
//
//            Matcher clauseMatcher = CLAUSE_START.matcher(para);
//            if (clauseMatcher.find()) {
//                String sectionNumber = clauseMatcher.group(1);           // "177.2"
//                String contentWithoutPrefix = para.substring(clauseMatcher.end()).trim(); // prefiks (nomre+nöqte) atilir
//
//                current = TaxSection.builder()
//                        .sectionNumber(sectionNumber)
//                        .content(contentWithoutPrefix)
//                        .orderIndex(resolveOrderIndex(sectionNumber, fallbackCounter++))
//                        .syncedToVectorStore(false)
//                        .article(article)
//                        .build();
//                sections.add(current);
//            } else if (current != null) {
//                current.setContent(current.getContent() + "\n\n" + para);
//            } else {
//                current = TaxSection.builder()
//                        .sectionNumber(articleNumber)
//                        .content(para)
//                        .orderIndex(fallbackCounter++)
//                        .syncedToVectorStore(false)
//                        .article(article)
//                        .build();
//                sections.add(current);
//            }
//        }
//
//        if (sections.isEmpty()) {
//            sections.add(TaxSection.builder()
//                    .sectionNumber(articleNumber)
//                    .content(title != null && !title.isBlank() ? title : articleNumber)
//                    .orderIndex(0)
//                    .syncedToVectorStore(false)
//                    .article(article)
//                    .build());
//        }
//
//        return sections;
//    }
//
//    private int resolveOrderIndex(String sectionNumber, int fallback) {
//        String[] parts = sectionNumber.split("\\.");
//        if (parts.length >= 2) {
//            try {
//                return Integer.parseInt(parts[1]);
//            } catch (NumberFormatException ignored) {
//
//            }
//        }
//        return fallback;
//    }
}
package com.foodhub.springaitest.controller;

import com.foodhub.springaitest.service.IngestionService;
import com.foodhub.springaitest.service.TaxRagService;
import com.foodhub.springaitest.service.VectorSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxController {

    private final IngestionService ingestionService;
    private final TaxRagService taxRagService;
    private final VectorSyncService vectorSyncService;

    @PostMapping("/ingest-text")
    public ResponseEntity<String> ingestText(@RequestBody String rawText, @RequestParam String docTitle) {
        ingestionService.ingestText(rawText, docTitle);
        return ResponseEntity.accepted().body("Mətnin emalı arxa fonda başladıldı.");
    }

    @PostMapping("/upload-pdf")
    public ResponseEntity<String> uploadPdf(@RequestParam("file") MultipartFile file) throws IOException, IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Fayl boş ola bilməz.");
        }

        byte[] bytes = file.getBytes(); // request thread-ində, temp fayl silinmədən oxunur
        String filename = file.getOriginalFilename();

        ingestionService.ingestPdf(bytes, filename);
        return ResponseEntity.accepted().body("PDF emalı arxa fonda başladıldı. Tamamlandıqda verilənlər Qdrant-da görünəcək.");
    }

    /**
     * YENİ: PDF-i birbaşa Qdrant-a yox, əvvəlcə Postgres-ə (tax_articles cədvəli) yazır.
     * Bundan sonra maddələri DB-də (DBeaver/pgAdmin ilə) yoxlayıb düzəldə bilərsən,
     * daha sonra ayrıca sync endpoint-i ilə Qdrant-a köçürərsən.
     */
    @PostMapping("/upload-pdf-to-db")
    public ResponseEntity<String> uploadPdfToDb(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Fayl boş ola bilməz.");
        }

        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename();

        ingestionService.ingestPdfToDb(bytes, filename);
        return ResponseEntity.accepted().body("PDF emalı arxa fonda başladıldı. Tamamlandıqda maddələr tax_articles cədvəlində görünəcək. Yoxlayıb düzəltdikdən sonra sync endpoint-ini çağır.");
    }

    /**
     * DB-də (tax_articles) yoxlanmış/düzəldilmiş maddələri Qdrant-a köçürür.
     * Bunu PDF-i DB-yə yazıb, məlumatı əl ilə yoxlayıb düzəltdikdən SONRA çağır.
     */
    @PostMapping("/sync-to-vector-store")
    public ResponseEntity<String> syncToVectorStore() {
        int count = vectorSyncService.syncUnsyncedArticles();
        return ResponseEntity.ok(count + " maddə uğurla Qdrant-a sync edildi.");
    }

    @GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askQuestion(@RequestBody String question) {
        return taxRagService.askTaxQuestionStream(question);
    }

    @GetMapping("/ask-simple")
    public String askSimple(@RequestBody String question) {
        return taxRagService.askTaxQuestion(question);
    }

}
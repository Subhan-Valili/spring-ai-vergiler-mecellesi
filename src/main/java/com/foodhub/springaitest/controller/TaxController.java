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



//    @PostMapping("/upload-pdf-to-db")
//    public ResponseEntity<String> uploadPdfToDb(@RequestParam("file") MultipartFile file) throws IOException {
//        if (file.isEmpty()) {
//            return ResponseEntity.badRequest().body("Fayl boş ola bilməz.");
//        }
//
//        byte[] bytes = file.getBytes();
//        String filename = file.getOriginalFilename();
//
//        ingestionService.ingestPdfToDb(bytes, filename);
//        return ResponseEntity.accepted().body("PDF emalı arxa fonda başladıldı. Tamamlandıqda maddələr/bəndlər tax_articles və tax_sections cədvəllərində görünəcək. Yoxlayıb düzəltdikdən sonra /sync-to-vector-store endpoint-ini çağır.");
//    }

    @PostMapping("/sync-to-vector-store")
    public ResponseEntity<String> syncToVectorStore() {
        int count = vectorSyncService.syncUnsyncedSections();
        return ResponseEntity.ok(count + " bənd uğurla Qdrant-a sync edildi.");
    }

//    @GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> askQuestion(@RequestParam String question) {
//        return taxRagService.askTaxQuestionStream(question);
//    }

    @GetMapping("/ask-simple")
    public String askSimple(@RequestBody String question) {
        return taxRagService.askTaxQuestion(question);
    }

}
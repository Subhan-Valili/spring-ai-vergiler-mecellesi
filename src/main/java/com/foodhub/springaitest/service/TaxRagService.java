package com.foodhub.springaitest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaxRagService {

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    public TaxRagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, ChatModel chatModel) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    public Flux<String> askTaxQuestionStream(String question) {
        List<Document> similarDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .similarityThreshold(0.38)  // müvəqqəti - bütün nəticələri gör
                        .build()
        );

// DEBUG
        similarDocs.forEach(doc ->
                log.info("SCORE: {} | CHUNK:\n{}\n---", doc.getMetadata().get("distance"), doc.getText())
        );

        if (similarDocs.isEmpty()) {
            return Flux.just("Təqdim olunan vergi qanunvericiliyi bazasında bu suala uyğun məlumat tapılmadı.");
        }

        String context = similarDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = """
            Sən Azərbaycan Respublikasının Vergi Məcəlləsi üzrə peşəkar hüquqi assistentisən.

            QAYDALAR:
            1. YALNIZ aşağıdakı vergi qanunvericiliyi kontekstinə əsasən sualı dəqiq cavablandır.
            2. Cavabda müvafiq Vergi Məcəlləsinin maddə nömrələrini (məsələn, Maddə 102.1.30) aydın qeyd et.
            3. Dəyişiklik aktlarının texniki ifadələrini ("sözləri əvəz edilmişdir" və s.) təkrarlama, maddənin son vəziyyətinin mahiyyətini izah et.
            4. Əgər cavab kontekstdə yoxdursa, uydurma cavab vermə, sadəcə bilmədiyini qeyd et.

            Kontekst:
            {context}
            """;

        Flux<String> tokenStream = chatClient.prompt()
                .system(sp -> sp.text(systemPrompt).param("context", context))
                .user(question)
                .stream()
                .content();

        return bufferIntoSentences(tokenStream);
    }

    private Flux<String> bufferIntoSentences(Flux<String> tokenStream) {
        StringBuilder buffer = new StringBuilder();
        Pattern sentenceEnd = Pattern.compile(".*?[.!?:](\\s+|$)", Pattern.DOTALL);

        Flux<String> sentences = tokenStream.concatMap(token -> {
            buffer.append(token);
            List<String> completed = new ArrayList<>();

            Matcher matcher = sentenceEnd.matcher(buffer);
            int lastEnd = 0;
            while (matcher.find()) {
                completed.add(matcher.group().trim());
                lastEnd = matcher.end();
            }
            if (lastEnd > 0) {
                buffer.delete(0, lastEnd);
            }
            return Flux.fromIterable(completed);
        });

        // Axın bitəndə buffer-də qalan (nöqtəsiz) son hissəni də göndər
        return sentences.concatWith(Flux.defer(() ->
                buffer.length() > 0 ? Flux.just(buffer.toString().trim()) : Flux.empty()
        ));
    }

    public String askTaxQuestion(String question) {
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(10)
                        .similarityThreshold(0.38)
                        .build()
        );
        similarDocuments.forEach(doc ->
                log.info("SCORE: {} | CHUNK:\n{}\n---", doc.getMetadata().get("distance"), doc.getText())
        );

        if (similarDocuments.isEmpty()) {
            return "Təqdim olunan vergi qanunvericiliyi bazasında bu suala uyğun dəqiq məlumat tapılmadı.";
        }

        String context = similarDocuments.stream()
                .map(doc -> String.format(
                        "Maddə: %s\nFəsil: %s\nMətn:\n%s",
                        doc.getMetadata().get("article"),
                        doc.getMetadata().get("chapter"),
                        doc.getText()
                ))
                .collect(Collectors.joining("\n\n---\n\n"));

        PromptTemplate promptTemplate = new PromptTemplate("""
                Sən Azərbaycan Respublikasının Vergi Məcəlləsi üzrə peşəkar hüquqi assistentisən.
                
                QAYDALAR:
                1. YALNIZ aşağıdakı vergi qanunvericiliyi kontekstinə əsasən sualı dəqiq cavablandır.
                2. Cavabda müvafiq Vergi Məcəlləsinin maddə nömrələrini (məsələn, Maddə 102.1.30) aydın qeyd et.
                3. Dəyişiklik aktlarının texniki ifadələrini ("sözləri əvəz edilmişdir" və s.) təkrarlama, maddənin son vəziyyətinin mahiyyətini izah et.
                4. Əgər cavab kontekstdə yoxdursa, uydurma cavab vermə, sadəcə bilmədiyini qeyd et.

                Kontekst:
                {context}

                Sual:
                {question}
                """);

        Prompt prompt = promptTemplate.create(Map.of(
                "context", context,
                "question", question
        ));

        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
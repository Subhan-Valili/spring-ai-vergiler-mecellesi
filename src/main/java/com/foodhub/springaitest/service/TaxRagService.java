package com.foodhub.springaitest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
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

    private static final String SYSTEM_PROMPT = """
            Sən Azərbaycan Respublikasının Vergi Məcəlləsi üzrə peşəkar hüquqi assistentisən.
            
            TƏLƏBLƏR VƏ QAYDALAR:
            1. Cavabı MÜMKÜN QƏDƏR ƏTRAFLI, İSRAFLI DETALLI və MƏNTİQİ şəkildə ver. Qısa xülasə ilə kifayətlənmə.
            2. Kontekstdə olan HƏR BİR bəndi, şərti, müddəti və istisnanı buraxmadan tək-tək izah et.
            3. Cavabı bu şəkildə geniş strukturlaşdır:
               - **Hüquqi Mahiyyət və Anlayış**: Maddənin ümumi məqsədi və geniş tərifi.
               - **Əhatə Etdiyi Hallar və Şərtlər**: Bütün aidiyyəti bəndlərin təfərrüatlı izahı (bənd nömrələri ilə).
               - **İstisnalar (Şamil edilməyən / Yaratmayan hallar)**: Inkar mənalı bəndlərin ("yaratmır", "sayılmır") ayrı-ayrı geniş izahı.
               - **Xüsusi Qaydalar və Müddətlər**: Kontekstdə keçən müddətlər (günlər, aylar), faizlər və ya xüsusi tələblər.
            4. Maddə və bənd nömrələrini (məsələn, Maddə 19.2, Maddə 19.3.1) aydın göstər.
            5. Kontekst xaricinə çıxma, uydurma cavab vermə.
            
            Kontekst:
            {context}
            
            Sual:
            {question}
            """;

    public TaxRagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, ChatModel chatModel) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    public String askTaxQuestion(String question) {
        long startTime = System.currentTimeMillis();

        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
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
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
        Prompt prompt = promptTemplate.create(Map.of(
                "context", context,
                "question", question
        ));

        ChatResponse response = chatModel.call(prompt);
        long duration = System.currentTimeMillis() - startTime;

        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            Usage usage = response.getMetadata().getUsage();

            log.info("================ TOKEN XƏRCİ VƏ METRİKALAR ================");
            log.info("Prompt Tokens (Sual + Kontekst xərci): {}", usage.getPromptTokens());
            log.info("Generation Tokens (Modelin cavab xərci): {}", usage.getCompletionTokens());
            log.info("Ümumi İşlədilən Token: {}", usage.getTotalTokens());
            log.info("Soruşma və cavabalma müddəti: {} ms", duration);
            log.info("==========================================================");
        }

        return response.getResult().getOutput().getText();
    }

//    public Flux<String> askTaxQuestionStream(String question) {
//        List<Document> similarDocs = vectorStore.similaritySearch(
//                SearchRequest.builder()
//                        .query(question)
//                        .topK(6)
//                        .similarityThreshold(0.45)
//                        .build()
//        );
//
//        if (similarDocs.isEmpty()) {
//            return Flux.just("Təqdim olunan vergi qanunvericiliyi bazasında bu suala uyğun məlumat tapılmadı.");
//        }
//
//        String context = similarDocs.stream()
//                .map(Document::getText)
//                .collect(Collectors.joining("\n\n---\n\n"));
//
//        Flux<String> tokenStream = chatClient.prompt()
//                .system(sp -> sp.text(SYSTEM_PROMPT).param("context", context).param("question", question))
//                .user(question)
//                .stream()
//                .content();
//
//        return bufferIntoSentences(tokenStream);
//    }

//    private Flux<String> bufferIntoSentences(Flux<String> tokenStream) {
//        StringBuilder buffer = new StringBuilder();
//        Pattern sentenceEnd = Pattern.compile(".*?[.!?:](\\s+|$)", Pattern.DOTALL);
//
//        Flux<String> sentences = tokenStream.concatMap(token -> {
//            buffer.append(token);
//            List<String> completed = new ArrayList<>();
//
//            Matcher matcher = sentenceEnd.matcher(buffer);
//            int lastEnd = 0;
//            while (matcher.find()) {
//                completed.add(matcher.group().trim());
//                lastEnd = matcher.end();
//            }
//            if (lastEnd > 0) {
//                buffer.delete(0, lastEnd);
//            }
//            return Flux.fromIterable(completed);
//        });
//
//        return sentences.concatWith(Flux.defer(() ->
//                buffer.length() > 0 ? Flux.just(buffer.toString().trim()) : Flux.empty()
//        ));
//    }
}
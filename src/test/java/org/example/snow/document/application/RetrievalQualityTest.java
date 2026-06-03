package org.example.snow.document.application;

import org.example.snow.document.application.chunking.BoilerplateFilter;
import org.example.snow.document.application.chunking.ChunkComposer;
import org.example.snow.document.application.chunking.DocumentTitleResolver;
import org.example.snow.document.application.chunking.SectionBuilder;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.infra.extractor.PdfTextExtractor;
import org.example.snow.embedding.infra.EmbeddingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 임베딩 모델만으로 검색 정밀도를 진단한다.
 *
 * 실제 청킹 파이프라인으로 청크를 만들고, 각 청크를 breadcrumb 헤더와 함께 임베딩한 뒤
 * 질의 임베딩과의 코사인 유사도로 순위를 매겨 "질의가 올바른 청크를 상위에 끌어올리는지" 확인한다.
 * (생성 모델 불필요 — Parent-Child 검색 단계까지만 검증)
 *
 * 실행:
 *   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test --tests '*RetrievalQualityTest' \
 *     -Dmeasure.pdf=build/tmp/test/cloud02_1_IAM.pptx.pdf \
 *     -Dretrieval.queries='Root 사용자 권한;IAM 정책이란;AWS 계정의 의미' --rerun-tasks
 */
@EnabledIfSystemProperty(named = "measure.pdf", matches = ".+")
class RetrievalQualityTest {

    private final DocumentIngestionService ingestionService = new DocumentIngestionService(
            List.of(new PdfTextExtractor()),
            new TextPreprocessor(),
            new ChunkingService(new SectionBuilder(), new ChunkComposer()),
            new BoilerplateFilter(),
            new DocumentTitleResolver()
    );

    private EmbeddingClient embeddingClient() {
        EmbeddingClient client = new EmbeddingClient();
        ReflectionTestUtils.setField(client, "embeddingUrl",
                System.getenv().getOrDefault("OLLAMA_EMBEDDING_URL", "http://localhost:11434"));
        ReflectionTestUtils.setField(client, "embeddingModel",
                System.getenv().getOrDefault("OLLAMA_EMBEDDING_MODEL", "qwen3-embedding:0.6b"));
        ReflectionTestUtils.setField(client, "connectTimeoutSeconds", 5);
        ReflectionTestUtils.setField(client, "readTimeoutSeconds", 60);
        return client;
    }

    @Test
    void retrieval() throws Exception {
        Path pdfPath = Path.of(System.getProperty("measure.pdf"));
        int topK = Integer.parseInt(System.getProperty("retrieval.topk", "5"));
        List<String> queries = List.of(System.getProperty("retrieval.queries",
                "Root 사용자 권한;IAM 정책이란 무엇인가;AWS 계정의 의미").split(";"));

        DocumentUploadCommand command = new DocumentUploadCommand(
                new UploadedDocument(pdfPath.getFileName().toString(), "application/pdf",
                        Files.readAllBytes(pdfPath)));
        DocumentProcessingResult result = ingestionService.ingest(command);
        List<ExtractedChunk> chunks = result.chunks();

        EmbeddingClient client = embeddingClient();

        // 청크: 실제 파이프라인과 동일하게 breadcrumb 헤더를 붙여 임베딩
        List<String> inputs = chunks.stream()
                .map(c -> embeddingHeader(result.docTitle(), c) + c.text())
                .toList();
        // 운영(EmbeddingService)과 동일하게 배치로 임베딩 (큰 문서 read timeout 방지)
        List<float[]> chunkVectors = new java.util.ArrayList<>();
        int batchSize = 20;
        for (int i = 0; i < inputs.size(); i += batchSize) {
            chunkVectors.addAll(client.embedAll(inputs.subList(i, Math.min(i + batchSize, inputs.size()))));
        }

        System.out.println("\n############ RETRIEVAL QUALITY ############");
        System.out.println("file     : " + pdfPath.getFileName());
        System.out.println("docTitle : " + result.docTitle());
        System.out.println("chunks   : " + chunks.size() + " (각 " + chunkVectors.get(0).length + "차원)");

        for (String query : queries) {
            String q = query.trim();
            float[] qv = client.embed(q);

            List<Integer> idx = new java.util.ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) idx.add(i);
            idx.sort(Comparator.comparingDouble((Integer i) -> cosine(qv, chunkVectors.get(i))).reversed());

            System.out.printf("%n🔎 질의: \"%s\"%n", q);
            for (int rank = 0; rank < Math.min(topK, idx.size()); rank++) {
                int i = idx.get(rank);
                ExtractedChunk c = chunks.get(i);
                System.out.printf("  %d위 sim=%.4f [p%d~%d] [섹션: %s]%n        %s%n",
                        rank + 1, cosine(qv, chunkVectors.get(i)),
                        c.sourceStartIndex(), c.sourceEndIndex(), stripPart(c.heading()),
                        preview(c.text(), 90));
            }
        }
        System.out.println("###########################################\n");
    }

    private String embeddingHeader(String docTitle, ExtractedChunk c) {
        StringBuilder h = new StringBuilder();
        if (docTitle != null && !docTitle.isBlank()) h.append("[문서: ").append(docTitle).append("] ");
        if (c.heading() != null && !c.heading().isBlank()) h.append("[섹션: ").append(stripPart(c.heading())).append("]\n");
        return h.toString();
    }

    private String stripPart(String heading) {
        int idx = heading.indexOf(" (Part ");
        return idx >= 0 ? heading.substring(0, idx) : heading;
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9);
    }

    private String preview(String text, int limit) {
        String s = text.replace("\n", " ⏎ ");
        return s.length() > limit ? s.substring(0, limit) + " …" : s;
    }
}

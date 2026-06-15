package org.example.snow.document.infra.extractor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record OllamaVisionProperties(
        boolean enabled,
        String model,
        String url,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int renderDpi,
        String mode,
        boolean pdfEnabled,
        boolean powerpointEnabled,
        String prompt
) {

    private static final String MODE_ALWAYS = "always";
    private static final String MODE_IMAGE = "image";

    public OllamaVisionProperties(
            @Value("${ollama.vision.enabled:true}") boolean enabled,
            @Value("${ollama.vision.model:qwen3-vl:4b}") String model,
            @Value("${ollama.vision.url:http://localhost:11434}") String url,
            @Value("${ollama.vision.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${ollama.vision.read-timeout-seconds:180}") int readTimeoutSeconds,
            @Value("${ollama.vision.render-dpi:180}") int renderDpi,
            @Value("${ollama.vision.mode:image}") String mode,
            @Value("${ollama.vision.pdf-enabled:true}") boolean pdfEnabled,
            @Value("${ollama.vision.powerpoint-enabled:true}") boolean powerpointEnabled,
            @Value("${ollama.vision.prompt:Analyze this lecture material image for study.}") String prompt
    ) {
        this.enabled = enabled;
        this.model = model;
        this.url = url;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.renderDpi = renderDpi;
        // always 외의 모든 값(미설정·image·오타 포함)은 기본 모드 image로 정규화한다.
        this.mode = (mode != null && MODE_ALWAYS.equalsIgnoreCase(mode.trim())) ? MODE_ALWAYS : MODE_IMAGE;
        this.pdfEnabled = pdfEnabled;
        this.powerpointEnabled = powerpointEnabled;
        this.prompt = prompt;
    }

    boolean alwaysAnalyze() {
        return MODE_ALWAYS.equals(mode);
    }

    /** 페이지/슬라이드에 이미지가 있을 때만 비전 분석 (텍스트 양과 무관). 기본 모드. */
    boolean imageOnly() {
        return MODE_IMAGE.equals(mode);
    }

    static OllamaVisionProperties disabled() {
        return new OllamaVisionProperties(
                false,
                "qwen3-vl:4b",
                "http://localhost:11434",
                5,
                180,
                180,
                MODE_ALWAYS,
                false,
                false,
                "Analyze this lecture material image for study."
        );
    }
}

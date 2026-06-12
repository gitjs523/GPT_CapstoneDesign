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
    private static final String MODE_FALLBACK = "fallback";

    public OllamaVisionProperties(
            @Value("${ollama.vision.enabled:true}") boolean enabled,
            @Value("${ollama.vision.model:qwen3-vl:4b}") String model,
            @Value("${ollama.vision.url:http://localhost:11434}") String url,
            @Value("${ollama.vision.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${ollama.vision.read-timeout-seconds:180}") int readTimeoutSeconds,
            @Value("${ollama.vision.render-dpi:180}") int renderDpi,
            @Value("${ollama.vision.mode:always}") String mode,
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
        this.mode = mode == null ? MODE_ALWAYS : mode.trim().toLowerCase();
        this.pdfEnabled = pdfEnabled;
        this.powerpointEnabled = powerpointEnabled;
        this.prompt = prompt;
    }

    boolean alwaysAnalyze() {
        return MODE_ALWAYS.equals(mode);
    }

    boolean fallbackOnly() {
        return MODE_FALLBACK.equals(mode);
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

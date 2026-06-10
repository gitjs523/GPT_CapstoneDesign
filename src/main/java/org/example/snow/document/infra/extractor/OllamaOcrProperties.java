package org.example.snow.document.infra.extractor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record OllamaOcrProperties(
        boolean enabled,
        String model,
        String url,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int minTextChars,
        int renderDpi,
        boolean powerpointEnabled,
        String prompt
) {

    public OllamaOcrProperties(
            @Value("${ollama.ocr.enabled:true}") boolean enabled,
            @Value("${ollama.ocr.model:glm-ocr:latest}") String model,
            @Value("${ollama.ocr.url:http://localhost:11434}") String url,
            @Value("${ollama.ocr.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${ollama.ocr.read-timeout-seconds:180}") int readTimeoutSeconds,
            @Value("${ollama.ocr.min-text-chars:30}") int minTextChars,
            @Value("${ollama.ocr.render-dpi:180}") int renderDpi,
            @Value("${ollama.ocr.powerpoint-enabled:true}") boolean powerpointEnabled,
            @Value("${ollama.ocr.prompt:Text Recognition:}") String prompt
    ) {
        this.enabled = enabled;
        this.model = model;
        this.url = url;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.minTextChars = minTextChars;
        this.renderDpi = renderDpi;
        this.powerpointEnabled = powerpointEnabled;
        this.prompt = prompt;
    }

    static OllamaOcrProperties disabled() {
        return new OllamaOcrProperties(
                false,
                "glm-ocr:latest",
                "http://localhost:11434",
                5,
                180,
                30,
                180,
                false,
                "Text Recognition:"
        );
    }
}

package org.example.snow.document.infra.extractor;

import lombok.extern.slf4j.Slf4j;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OllamaOcrClient implements OcrTextExtractor {

    private final OllamaOcrProperties properties;
    private volatile RestTemplate restTemplate;

    public OllamaOcrClient(OllamaOcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String extractText(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("OCR image must not be null.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("prompt", properties.prompt());
        body.put("images", List.of(toBase64Png(image)));
        body.put("stream", false);
        body.put("options", Map.of("temperature", 0));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("ngrok-skip-browser-warning", "1");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response;
        try {
            response = getRestTemplate().postForEntity(normalizedUrl() + "/api/generate", request, Map.class);
        } catch (RestClientException exception) {
            log.error("Ollama OCR call failed | url={} model={} cause={}",
                    properties.url(), properties.model(), exception.getMessage(), exception);
            throw new BusinessException(ErrorCode.OCR_TEXT_EXTRACTION_FAILED, exception);
        }

        Map<?, ?> responseBody = response.getBody();
        if (responseBody == null) {
            log.error("Ollama OCR response body is null | url={} model={}", properties.url(), properties.model());
            throw new BusinessException(ErrorCode.OCR_TEXT_EXTRACTION_FAILED);
        }

        Object responseText = responseBody.get("response");
        if (responseText == null || !StringUtils.hasText(responseText.toString())) {
            log.error("Ollama OCR response does not contain text | response={}", responseBody);
            throw new BusinessException(ErrorCode.OCR_TEXT_EXTRACTION_FAILED);
        }

        return responseText.toString().trim();
    }

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (this) {
                if (restTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(properties.connectTimeoutSeconds() * 1000);
                    factory.setReadTimeout(properties.readTimeoutSeconds() * 1000);
                    restTemplate = new RestTemplate(factory);
                }
            }
        }
        return restTemplate;
    }

    private String toBase64Png(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.OCR_TEXT_EXTRACTION_FAILED, exception);
        }
    }

    private String normalizedUrl() {
        String url = properties.url();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}

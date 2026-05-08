package org.example.snow.document.application;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class TextPreprocessor {

    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f]+", " ");

        normalized = Arrays.stream(normalized.split("\\n", -1))
                .map(String::strip)
                .collect(Collectors.joining("\n"));

        normalized = normalized
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return normalized;
    }
}

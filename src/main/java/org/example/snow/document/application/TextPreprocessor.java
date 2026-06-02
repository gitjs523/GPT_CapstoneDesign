package org.example.snow.document.application;

import org.example.snow.global.text.ControlCharSanitizer;
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
                .replace('\r', '\n');

        // \uC190\uC0C1\uB41C \uC784\uBCA0\uB4DC \uD3F0\uD2B8\uB85C \uACF5\uBC31\uC774 U+0000(NUL) \uB4F1 \uC81C\uC5B4\uBB38\uC790\uB85C \uCD94\uCD9C\uB418\uB294 \uACBD\uC6B0 \uACF5\uBC31\uC73C\uB85C \uBCF5\uC6D0\uD55C\uB2E4.
        // NUL\uC774 \uADF8\uB300\uB85C \uB0A8\uC73C\uBA74 PostgreSQL text \uCEEC\uB7FC \uC800\uC7A5 \uC2DC \uC608\uC678\uAC00 \uBC1C\uC0DD\uD574 \uBD84\uC11D \uD30C\uC774\uD504\uB77C\uC778\uC774 \uC2E4\uD328\uD55C\uB2E4.
        // \t\u00B7\n\u00B7\r\uC740 ControlCharSanitizer\uAC00 \uBCF4\uC874\uD55C\uB2E4.
        normalized = ControlCharSanitizer.sanitize(normalized);

        normalized = normalized
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

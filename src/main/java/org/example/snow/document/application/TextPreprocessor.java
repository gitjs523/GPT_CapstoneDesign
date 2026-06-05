package org.example.snow.document.application;

import org.example.snow.document.application.chunking.OutlineMarkers;
import org.example.snow.global.text.ControlCharSanitizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                .replaceAll("[\\t\\x0B\\f]+", " ")
                // \uBE48 \uAD04\uD638 \uC544\uD2F0\uD329\uD2B8 \uC81C\uAC70 (PDF \uCD94\uCD9C \uC2DC placeholder \uBC15\uC2A4\uAC00 "[ ]"\uB85C \uB0A8\uB294 \uACBD\uC6B0)
                .replaceAll("[\\[\\uFF3B]\\s*[\\]\\uFF3D]", " ");

        List<String> lines = Arrays.stream(normalized.split("\\n", -1))
                .map(String::strip)
                .toList();
        normalized = joinWrappedLines(lines);

        normalized = normalized
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return normalized;
    }

    /**
     * 화면 폭 때문에 끊긴 줄(wrapping)을 한 줄로 병합한다.
     *
     * 직전 줄이 문장 종결 부호로 끝나지 않고, 다음 줄이 새 마커로 시작하지 않으면 이어붙인다.
     * 단, 마커 없는 짧은 제목줄은 뒤 본문을 흡수하지 않도록 보존한다(헤딩 탐지 보호).
     * 빈 줄(문단 경계)은 병합 경계로 유지한다.
     */
    private String joinWrappedLines(List<String> lines) {
        List<String> result = new ArrayList<>();
        String current = null;
        for (String line : lines) {
            if (line.isBlank()) {
                if (current != null) {
                    result.add(current);
                    current = null;
                }
                result.add("");
            } else if (current == null) {
                current = line;
            } else if (shouldJoin(current, line)) {
                current = current + " " + line;
            } else {
                result.add(current);
                current = line;
            }
        }
        if (current != null) {
            result.add(current);
        }
        return String.join("\n", result);
    }

    private boolean shouldJoin(String previous, String next) {
        if (OutlineMarkers.endsWithSentencePunctuation(previous)) {
            return false;
        }
        if (OutlineMarkers.startsWithMarker(next)) {
            return false;
        }
        // 마커 없는 짧은 제목줄은 뒤 본문을 흡수하지 않는다.
        if (OutlineMarkers.isShortTitleLine(previous) && !OutlineMarkers.startsWithMarker(previous)) {
            return false;
        }
        return true;
    }
}

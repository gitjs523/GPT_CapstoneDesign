package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedSourceUnit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 문서 전체에서 반복되는 머리말/꼬리말·페이지 번호를 제거한다.
 *
 * 대부분 페이지에 반복되는 짧은 라인(예: 핸드아웃 머리말 "경제학개론")은 Section 경계 탐지를
 * 오염시키므로 제거한다. 동시에 그 반복 라인을 반환해 문서 제목 추정에 쓴다.
 * (강의 슬라이드의 섹션별 제목은 반복률이 낮아 제거되지 않는다 — 의도된 동작.)
 */
@Component
public class BoilerplateFilter {

    private static final double REPEAT_RATIO_THRESHOLD = 0.6;
    private static final int MIN_UNITS_FOR_REPEAT_DETECTION = 4;
    private static final int MAX_BOILERPLATE_LINE_LENGTH = 40;
    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile("^[-\\s]*\\d{1,4}[-\\s]*$");

    public record Result(List<ExtractedSourceUnit> units, List<String> repeatedLines) {
    }

    public Result filter(List<ExtractedSourceUnit> units) {
        Set<String> repeated = detectRepeatedLines(units);

        List<ExtractedSourceUnit> cleaned = new ArrayList<>();
        for (ExtractedSourceUnit unit : units) {
            cleaned.add(new ExtractedSourceUnit(unit.index(), unit.heading(), removeLines(unit.text(), repeated)));
        }
        return new Result(cleaned, new ArrayList<>(repeated));
    }

    /** 페이지의 ≥60%에 등장하는 짧은 라인을 반복 라인으로 본다. 문서가 작으면(<4) 적용하지 않는다. */
    private Set<String> detectRepeatedLines(List<ExtractedSourceUnit> units) {
        Set<String> repeated = new LinkedHashSet<>();
        if (units.size() < MIN_UNITS_FOR_REPEAT_DETECTION) {
            return repeated;
        }

        Map<String, Integer> unitCount = new HashMap<>();
        for (ExtractedSourceUnit unit : units) {
            Set<String> linesInUnit = new HashSet<>();
            for (String raw : unit.text().split("\n")) {
                String line = OutlineMarkers.normalizeHeading(raw);
                if (line.isEmpty() || line.length() > MAX_BOILERPLATE_LINE_LENGTH) {
                    continue;
                }
                if (PAGE_NUMBER_LINE.matcher(line).matches()) {
                    continue; // 페이지 번호는 빈도와 무관하게 removeLines에서 제거
                }
                linesInUnit.add(line);
            }
            for (String line : linesInUnit) {
                unitCount.merge(line, 1, Integer::sum);
            }
        }

        int threshold = (int) Math.ceil(units.size() * REPEAT_RATIO_THRESHOLD);
        for (Map.Entry<String, Integer> entry : unitCount.entrySet()) {
            if (entry.getValue() >= threshold) {
                repeated.add(entry.getKey());
            }
        }
        return repeated;
    }

    private String removeLines(String text, Set<String> repeated) {
        List<String> kept = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            String norm = OutlineMarkers.normalizeHeading(raw);
            if (!norm.isEmpty() && PAGE_NUMBER_LINE.matcher(norm).matches()) {
                continue; // 페이지 번호
            }
            if (!norm.isEmpty() && repeated.contains(norm)) {
                continue; // 반복 머리말/꼬리말
            }
            kept.add(raw);
        }
        return String.join("\n", kept).strip();
    }
}

package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 전처리·boilerplate 제거가 끝난 문서를 문단 블록으로 분리한다.
 *
 * 블록은 semantic 분할(임베딩 유사도)의 단위이자 최종 Chunk다. 빈 줄(\n\n) 경계로 나누고,
 * 너무 짧은 단편(MIN 미만)은 직전 블록에 흡수, 너무 긴 문단(MAX 초과)은 길이로 분할한다.
 * 마커/헤딩에 의존하지 않는다 — 경계 판정은 이후 SemanticSectionizer가 임베딩으로 수행한다.
 */
@Component
public class BlockSplitter {

    private static final int MIN_BLOCK_CHARS = 40;
    private static final int MAX_BLOCK_CHARS = 600;

    public List<TextBlock> split(ExtractedDocument document) {
        List<TextBlock> blocks = new ArrayList<>();
        for (ExtractedSourceUnit unit : document.sourceUnits()) {
            if (unit.text() == null || unit.text().isBlank()) {
                continue;
            }
            for (String paragraph : unit.text().split("\n{2,}")) {
                String p = paragraph.trim();
                if (p.isBlank()) {
                    continue;
                }
                List<String> parts = p.length() > MAX_BLOCK_CHARS ? splitByLength(p) : List.of(p);
                for (String part : parts) {
                    if (part.length() < MIN_BLOCK_CHARS && !blocks.isEmpty()) {
                        TextBlock prev = blocks.remove(blocks.size() - 1);
                        blocks.add(new TextBlock(prev.page(), prev.text() + "\n" + part, prev.sourceType()));
                    } else {
                        blocks.add(new TextBlock(unit.index(), part, document.sourceType()));
                    }
                }
            }
        }
        return blocks;
    }

    private List<String> splitByLength(String text) {
        List<String> out = new ArrayList<>();
        for (int start = 0; start < text.length(); start += MAX_BLOCK_CHARS) {
            out.add(text.substring(start, Math.min(start + MAX_BLOCK_CHARS, text.length())).trim());
        }
        return out;
    }
}

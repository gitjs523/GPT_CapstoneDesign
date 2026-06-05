package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.SourceUnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTitleResolverTest {

    private final DocumentTitleResolver resolver = new DocumentTitleResolver();

    private ExtractedSection section(String heading) {
        return new ExtractedSection(1, heading, "본문", SourceUnitType.PAGE, 1, 1, List.of(1));
    }

    @Test
    void 반복_라인이_있으면_그것을_제목으로() {
        String title = resolver.resolve(List.of("경제학개론"), List.of(section("1주차 개념")), "경제학개론_전반기.pdf");

        assertThat(title).isEqualTo("경제학개론");
    }

    @Test
    void 반복_라인이_없으면_첫_섹션_헤딩을_제목으로() {
        String title = resolver.resolve(List.of(), List.of(section("04. EC2")), "cloud02_2_EC2.pptx.pdf");

        // 번호 접두사 제거
        assertThat(title).isEqualTo("EC2");
    }

    @Test
    void 기본_헤딩이면_파일명으로_폴백() {
        String title = resolver.resolve(List.of(), List.of(section("Section 1")), "cloud02_2_EC2.pptx.pdf");

        // 이중 확장자 제거 + 언더스코어 정리
        assertThat(title).isEqualTo("cloud02 2 EC2");
    }

    @Test
    void 섹션도_없으면_파일명으로_폴백() {
        String title = resolver.resolve(List.of(), List.of(), "경제학개론_후반기.pdf");

        assertThat(title).isEqualTo("경제학개론 후반기");
    }
}

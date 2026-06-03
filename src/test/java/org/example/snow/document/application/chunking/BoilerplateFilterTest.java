package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedSourceUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoilerplateFilterTest {

    private final BoilerplateFilter filter = new BoilerplateFilter();

    private ExtractedSourceUnit unit(int index, String text) {
        return new ExtractedSourceUnit(index, "Page " + index, text);
    }

    @Test
    void 대부분_페이지에_반복되는_머리말은_제거되고_반환된다() {
        List<ExtractedSourceUnit> units = List.of(
                unit(1, "경제학개론\n1주차 경제학의 개념\n정의 내용"),
                unit(2, "경제학개론\n2. 경제학의 구분\n구분 내용"),
                unit(3, "경제학개론\n3) 미시경제학\n미시 내용"),
                unit(4, "경제학개론\n4. 방법론\n방법론 내용")
        );

        BoilerplateFilter.Result result = filter.filter(units);

        assertThat(result.repeatedLines()).contains("경제학개론");
        assertThat(result.units()).allSatisfy(u -> assertThat(u.text()).doesNotContain("경제학개론"));
        assertThat(result.units().get(0).text()).startsWith("1주차 경제학의 개념");
    }

    @Test
    void 페이지_번호_라인은_제거된다() {
        List<ExtractedSourceUnit> units = List.of(
                unit(1, "1\n첫 페이지 본문"),
                unit(2, "2\n둘째 페이지 본문"),
                unit(3, "3\n셋째 페이지 본문"),
                unit(4, "4\n넷째 페이지 본문")
        );

        BoilerplateFilter.Result result = filter.filter(units);

        assertThat(result.units()).allSatisfy(u ->
                assertThat(u.text().lines().findFirst().orElse("")).doesNotMatch("\\d+"));
        assertThat(result.units().get(0).text()).isEqualTo("첫 페이지 본문");
    }

    @Test
    void 반복되지_않는_라인은_보존된다() {
        List<ExtractedSourceUnit> units = List.of(
                unit(1, "고유한 제목 A\n본문 A"),
                unit(2, "고유한 제목 B\n본문 B"),
                unit(3, "고유한 제목 C\n본문 C"),
                unit(4, "고유한 제목 D\n본문 D")
        );

        BoilerplateFilter.Result result = filter.filter(units);

        assertThat(result.repeatedLines()).isEmpty();
        assertThat(result.units().get(0).text()).isEqualTo("고유한 제목 A\n본문 A");
    }

    @Test
    void 문서가_작으면_빈도_기반_제거를_하지_않는다() {
        // 3페이지(<4) — 머리말이 반복돼도 제거하지 않는다(오탐 방지)
        List<ExtractedSourceUnit> units = List.of(
                unit(1, "머리말\n본문 1"),
                unit(2, "머리말\n본문 2"),
                unit(3, "머리말\n본문 3")
        );

        BoilerplateFilter.Result result = filter.filter(units);

        assertThat(result.repeatedLines()).isEmpty();
        assertThat(result.units().get(0).text()).contains("머리말");
    }
}

package org.example.snow.global.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControlCharSanitizerTest {

    private final ControlCharSanitizingConverter converter = new ControlCharSanitizingConverter();

    @Test
    void null은_그대로_null() {
        assertThat(ControlCharSanitizer.sanitize(null)).isNull();
    }

    @Test
    void NUL은_공백으로_치환되고_결과에_없다() {
        String result = ControlCharSanitizer.sanitize("문제\u0000정답\u0000해설");

        assertThat(result).doesNotContain("\u0000");
        assertThat(result).isEqualTo("문제 정답 해설");
    }

    @Test
    void C0_C1_DEL_제어문자_전부_공백으로() {
        // BEL, US, VT, FF, DEL, NEL(C1)
        String result = ControlCharSanitizer.sanitize("가\u0007나\u001F다\u000B라\u000C마\u007F바\u0085사");

        assertThat(result).doesNotContainPattern("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F]");
        assertThat(result).isEqualTo("가 나 다 라 마 바 사");
    }

    @Test
    void 탭_줄바꿈_캐리지리턴은_보존된다() {
        String result = ControlCharSanitizer.sanitize("a\tb\nc\rd");

        assertThat(result).isEqualTo("a\tb\nc\rd");
    }

    @Test
    void 정상_텍스트는_그대로() {
        assertThat(ControlCharSanitizer.sanitize("정상 문장입니다.")).isEqualTo("정상 문장입니다.");
    }

    // ─── Converter ───

    @Test
    void converter_저장시_NUL_제거() {
        assertThat(converter.convertToDatabaseColumn("a\u0000b")).isEqualTo("a b");
    }

    @Test
    void converter_저장시_null_허용() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void converter_읽기는_통과() {
        assertThat(converter.convertToEntityAttribute("a b")).isEqualTo("a b");
    }
}

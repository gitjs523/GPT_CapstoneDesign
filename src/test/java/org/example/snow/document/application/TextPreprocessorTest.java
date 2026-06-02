package org.example.snow.document.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextPreprocessorTest {

    private final TextPreprocessor textPreprocessor = new TextPreprocessor();

    @Test
    void null_input_is_empty() {
        assertThat(textPreprocessor.normalize(null)).isEmpty();
    }

    @Test
    void blank_input_is_empty() {
        assertThat(textPreprocessor.normalize("   \n  ")).isEmpty();
    }

    // ─── 핵심: 손상 폰트로 공백이 U+0000(NUL)로 추출되는 경우 ───

    @Test
    void nul_is_restored_to_space_and_absent_from_result() {
        // 손상 폰트 추출 결과: 단어 사이 공백이 NUL(U+0000)로 깨진 상태
        String broken = "경제학은\u0000재화와\u0000서비스의\u0000생산";

        String result = textPreprocessor.normalize(broken);

        assertThat(result).doesNotContain("\u0000");
        assertThat(result).isEqualTo("경제학은 재화와 서비스의 생산");
    }

    @Test
    void mixed_nul_and_space_collapse_to_single_space() {
        String broken = "단어A\u0000 \u0000단어B";

        String result = textPreprocessor.normalize(broken);

        assertThat(result).isEqualTo("단어A 단어B");
    }

    @Test
    void other_c0_c1_control_chars_become_space() {
        // U+0007=BEL, U+001F=US, U+007F=DEL, U+0085=NEL(C1)
        String broken = "가\u0007나\u001F다\u007F라\u0085마";

        String result = textPreprocessor.normalize(broken);

        assertThat(result).doesNotContainPattern("[\\x00-\\x08\\x0E-\\x1F\\x7F-\\x9F]");
        assertThat(result).isEqualTo("가 나 다 라 마");
    }

    // ─── 보존되어야 하는 것: 줄 구조 / 정상 공백 ───

    @Test
    void newline_structure_is_preserved() {
        String text = "첫 줄\n\n둘째 줄";

        assertThat(textPreprocessor.normalize(text)).isEqualTo("첫 줄\n\n둘째 줄");
    }

    @Test
    void clean_document_is_unchanged() {
        String text = "AWS EC2는 컴퓨팅 서비스다.";

        assertThat(textPreprocessor.normalize(text)).isEqualTo("AWS EC2는 컴퓨팅 서비스다.");
    }

    // ─── 기존 정규화 동작 회귀 ───

    @Test
    void tabs_and_repeated_spaces_collapse_to_single_space() {
        String text = "단어A\t\t단어B    단어C";

        assertThat(textPreprocessor.normalize(text)).isEqualTo("단어A 단어B 단어C");
    }

    @Test
    void cr_and_crlf_normalize_to_lf() {
        String text = "줄1\r\n줄2\r줄3";

        assertThat(textPreprocessor.normalize(text)).isEqualTo("줄1\n줄2\n줄3");
    }

    @Test
    void nbsp_becomes_regular_space() {
        String text = "단어A\u00A0단어B";

        assertThat(textPreprocessor.normalize(text)).isEqualTo("단어A 단어B");
    }

    @Test
    void three_or_more_newlines_collapse_to_two() {
        String text = "줄1\n\n\n\n줄2";

        assertThat(textPreprocessor.normalize(text)).isEqualTo("줄1\n\n줄2");
    }
}

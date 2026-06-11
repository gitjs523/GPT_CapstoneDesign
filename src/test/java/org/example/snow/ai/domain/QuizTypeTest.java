package org.example.snow.ai.domain;

import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizTypeTest {

    @Test
    void from_resolvesAllowedLabels() {
        assertThat(QuizType.from("객관식")).isEqualTo(QuizType.MULTIPLE_CHOICE);
        assertThat(QuizType.from("단답형")).isEqualTo(QuizType.SHORT_ANSWER);
        assertThat(QuizType.from("서술형")).isEqualTo(QuizType.DESCRIPTIVE);
    }

    @Test
    void from_trimsSurroundingWhitespace() {
        assertThat(QuizType.from("  객관식 ")).isEqualTo(QuizType.MULTIPLE_CHOICE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"주관식", "MULTIPLE_CHOICE", "4지선다", "객관"})
    void from_rejectsValuesOutsideAllowedSet(String raw) {
        assertThatThrownBy(() -> QuizType.from(raw))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_QUIZ_TYPE));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void from_rejectsBlank(String raw) {
        assertThatThrownBy(() -> QuizType.from(raw))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_QUIZ_TYPE));
    }
}

package org.example.snow.ai.domain;

import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.util.StringUtils;

/**
 * 문제 유형 단일 출처. 허용값은 객관식/단답형/서술형 셋으로 제한한다 (function-specs #31).
 * DB에는 한글 label을 그대로 저장하므로(기존 데이터 호환), 외부 입력은 {@link #from(String)}으로
 * 검증·정규화한 뒤 label을 사용한다.
 */
public enum QuizType {

    MULTIPLE_CHOICE("객관식"),
    SHORT_ANSWER("단답형"),
    DESCRIPTIVE("서술형");

    private final String label;

    QuizType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 입력 문자열을 trim 후 허용 label과 매칭한다. 비어있거나 허용값을 벗어나면
     * {@link ErrorCode#INVALID_QUIZ_TYPE}로 거부한다.
     */
    public static QuizType from(String raw) {
        if (StringUtils.hasText(raw)) {
            String normalized = raw.trim();
            for (QuizType type : values()) {
                if (type.label.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new BusinessException(ErrorCode.INVALID_QUIZ_TYPE);
    }
}

package org.example.snow.global.text;

import java.util.regex.Pattern;

/**
 * 텍스트에서 인쇄 불가 제어문자를 공백으로 치환하는 공유 유틸.
 *
 * 특히 U+0000(NUL)은 PostgreSQL의 text/varchar 컬럼에 저장할 수 없어
 * INSERT 시 예외를 일으킨다. 손상된 임베드 폰트로 추출된 PDF(공백이 NUL로 깨짐),
 * 생성 모델 출력, 사용자 입력 등 어느 경로로 들어오든 영속화 전에 정화해야 한다.
 *
 * 보존 대상: \t(U+0009), \n(U+000A), \r(U+000D) — 줄/탭 구조는 의미가 있고 DB 저장도 가능하다.
 * 제거 대상: 그 외 C0 제어문자(U+0000~U+001F), DEL(U+007F), C1 제어문자(U+0080~U+009F).
 */
public final class ControlCharSanitizer {

    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F]");

    private ControlCharSanitizer() {
    }

    /**
     * 제어문자를 공백으로 치환한다. null은 그대로 반환한다.
     * 제거가 아니라 공백 치환인 이유: 깨진 공백(NUL)을 복원하고 단어 경계가 붙는 것을 막기 위함.
     */
    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        return CONTROL_CHARS.matcher(text).replaceAll(" ");
    }
}

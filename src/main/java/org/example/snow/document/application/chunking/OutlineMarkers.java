package org.example.snow.document.application.chunking;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 아웃라인 마커 인식의 단일 정의.
 *
 * 강의 슬라이드(불릿 ➢○■·•)와 텍스트 핸드아웃(번호 N. N) N주차 ①, 영문 A.)처럼
 * 작성자마다 다른 마커 체계를 한곳에서 인식한다. wrapping 줄바꿈 병합(TextPreprocessor)과
 * Section label 추출(SemanticSectionizer)이 공통으로 사용한다.
 *
 * 순수 유틸 — Spring 의존 없음.
 */
public final class OutlineMarkers {

    /** 기호 불릿 글리프 모음. */
    private static final String BULLET_CHARS =
            "\\u2022\\u00B7\\u25CF\\u25CB\\u25A0\\u25A1\\u2023\\u25E6\\u25AA\\u25B6\\u27A2\\u25C6";

    /**
     * 줄 첫머리의 마커 토큰(불릿/번호/문자 열거). 뒤따르는 공백까지 포함해 매칭한다.
     * 소문자 'o'는 뒤에 공백이 있을 때만 불릿으로 본다(영단어 오인 방지).
     */
    private static final Pattern LEADING_MARKER = Pattern.compile(
            "^\\s*(?:" +
                    "[" + BULLET_CHARS + "]" +              // 기호 불릿
                    "|[-*]" +                                // ASCII 불릿
                    "|o(?=\\s)" +                            // 소문자 o 불릿
                    "|\\d+(?:[.\\-]\\d+)*[.)]" +             // 1.  1)  1.2.3.
                    "|\\uC81C\\s*\\d+\\s*[\\uC7A5\\uC808\\uC870\\uD56D]" + // 제1장/절/조/항
                    "|\\d+\\s*(?:\\uC8FC\\uCC28|\\uCC28\\uC2DC|\\uB2E8\\uC6D0|\\uC7A5|\\uC808)" + // 1주차 3차시 단원 장 절
                    "|[\\u2460-\\u2473\\u2776-\\u277F]" +    // 원숫자 ①~⑳ ❶~❿
                    "|[A-Za-z][.)]" +                        // A.  a)
                    "|[\\uAC00\\uB098\\uB2E4\\uB77C\\uB9C8\\uBC14\\uC0AC\\uC544\\uC790\\uCC28\\uCE74\\uD0C0\\uD30C\\uD558][.)]" + // 가. 나)
                    ")\\s*");

    /** 강한 구조적 헤딩 신호: 마커 + 공백 + 본문. */
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^(?:\\d+(?:[.\\-]\\d+)*[.)]?" +
                    "|\\uC81C\\s*\\d+\\s*[\\uC7A5\\uC808\\uC870\\uD56D]" +
                    "|\\d+\\s*(?:\\uC8FC\\uCC28|\\uCC28\\uC2DC|\\uB2E8\\uC6D0|\\uC7A5|\\uC808)" +
                    "|[A-Z][.)]|[IVXLCM]+[.)]|[\\u2460-\\u2473])\\s+.+$");

    private static final Pattern GENERIC_PAGE_HEADING = Pattern.compile("^Page\\s+\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_SLIDE_HEADING = Pattern.compile("^Slide\\s+\\d+$", Pattern.CASE_INSENSITIVE);

    private static final int SHORT_TITLE_MAX_CHARS = 40;
    private static final int SHORT_TITLE_MAX_WORDS = 10;

    private OutlineMarkers() {
    }

    /** 줄이 불릿/번호/문자 열거 마커로 시작하는가. */
    public static boolean startsWithMarker(String line) {
        return line != null && LEADING_MARKER.matcher(line).lookingAt();
    }

    /** 줄 첫머리 마커를 제거한 라벨 텍스트. 마커가 없으면 원문(trim)을 반환. */
    public static String stripLeadingMarker(String line) {
        if (line == null) {
            return "";
        }
        return LEADING_MARKER.matcher(line).replaceFirst("").trim();
    }

    /** 공백 정규화(앞뒤 trim + 연속 공백 1칸). */
    public static String normalizeHeading(String heading) {
        return heading == null ? "" : heading.trim().replaceAll("\\s+", " ");
    }

    /** 문장 종결 부호로 끝나는가(=완결 문장). wrapping 병합 판정에 사용. */
    public static boolean endsWithSentencePunctuation(String line) {
        if (line == null) {
            return false;
        }
        String t = line.stripTrailing().toLowerCase(Locale.ROOT);
        return t.endsWith(".") || t.endsWith("?") || t.endsWith("!")
                || t.endsWith("。") || t.endsWith("？") || t.endsWith("！") // 。？！
                || t.endsWith("다.") || t.endsWith("요.") || t.endsWith("음.") // 다. 요. 음.
                || t.endsWith(";") || t.endsWith(":");
    }

    /** 번호/문자 열거로 시작하는 강한 헤딩 신호. */
    public static boolean isNumberedHeading(String line) {
        return line != null && NUMBERED_HEADING.matcher(normalizeHeading(line)).matches();
    }

    /** 짧고 문장이 아닌 제목줄(휴리스틱 헤딩). */
    public static boolean isShortTitleLine(String line) {
        String n = normalizeHeading(line);
        if (n.isEmpty() || n.length() > SHORT_TITLE_MAX_CHARS) {
            return false;
        }
        if (endsWithSentencePunctuation(n)) {
            return false;
        }
        return n.split("\\s+").length <= SHORT_TITLE_MAX_WORDS;
    }

    /** "Page 12" 같은 추출기 기본 페이지 헤딩. */
    public static boolean isGenericPageHeading(String heading) {
        return heading != null && GENERIC_PAGE_HEADING.matcher(heading.trim()).matches();
    }

    /** "Slide 3" 같은 추출기 기본 슬라이드 헤딩. */
    public static boolean isGenericSlideHeading(String heading) {
        return heading != null && GENERIC_SLIDE_HEADING.matcher(heading.trim()).matches();
    }
}

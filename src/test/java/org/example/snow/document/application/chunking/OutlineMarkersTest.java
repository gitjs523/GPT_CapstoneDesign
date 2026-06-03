package org.example.snow.document.application.chunking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutlineMarkersTest {

    // 불릿 글리프 (소스 ASCII 유지용 이스케이프)
    private static final String TRIANGLE = "➢"; // ➢
    private static final String CIRCLE = "○";   // ○
    private static final String SQUARE = "■";   // ■
    private static final String MIDDOT = "·";   // ·
    private static final String CIRCLED_ONE = "①"; // ①

    // ─── startsWithMarker ───

    @Test
    void 번호_불릿_원숫자_영문은_마커로_인식() {
        assertThat(OutlineMarkers.startsWithMarker("1. 정의")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker("1) 정의")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker("1.2.3 세부")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker(TRIANGLE + " Root 사용자")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker(CIRCLE + " 가격 모델")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker(MIDDOT + " 경제학은")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker(CIRCLED_ONE + " 최소비용")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker("A. 개요")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker("o 게임서버")).isTrue();
        assertThat(OutlineMarkers.startsWithMarker("1주차 경제학의 개념")).isTrue();
    }

    @Test
    void 일반_문장이나_영단어는_마커가_아니다() {
        assertThat(OutlineMarkers.startsWithMarker("AWS IAM")).isFalse();
        assertThat(OutlineMarkers.startsWithMarker("online 서비스")).isFalse();
        assertThat(OutlineMarkers.startsWithMarker("경제학은 재화와 서비스의 생산")).isFalse();
        assertThat(OutlineMarkers.startsWithMarker("")).isFalse();
        assertThat(OutlineMarkers.startsWithMarker(null)).isFalse();
    }

    // ─── stripLeadingMarker ───

    @Test
    void 마커_접두사를_제거한다() {
        assertThat(OutlineMarkers.stripLeadingMarker("1. AWS EC2 기초")).isEqualTo("AWS EC2 기초");
        assertThat(OutlineMarkers.stripLeadingMarker(TRIANGLE + " Root 사용자")).isEqualTo("Root 사용자");
        assertThat(OutlineMarkers.stripLeadingMarker("o 게임서버")).isEqualTo("게임서버");
        assertThat(OutlineMarkers.stripLeadingMarker("3) 미시경제학")).isEqualTo("미시경제학");
    }

    @Test
    void 마커가_없으면_원문을_유지한다() {
        assertThat(OutlineMarkers.stripLeadingMarker("AWS IAM")).isEqualTo("AWS IAM");
        assertThat(OutlineMarkers.stripLeadingMarker("online 서비스")).isEqualTo("online 서비스");
    }

    @Test
    void 번호_접두사가_다른_헤딩도_제거후_동일해진다() {
        // divider "1. AWS EC2 기초" 와 내용 "AWS EC2 기초" 병합 판정 근거
        String a = OutlineMarkers.stripLeadingMarker("1. AWS EC2 기초");
        String b = OutlineMarkers.stripLeadingMarker("AWS EC2 기초");
        assertThat(a).isEqualTo(b);
    }

    // ─── endsWithSentencePunctuation ───

    @Test
    void 문장_종결_여부() {
        assertThat(OutlineMarkers.endsWithSentencePunctuation("가격이 초 단위로 결정된다.")).isTrue();
        assertThat(OutlineMarkers.endsWithSentencePunctuation("무엇인가?")).isTrue();
        assertThat(OutlineMarkers.endsWithSentencePunctuation("AWS IAM")).isFalse();
        assertThat(OutlineMarkers.endsWithSentencePunctuation("750개가 넘는 인스턴스 및")).isFalse();
    }

    // ─── isNumberedHeading ───

    @Test
    void 번호_헤딩_인식() {
        assertThat(OutlineMarkers.isNumberedHeading("1. 경제학이란 무엇인가?")).isTrue();
        assertThat(OutlineMarkers.isNumberedHeading("1주차 경제학의 개념")).isTrue();
        assertThat(OutlineMarkers.isNumberedHeading("AWS IAM")).isFalse();
    }

    // ─── isShortTitleLine ───

    @Test
    void 짧은_제목줄_판정() {
        assertThat(OutlineMarkers.isShortTitleLine("AWS IAM")).isTrue();
        assertThat(OutlineMarkers.isShortTitleLine("경제학의 구분")).isTrue();
        assertThat(OutlineMarkers.isShortTitleLine("가격이 초 단위로 결정된다.")).isFalse(); // 문장
        assertThat(OutlineMarkers.isShortTitleLine(
                "이 문장은 마흔 글자를 충분히 넘기는 아주 긴 설명 문장이라서 제목으로 보지 않는다 정말로")).isFalse();
    }

    // ─── generic page/slide ───

    @Test
    void 추출기_기본_헤딩_인식() {
        assertThat(OutlineMarkers.isGenericPageHeading("Page 12")).isTrue();
        assertThat(OutlineMarkers.isGenericPageHeading("페이지 내용")).isFalse();
        assertThat(OutlineMarkers.isGenericSlideHeading("Slide 3")).isTrue();
        assertThat(OutlineMarkers.isGenericSlideHeading("슬라이드 제목")).isFalse();
    }
}

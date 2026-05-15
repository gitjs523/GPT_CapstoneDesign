package org.example.snow.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "DOC_001", "업로드할 파일이 필요합니다."),
    UNSUPPORTED_DOCUMENT_TYPE(HttpStatus.BAD_REQUEST, "DOC_002", "지원하지 않는 파일 형식입니다. PDF, PPT, PPTX만 업로드할 수 있습니다."),
    PAGE_CHUNK_STRATEGY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DOC_003", "PAGE 청킹은 PDF 문서에서만 사용할 수 있습니다."),
    SLIDE_CHUNK_STRATEGY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DOC_004", "SLIDE 청킹은 PPT/PPTX 문서에서만 사용할 수 있습니다."),
    PDF_TEXT_EXTRACTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DOC_005", "PDF 텍스트 추출에 실패했습니다."),
    POWERPOINT_TEXT_EXTRACTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DOC_006", "PowerPoint 텍스트 추출에 실패했습니다."),
    DOCUMENT_PREPROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DOC_007", "문서 전처리에 실패했습니다."),
    UPLOADED_FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DOC_008", "업로드 파일을 읽는 중 오류가 발생했습니다."),
    NOTEBOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "NB_001", "노트북을 찾을 수 없습니다."),
    NOTEBOOK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "NB_002", "해당 노트북에 접근할 권한이 없습니다."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOC_010", "문서를 찾을 수 없습니다."),
    DOCUMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DOC_011", "해당 문서에 접근할 권한이 없습니다."),
    DOCUMENT_ANALYZING(HttpStatus.CONFLICT, "DOC_012", "분석 중인 문서는 삭제할 수 없습니다. 분석 완료 후 삭제해주세요."),
    DOCUMENT_NOT_COMPLETED(HttpStatus.CONFLICT, "DOC_013", "문서 분석이 완료된 후에 조회할 수 있습니다."),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "DOC_014", "섹션을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_003", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_004", "접근 권한이 없습니다."),
    REFRESH_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_005", "리프레시 토큰이 필요합니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_006", "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_007", "만료된 리프레시 토큰입니다."),
    AI_RESPONSE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 응답 생성에 실패했습니다."),
    AI_RESPONSE_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI_002", "AI 응답을 해석하는 데 실패했습니다."),
    EMBEDDING_MODEL_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMB_001", "임베딩 모델 호출에 실패했습니다."),
    GENERATION_CONTEXT_NOT_FOUND(HttpStatus.CONFLICT, "AI_003", "문제 생성을 위한 문서 근거를 찾지 못했습니다."),
    GENERATION_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "AI_004", "생성 작업을 찾을 수 없습니다."),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "AI_005", "퀴즈를 찾을 수 없습니다."),
    QUIZ_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AI_006", "해당 퀴즈에 접근할 권한이 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}

package org.example.snow.global.text;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 영속화 직전 최후 방어선 — String 컬럼에 @Convert로 적용하면 저장 시점에 제어문자를 정화한다.
 *
 * TextPreprocessor를 거치지 않는 텍스트(생성 모델 출력, 사용자 입력, 서버 생성 에러 메시지 등)가
 * U+0000(NUL)을 포함한 채 DB에 들어가 PostgreSQL INSERT를 실패시키는 것을 막는다.
 * autoApply는 하지 않으며, 위험 컬럼에만 명시적으로 부착한다.
 *
 * 읽기(convertToEntityAttribute)는 통과시킨다 — 저장 시 이미 정화되므로 재처리 불필요.
 */
@Converter
public class ControlCharSanitizingConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return ControlCharSanitizer.sanitize(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData;
    }
}

package org.example.snow.ai.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class FloatArrayConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < attribute.length; i++) {
            sb.append(attribute[i]);
            if (i < attribute.length - 1) {
                sb.append(",");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.length() < 2) return new float[0];

        String cleaned = dbData.replace("[", "").replace("]", "");
        String[] parts = cleaned.split(",");

        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }

        return result;
    }
}
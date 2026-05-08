package org.example.snow.document.web.dto;

import org.example.snow.document.domain.Section;

import java.util.List;

public record SectionResponse(
        Long sectionId,
        int sectionOrder,
        String heading,
        String content,
        int sourceStartIndex,
        int sourceEndIndex,
        List<Integer> sourceIndices
) {
    public static SectionResponse from(Section section) {
        return new SectionResponse(
                section.getSectionId(),
                section.getSectionOrder(),
                section.getHeading(),
                section.getContent(),
                section.getSourceStartIndex(),
                section.getSourceEndIndex(),
                section.getSourceIndices()
        );
    }
}

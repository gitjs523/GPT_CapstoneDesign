package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ChunkStrategyResolver {

    public ChunkStrategy resolve(SourceUnitType sourceType, ChunkStrategy requestedStrategy) {
        ChunkStrategy strategy = requestedStrategy == null ? ChunkStrategy.AUTO : requestedStrategy;

        if (strategy == ChunkStrategy.AUTO) {
            return ChunkStrategy.SECTION;
        }

        if (strategy == ChunkStrategy.PAGE && sourceType != SourceUnitType.PAGE) {
            throw new BusinessException(ErrorCode.PAGE_CHUNK_STRATEGY_NOT_ALLOWED);
        }

        if (strategy == ChunkStrategy.SLIDE && sourceType != SourceUnitType.SLIDE) {
            throw new BusinessException(ErrorCode.SLIDE_CHUNK_STRATEGY_NOT_ALLOWED);
        }

        return strategy;
    }
}

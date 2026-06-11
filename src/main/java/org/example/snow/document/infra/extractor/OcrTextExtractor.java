package org.example.snow.document.infra.extractor;

import java.awt.image.BufferedImage;

@FunctionalInterface
interface OcrTextExtractor {

    String extractText(BufferedImage image);
}

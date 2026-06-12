package org.example.snow.document.infra.extractor;

import java.awt.image.BufferedImage;

@FunctionalInterface
interface VisualContentAnalyzer {

    String analyze(BufferedImage image);
}

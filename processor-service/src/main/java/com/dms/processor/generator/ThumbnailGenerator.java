package com.dms.processor.generator;

import java.awt.image.BufferedImage;

public interface ThumbnailGenerator {
    BufferedImage generateThumbnail(String content);
}

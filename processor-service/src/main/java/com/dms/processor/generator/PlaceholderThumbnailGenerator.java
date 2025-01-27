package com.dms.processor.generator;

import com.dms.processor.enums.DocumentType;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PlaceholderThumbnailGenerator extends BaseThumbnailGenerator {

    public PlaceholderThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    public BufferedImage generateThumbnail(DocumentType type) {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        // Draw placeholder text
        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        String text = type != null ? type.getDisplayName() : "Unknown Type";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}
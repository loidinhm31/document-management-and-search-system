package com.dms.processor.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class TextThumbnailGenerator extends BaseThumbnailGenerator {

    public TextThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    public BufferedImage generateThumbnail(String content) {
        if (content == null || content.trim().isEmpty()) {
            return createEmptyThumbnail();
        }

        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        drawTextContent(g2d, content);
        g2d.dispose();

        return image;
    }

    private void drawTextContent(Graphics2D g2d, String content) {
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Monospace", Font.PLAIN, 12));

        String[] lines = content.split("\n");
        int y = 45;
        int leftMargin = 15;
        int maxLines = 20;

        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                String truncatedLine = truncateLine(line, 42);
                g2d.drawString(truncatedLine, leftMargin, y);
                y += 20;
            }
        }
    }

    private BufferedImage createEmptyThumbnail() {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Empty Text File";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}
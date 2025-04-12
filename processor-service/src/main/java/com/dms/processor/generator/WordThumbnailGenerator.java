package com.dms.processor.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class WordThumbnailGenerator extends BaseThumbnailGenerator implements ThumbnailGenerator {

    public WordThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    @Override
    public BufferedImage generateThumbnail(String content) {
        if (content == null || content.trim().isEmpty()) {
            return createEmptyThumbnail();
        }

        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        drawDocumentContent(g2d, content);
        g2d.dispose();

        return image;
    }

    private void drawDocumentContent(Graphics2D g2d, String content) {
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));

        String[] lines = content.split("\n");
        int y = 40;
        int leftMargin = 30;
        int maxLines = 20;

        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = truncateLine(lines[i].trim(), 45);
            if (!line.isEmpty()) {
                g2d.drawString(line, leftMargin, y);
                y += 18;
            }
        }
    }

    private BufferedImage createEmptyThumbnail() {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Empty Document";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}
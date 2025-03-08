package com.dms.processor.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class PowerPointThumbnailGenerator extends BaseThumbnailGenerator implements ThumbnailGenerator {

    public PowerPointThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    @Override
    public BufferedImage generateThumbnail(String content) {
        if (content == null || content.trim().isEmpty()) {
            return createEmptyThumbnail();
        }

        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        drawSlideContent(g2d, content);
        g2d.dispose();

        return image;
    }

    private void drawSlideContent(Graphics2D g2d, String content) {
        // Draw slide background
        g2d.setColor(new Color(245, 245, 245));
        g2d.fillRect(0, 0, width, height);

        // Draw slide border
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawRect(0, 0, width - 1, height - 1);

        String[] lines = content.split("\n");
        String title = lines.length > 0 ? lines[0].trim() : "";

        // Draw title area
        drawTitleArea(g2d, title);

        // Draw content
        drawBulletPoints(g2d, lines);

        // Draw slide number
        drawSlideNumber(g2d);
    }

    private void drawTitleArea(Graphics2D g2d, String title) {
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(20, 20, width - 40, 40);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString(truncateLine(title, 35), 25, 45);
    }

    private void drawBulletPoints(Graphics2D g2d, String[] lines) {
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        int y = 80;
        int leftMargin = 25;
        int maxLines = 8;

        for (int i = 1; i < Math.min(lines.length, maxLines + 1); i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                String bulletPoint = truncateLine(line, 45);
                g2d.drawString("• " + bulletPoint, leftMargin, y);
                y += 20;
            }
        }
    }

    private void drawSlideNumber(Graphics2D g2d) {
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        g2d.setColor(Color.GRAY);
        g2d.drawString("Slide 1", width - 50, height - 15);
    }

    private BufferedImage createEmptyThumbnail() {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Empty Presentation";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}
package com.dms.processor.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class MarkdownThumbnailGenerator extends BaseThumbnailGenerator implements ThumbnailGenerator {

    public MarkdownThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    @Override
    public BufferedImage generateThumbnail(String content) {
        if (content == null || content.trim().isEmpty()) {
            return createEmptyThumbnail();
        }

        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        drawMarkdownContent(g2d, content);
        g2d.dispose();

        return image;
    }

    private void drawMarkdownContent(Graphics2D g2d, String content) {
        // Set background
        g2d.setColor(new Color(250, 250, 250));
        g2d.fillRect(0, 0, width, height);

        String[] lines = content.split("\n");
        int y = 40;
        int leftMargin = 20;
        int maxLines = 12;

        g2d.setColor(Color.BLACK);

        // Process and draw each line
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                // Format line based on Markdown syntax
                drawFormattedLine(g2d, line, leftMargin, y);
                y += 20;
            }
        }

        if (lines.length > maxLines) {
            g2d.setColor(Color.GRAY);
            g2d.drawString("...", leftMargin, y);
        }
    }

    private void drawFormattedLine(Graphics2D g2d, String line, int x, int y) {
        // Handle headers
        if (line.startsWith("#")) {
            int level = 1;
            while (line.charAt(level) == '#') {
                level++;
            }
            line = line.substring(level).trim();

            int fontSize = Math.max(16 - level, 11);
            g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
            g2d.setColor(new Color(40, 40, 40));
            g2d.drawString(truncateLine(line, 35), x, y);
            return;
        }

        // Handle lists
        if (line.startsWith("-") || line.startsWith("*")) {
            line = "•" + line.substring(1);
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));
            g2d.setColor(Color.BLACK);
            g2d.drawString(truncateLine(line, 40), x, y);
            return;
        }

        // Handle numbered lists
        if (line.matches("^\\d+\\..*")) {
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));
            g2d.setColor(Color.BLACK);
            g2d.drawString(truncateLine(line, 40), x, y);
            return;
        }

        // Handle blockquotes
        if (line.startsWith(">")) {
            line = line.substring(1).trim();
            g2d.setFont(new Font("Arial", Font.ITALIC, 11));
            g2d.setColor(new Color(70, 70, 70));
            g2d.drawString(truncateLine(line, 40), x + 10, y);
            return;
        }

        // Regular text
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        g2d.setColor(Color.BLACK);
        g2d.drawString(truncateLine(line, 45), x, y);
    }

    private BufferedImage createEmptyThumbnail() {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Empty Markdown Document";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}
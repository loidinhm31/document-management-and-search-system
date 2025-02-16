package com.dms.processor.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class JsonThumbnailGenerator extends BaseThumbnailGenerator {

    public JsonThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    public BufferedImage generateThumbnail(String content) {
        if (content == null || content.trim().isEmpty()) {
            return createEmptyThumbnail();
        }

        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        drawJsonContent(g2d, content);
        g2d.dispose();

        return image;
    }

    private void drawJsonContent(Graphics2D g2d, String content) {
        // Setup colors
        Color keyColor = new Color(102, 0, 102);    // Purple for keys
        Color stringColor = new Color(0, 128, 0);   // Green for string values
        Color numberColor = new Color(0, 0, 255);   // Blue for number values
        Color bracesColor = Color.BLACK;            // Black for braces and commas

        // Draw background for better readability
        g2d.setColor(new Color(250, 250, 250));
        g2d.fillRect(0, 0, width, height);

        // Setup font
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));
        FontMetrics fm = g2d.getFontMetrics();

        String[] lines = formatJsonLines(content);
        int y = 40;
        int leftMargin = 20;
        int maxLines = 12;

        // Draw a title
        g2d.setColor(Color.DARK_GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // Draw content lines
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                // Handle different JSON elements with different colors
                if (line.contains(":")) {
                    // Key-value pair
                    String[] parts = line.split(":", 2);
                    int xPos = leftMargin;

                    // Draw key
                    g2d.setColor(keyColor);
                    String key = parts[0].trim().replaceAll("\"", "");
                    g2d.drawString(key + ":", xPos, y);
                    xPos += fm.stringWidth(key + ": ");

                    // Draw value
                    String value = parts[1].trim().replaceAll("[,}\\]]$", "");
                    if (value.startsWith("\"")) {
                        g2d.setColor(stringColor);
                    } else {
                        g2d.setColor(numberColor);
                    }
                    g2d.drawString(truncateLine(value, 30), xPos, y);
                } else {
                    // Braces, brackets, or commas
                    g2d.setColor(bracesColor);
                    g2d.drawString(line, leftMargin, y);
                }
                y += 18;
            }
        }

        if (lines.length > maxLines) {
            g2d.setColor(Color.GRAY);
            g2d.drawString("...", leftMargin, y);
        }
    }

    private String[] formatJsonLines(String content) {
        // Basic JSON formatting - split on braces and commas
        return content.replaceAll("\\{", "{\n")
                .replaceAll("\\}", "\n}")
                .replaceAll(",", ",\n")
                .split("\n");
    }

    private BufferedImage createEmptyThumbnail() {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Empty JSON Document";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}
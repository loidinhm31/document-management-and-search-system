package com.dms.processor.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class XmlThumbnailGenerator extends BaseThumbnailGenerator implements ThumbnailGenerator {

    public XmlThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    @Override
    public BufferedImage generateThumbnail(String content) {
        if (content == null || content.trim().isEmpty()) {
            return createEmptyThumbnail();
        }

        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        drawXmlContent(g2d, content);
        g2d.dispose();

        return image;
    }

    private void drawXmlContent(Graphics2D g2d, String content) {
        // Setup colors
        Color tagColor = new Color(128, 0, 0);      // Dark red for tags
        Color attributeColor = new Color(0, 0, 128); // Dark blue for attributes
        Color valueColor = new Color(0, 128, 0);     // Green for values
        Color textColor = Color.BLACK;               // Black for text content

        // Draw background for better readability
        g2d.setColor(new Color(250, 250, 250));
        g2d.fillRect(0, 0, width, height);

        // Setup font
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));
        FontMetrics fm = g2d.getFontMetrics();

        String[] lines = formatXmlLines(content);
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
                int xPos = leftMargin;

                // Add indentation based on nesting level
                int indent = line.indexOf("<");
                xPos += (indent > 0 ? indent * 4 : 0);

                if (line.startsWith("<?") || line.startsWith("<!")) {
                    // XML declaration or DOCTYPE
                    g2d.setColor(tagColor);
                    g2d.drawString(truncateLine(line, 40), xPos, y);
                } else if (line.startsWith("</") || line.startsWith("<")) {
                    // XML tags
                    drawXmlTag(g2d, line, xPos, y, tagColor, attributeColor);
                } else {
                    // Text content
                    g2d.setColor(valueColor);
                    g2d.drawString(truncateLine(line, 40), xPos, y);
                }
                y += 18;
            }
        }

        if (lines.length > maxLines) {
            g2d.setColor(Color.GRAY);
            g2d.drawString("...", leftMargin, y);
        }
    }

    private void drawXmlTag(Graphics2D g2d, String tag, int x, int y,
                            Color tagColor, Color attributeColor) {
        // Split tag into name and attributes
        int attributeStart = tag.indexOf(" ");
        if (attributeStart == -1) {
            // No attributes
            g2d.setColor(tagColor);
            g2d.drawString(truncateLine(tag, 40), x, y);
            return;
        }

        // Draw tag name
        g2d.setColor(tagColor);
        String tagName = tag.substring(0, attributeStart) + ">";
        g2d.drawString(tagName, x, y);

        // Draw attributes
        g2d.setColor(attributeColor);
        String attributes = tag.substring(attributeStart, tag.length() - 1);
        if (attributes.length() > 30) {
            attributes = attributes.substring(0, 27) + "...";
        }
        g2d.drawString(attributes + ">", x + g2d.getFontMetrics().stringWidth(tagName) - 1, y);
    }

    private String[] formatXmlLines(String content) {
        // Basic XML formatting - split on tags and normalize whitespace
        return content.replaceAll(">\\s*<", ">\n<")
                .replaceAll("^\\s+|\\s+$", "")
                .split("\n");
    }

    private BufferedImage createEmptyThumbnail() {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Empty XML Document";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}
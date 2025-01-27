package com.dms.processor.generator;

import lombok.RequiredArgsConstructor;

import java.awt.*;
import java.awt.image.BufferedImage;

@RequiredArgsConstructor
public abstract class BaseThumbnailGenerator {
    protected final int width;
    protected final int height;

    protected static final int DEFAULT_CELL_WIDTH = 52;
    protected static final int DEFAULT_CELL_HEIGHT = 25;
    protected static final int DEFAULT_START_X = 15;
    protected static final int DEFAULT_START_Y = 25;
    protected static final int DEFAULT_COLUMNS = 5;
    protected static final int DEFAULT_ROWS = 6;

    protected BufferedImage createBaseImage() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        setupGraphics(g2d);
        return image;
    }

    protected void setupGraphics(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
    }

    protected String truncateLine(String line, int maxLength) {
        if (line.length() > maxLength) {
            return line.substring(0, maxLength - 3) + "...";
        }
        return line;
    }

    protected void drawGrid(Graphics2D g2d, int startX, int startY, int cols, int rows, int cellWidth, int cellHeight) {
        g2d.setColor(new Color(220, 220, 220));

        // Draw vertical lines
        for (int i = 0; i <= cols; i++) {
            int x = startX + (i * cellWidth);
            g2d.drawLine(x, startY, x, startY + (rows * cellHeight));
        }

        // Draw horizontal lines
        for (int i = 0; i <= rows; i++) {
            int y = startY + (i * cellHeight);
            g2d.drawLine(startX, y, startX + (cols * cellWidth), y);
        }
    }

    protected void drawGridHeaders(Graphics2D g2d, int startX, int startY, int cols, int rows, int cellWidth, int cellHeight) {
        g2d.setColor(new Color(100, 100, 100));
        g2d.setFont(new Font("Arial", Font.BOLD, 11));

        // Draw column headers (A, B, C, etc.)
        for (int i = 0; i < cols; i++) {
            String colHeader = String.valueOf((char)('A' + i));
            int x = startX + (i * cellWidth) + (cellWidth / 3);
            g2d.drawString(colHeader, x, startY - 5);
        }

        // Draw row headers (1, 2, 3, etc.)
        for (int i = 0; i < rows; i++) {
            String rowHeader = String.valueOf(i + 1);
            int y = startY + (i * cellHeight) + (cellHeight / 2) + 5;
            g2d.drawString(rowHeader, startX - 15, y);
        }
    }
}
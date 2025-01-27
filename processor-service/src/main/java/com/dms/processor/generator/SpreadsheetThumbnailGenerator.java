package com.dms.processor.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class SpreadsheetThumbnailGenerator extends BaseThumbnailGenerator {

    public SpreadsheetThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    public BufferedImage generateThumbnail(String content) {
        if (content == null || content.trim().isEmpty()) {
            return createEmptyThumbnail();
        }

        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        setupGraphics(g2d);
        drawSpreadsheetContent(g2d, content);
        g2d.dispose();

        return image;
    }

    private void drawSpreadsheetContent(Graphics2D g2d, String content) {
        int startY = DEFAULT_START_Y + 25; // Adjust for sheet tab

        // Draw sheet name tab
        drawSheetTab(g2d, "Sheet1");

        // Draw grid
        drawGrid(g2d, DEFAULT_START_X, startY, DEFAULT_COLUMNS, DEFAULT_ROWS, DEFAULT_CELL_WIDTH, DEFAULT_CELL_HEIGHT);
        drawGridHeaders(g2d, DEFAULT_START_X, startY, DEFAULT_COLUMNS, DEFAULT_ROWS, DEFAULT_CELL_WIDTH, DEFAULT_CELL_HEIGHT);

        // Parse and draw content
        drawCellContent(g2d, content, startY);
    }

    private void drawSheetTab(Graphics2D g2d, String sheetName) {
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(DEFAULT_START_X, 5, 60, 20);
        g2d.setColor(new Color(180, 180, 180));
        g2d.drawRect(DEFAULT_START_X, 5, 60, 20);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        g2d.drawString(sheetName, DEFAULT_START_X + 10, 20);
    }

    private void drawCellContent(Graphics2D g2d, String content, int startY) {
        String[] lines = content.split("\n");
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        final int maxCharsPerCell = 10;
        final int cellPadding = 5;

        for (int i = 0; i < Math.min(lines.length, DEFAULT_ROWS); i++) {
            String[] cells = lines[i].split("\t", -1);
            for (int j = 0; j < Math.min(cells.length, DEFAULT_COLUMNS); j++) {
                String cellContent = cells[j].trim();
                if (!cellContent.isEmpty()) {
                    cellContent = truncateLine(cellContent, maxCharsPerCell);
                    int x = DEFAULT_START_X + (j * DEFAULT_CELL_WIDTH) + cellPadding;
                    int y = startY + (i * DEFAULT_CELL_HEIGHT) + (DEFAULT_CELL_HEIGHT / 2) + 5;
                    g2d.drawString(cellContent, x, y);
                }
            }
        }
    }

    private BufferedImage createEmptyThumbnail() {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Empty Spreadsheet";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}
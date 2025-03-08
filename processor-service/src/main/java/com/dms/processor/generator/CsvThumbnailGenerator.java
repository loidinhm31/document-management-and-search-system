package com.dms.processor.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class CsvThumbnailGenerator extends BaseThumbnailGenerator implements ThumbnailGenerator {

    public CsvThumbnailGenerator(int width, int height) {
        super(width, height);
    }

    @Override
    public BufferedImage generateThumbnail(String content) {
        if (content == null || content.trim().isEmpty()) {
            return createEmptyThumbnail();
        }

        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        drawCsvContent(g2d, content);
        g2d.dispose();

        return image;
    }

    private void drawCsvContent(Graphics2D g2d, String content) {
        // Draw grid
        drawGrid(g2d, DEFAULT_START_X, DEFAULT_START_Y, DEFAULT_COLUMNS, DEFAULT_ROWS,
                DEFAULT_CELL_WIDTH, DEFAULT_CELL_HEIGHT);

        // Draw headers
        drawGridHeaders(g2d, DEFAULT_START_X, DEFAULT_START_Y, DEFAULT_COLUMNS, DEFAULT_ROWS,
                DEFAULT_CELL_WIDTH, DEFAULT_CELL_HEIGHT);

        // Draw content
        drawCellContent(g2d, content);
    }

    private void drawCellContent(Graphics2D g2d, String content) {
        String[] lines = content.split("\n");
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));

        for (int i = 0; i < Math.min(lines.length, DEFAULT_ROWS); i++) {
            String[] cells = lines[i].split(",", -1);
            for (int j = 0; j < Math.min(cells.length, DEFAULT_COLUMNS); j++) {
                String cellContent = truncateLine(cells[j].trim(), 8);
                int x = DEFAULT_START_X + (j * DEFAULT_CELL_WIDTH) + 3;
                int y = DEFAULT_START_Y + (i * DEFAULT_CELL_HEIGHT) + (DEFAULT_CELL_HEIGHT / 2) + 5;
                g2d.drawString(cellContent, x, y);
            }
        }
    }

    private BufferedImage createEmptyThumbnail() {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Empty CSV";
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (width - textWidth) / 2, height / 2);

        g2d.dispose();
        return image;
    }
}

package com.dms.document.service;

import com.dms.document.enums.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;


@Service
@Slf4j
@RequiredArgsConstructor
public class ThumbnailService {

    @Value("${app.document.thumbnail.width:300}")
    private int thumbnailWidth;

    @Value("${app.document.thumbnail.height:200}")
    private int thumbnailHeight;

    public byte[] generateThumbnail(Path filePath, DocumentType documentType, String content) throws IOException {
        // Generate thumbnail based on document type
        BufferedImage thumbnail = switch (documentType) {
            case PDF -> generatePdfThumbnail(filePath);
            case WORD, WORD_DOCX -> generateWordThumbnail(documentType, content);
            case TEXT_PLAIN -> generateTextThumbnail(documentType, content);
            case CSV, EXCEL, EXCEL_XLSX -> generateSpreadsheetThumbnail(documentType, content);
            case POWERPOINT, POWERPOINT_PPTX -> generatePowerPointThumbnail(documentType, content);
            default -> generateDefaultThumbnail(documentType);
        };

        // Resize thumbnail
        BufferedImage resized = resizeImage(thumbnail, thumbnailWidth, thumbnailHeight);

        // Convert to bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resized, "PNG", baos);
        return baos.toByteArray();
    }

    private BufferedImage generatePdfThumbnail(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            return pdfRenderer.renderImageWithDPI(0, 72); // Render first page at 72 DPI
        }
    }

    private BufferedImage generateWordThumbnail(DocumentType documentType, String content) {
        if (content == null || content.trim().isEmpty()) {
            return createPlaceholderThumbnail(documentType);
        }
        // Create base image
        BufferedImage image = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw white background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, thumbnailWidth, thumbnailHeight);

        // Draw content preview
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));

        String[] lines = content.split("\n");
        int y = 40; // Start from top with margin
        int leftMargin = 30; // Left margin
        int maxLines = 20; // Maximum lines to show

        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                // Limit line length
                if (line.length() > 45) {
                    line = line.substring(0, 42) + "...";
                }
                g2d.drawString(line, leftMargin, y);
                y += 18; // Space between lines
            }
        }

        g2d.dispose();
        return image;
    }

    private BufferedImage generateTextThumbnail(DocumentType documentType, String content) {
        if (content == null || content.trim().isEmpty()) {
            return createPlaceholderThumbnail(documentType);
        }

        // Create base image
        BufferedImage image = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw white background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, thumbnailWidth, thumbnailHeight);

        // Draw content preview
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Monospace", Font.PLAIN, 12)); // Increased font size for better readability

        String[] lines = content.split("\n");
        int y = 45; // Start below the title
        int leftMargin = 15;
        int maxLines = 20; // Adjusted for larger font

        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                // Limit line length
                if (line.length() > 42) { // Adjusted for larger font
                    line = line.substring(0, 39) + "...";
                }
                g2d.drawString(line, leftMargin, y);
                y += 20; // Increased spacing for better readability
            }
        }

        g2d.dispose();
        return image;
    }


    private BufferedImage generateSpreadsheetThumbnail(DocumentType documentType, String content) {
        if (content == null || content.trim().isEmpty()) {
            return createPlaceholderThumbnail(documentType);
        }

        // Create base image
        BufferedImage image = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw white background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, thumbnailWidth, thumbnailHeight);

        // Define grid properties
        int cellWidth = 52;
        int cellHeight = 25;
        int startX = 15;
        int startY = 35; // Adjusted to accommodate title
        int cols = 5;
        int rows = 6;

        // Draw grid
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

        // Draw column headers (A, B, C, etc.)
        g2d.setColor(new Color(100, 100, 100));
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
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

        // Parse and draw content
        String[] lines = content.split("\n");
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));

        for (int i = 0; i < Math.min(lines.length, rows); i++) {
            String[] cells = lines[i].split(",", -1);
            for (int j = 0; j < Math.min(cells.length, cols); j++) {
                String cellContent = cells[j].trim();
                if (cellContent.length() > 8) {
                    cellContent = cellContent.substring(0, 6) + "...";
                }
                int x = startX + (j * cellWidth) + 3;
                int y = startY + (i * cellHeight) + (cellHeight / 2) + 5;
                g2d.drawString(cellContent, x, y);
            }
        }

        g2d.dispose();
        return image;
    }

    private BufferedImage generatePowerPointThumbnail(DocumentType documentType, String content) {
        if (content == null || content.trim().isEmpty()) {
            return createPlaceholderThumbnail(documentType);
        }

        // Create base image
        BufferedImage image = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw slide background (light gray)
        g2d.setColor(new Color(245, 245, 245));
        g2d.fillRect(0, 0, thumbnailWidth, thumbnailHeight);

        // Draw slide border
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawRect(0, 0, thumbnailWidth - 1, thumbnailHeight - 1);

        // Parse content for title and text
        String[] lines = content.split("\n");
        String title = lines.length > 0 ? lines[0].trim() : "";

        // Draw title area
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(20, 20, thumbnailWidth - 40, 40);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));

        // Draw title with ellipsis if too long
        if (title.length() > 35) {
            title = title.substring(0, 32) + "...";
        }
        g2d.drawString(title, 25, 45);

        // Draw content preview
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        int y = 80;
        int leftMargin = 25;
        int maxLines = 8; // Fewer lines than Word to match PowerPoint style

        for (int i = 1; i < Math.min(lines.length, maxLines + 1); i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                // Add bullet point and limit line length
                if (line.length() > 45) {
                    line = line.substring(0, 42) + "...";
                }
                g2d.drawString("• " + line, leftMargin, y);
                y += 20; // More spacing between lines for PowerPoint style
            }
        }

        // Draw slide number indicator
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        g2d.setColor(Color.GRAY);
        g2d.drawString("Slide 1", thumbnailWidth - 50, thumbnailHeight - 15);

        g2d.dispose();
        return image;
    }

    private BufferedImage generateDefaultThumbnail(DocumentType type) {
        return createPlaceholderThumbnail(type);
    }

    private BufferedImage createPlaceholderThumbnail(DocumentType type) {
        BufferedImage image = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Fill background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, thumbnailWidth, thumbnailHeight);

        // Draw text
        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        String text = type.getDisplayName();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (thumbnailWidth - textWidth) / 2, thumbnailHeight / 2);

        g2d.dispose();
        return image;
    }

    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, original.getType());
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }
}
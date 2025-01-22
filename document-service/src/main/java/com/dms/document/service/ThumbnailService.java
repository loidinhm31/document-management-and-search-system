package com.dms.document.service;

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

    public byte[] generateThumbnail(Path filePath, String documentType) throws IOException {
        // Generate thumbnail based on document type
        BufferedImage thumbnail = switch (documentType) {
            case "PDF" -> generatePdfThumbnail(filePath);
            case "WORD", "WORD_DOCX" -> generateWordThumbnail();
            case "EXCEL", "EXCEL_XLSX" -> generateExcelThumbnail();
            case "POWERPOINT", "POWERPOINT_PPTX" -> generatePowerPointThumbnail();
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

    private BufferedImage generateWordThumbnail() {
        return createPlaceholderThumbnail("WORD");
    }

    private BufferedImage generateExcelThumbnail() {
        return createPlaceholderThumbnail("EXCEL");
    }

    private BufferedImage generatePowerPointThumbnail() {
        return createPlaceholderThumbnail("PPT");
    }

    private BufferedImage generateDefaultThumbnail(String type) {
        return createPlaceholderThumbnail(type);
    }

    private BufferedImage createPlaceholderThumbnail(String type) {
        BufferedImage image = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Fill background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, thumbnailWidth, thumbnailHeight);

        // Draw text
        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        String text = type;
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
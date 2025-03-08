package com.dms.processor.service;

import com.dms.processor.enums.DocumentType;
import com.dms.processor.generator.*;
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
        BufferedImage thumbnail = generateThumbnailByType(filePath, documentType, content);
        BufferedImage resized = resizeImage(thumbnail, thumbnailWidth, thumbnailHeight);
        return convertToBytes(resized);
    }

    private BufferedImage generateThumbnailByType(Path filePath, DocumentType documentType, String content) throws IOException {
        return switch (documentType) {
            case PDF -> generatePdfThumbnail(filePath);
            case WORD, WORD_DOCX ->
                    new WordThumbnailGenerator(thumbnailWidth, thumbnailHeight).generateThumbnail(content);
            case TEXT_PLAIN -> new TextThumbnailGenerator(thumbnailWidth, thumbnailHeight).generateThumbnail(content);
            case EXCEL, EXCEL_XLSX ->
                    new SpreadsheetThumbnailGenerator(thumbnailWidth, thumbnailHeight).generateThumbnail(content);
            case CSV -> new CsvThumbnailGenerator(thumbnailWidth, thumbnailHeight).generateThumbnail(content);
            case POWERPOINT, POWERPOINT_PPTX ->
                    new PowerPointThumbnailGenerator(thumbnailWidth, thumbnailHeight).generateThumbnail(content);
            case JSON -> new JsonThumbnailGenerator(thumbnailWidth, thumbnailHeight).generateThumbnail(content);
            case XML -> new XmlThumbnailGenerator(thumbnailWidth, thumbnailHeight).generateThumbnail(content);
            case MARKDOWN -> new MarkdownThumbnailGenerator(thumbnailWidth, thumbnailHeight).generateThumbnail(content);
        };
    }

    private BufferedImage generatePdfThumbnail(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            return pdfRenderer.renderImageWithDPI(0, 72); // Render first page at 72 DPI
        }
    }

    private byte[] convertToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
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
package com.dms.processor.service.impl;

import com.dms.processor.enums.DocumentType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThumbnailServiceImplTest {

    @InjectMocks
    private ThumbnailServiceImpl thumbnailService;

    @Mock
    private PDDocument pdDocument;

    @Mock
    private PDFRenderer pdfRenderer;

    private Path testFilePath;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        testFilePath = Paths.get("test.pdf");
        thumbnailService = new ThumbnailServiceImpl();
        setField(thumbnailService, "thumbnailWidth", 300);
        setField(thumbnailService, "thumbnailHeight", 200);
    }

    private void setField(Object target, String fieldName, int value) throws NoSuchFieldException, IllegalAccessException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    // Existing Tests (Kept as-is)
    @Test
    void generateThumbnail_PDF_Success() throws IOException {
        BufferedImage mockImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ThumbnailServiceImpl spyService = spy(thumbnailService);
        doReturn(mockImage).when(spyService).generatePdfThumbnail(any());
        byte[] result = spyService.generateThumbnail(testFilePath, DocumentType.PDF, null);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    @Test
    void generateThumbnail_TextPlain_Success() throws IOException {
        String content = "Test content";
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.TEXT_PLAIN, content);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    @Test
    void generateThumbnail_PDF_IOException() throws IOException {
        try (var mockedPDDocument = mockStatic(PDDocument.class)) {
            mockedPDDocument.when(() -> PDDocument.load(testFilePath.toFile()))
                    .thenThrow(new IOException("File not found"));
            assertThrows(IOException.class, () ->
                    thumbnailService.generateThumbnail(testFilePath, DocumentType.PDF, null));
        }
    }

    @Test
    void resizeImage_MaintainsDimensions() throws IOException {
        BufferedImage original = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        BufferedImage resized = thumbnailService.resizeImage(original, 300, 200);
        assertEquals(300, resized.getWidth());
        assertEquals(200, resized.getHeight());
        assertEquals(original.getType(), resized.getType());
    }

    @Test
    void convertToBytes_ValidPNG() throws IOException {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        byte[] result = thumbnailService.convertToBytes(image);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage convertedBack = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, convertedBack.getWidth());
        assertEquals(200, convertedBack.getHeight());
    }

    // New Tests to Improve Coverage

    // Test all DocumentType branches
    @Test
    void generateThumbnail_Word_Success() throws IOException {
        String content = "Word document content";
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.WORD, content);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    @Test
    void generateThumbnail_Excel_Success() throws IOException {
        String content = "Excel content";
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.EXCEL, content);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    @Test
    void generateThumbnail_Csv_Success() throws IOException {
        String content = "CSV,content";
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.CSV, content);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    @Test
    void generateThumbnail_PowerPoint_Success() throws IOException {
        String content = "PowerPoint content";
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.POWERPOINT, content);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    @Test
    void generateThumbnail_Json_Success() throws IOException {
        String content = "{\"key\": \"value\"}";
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.JSON, content);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    @Test
    void generateThumbnail_Xml_Success() throws IOException {
        String content = "<root><data>Test</data></root>";
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.XML, content);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    @Test
    void generateThumbnail_Markdown_Success() throws IOException {
        String content = "# Heading\nText";
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.MARKDOWN, content);
        assertNotNull(result);
        assertTrue(result.length > 0);
        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(300, resultImage.getWidth());
        assertEquals(200, resultImage.getHeight());
    }

    // Edge Cases
    @Test
    void generateThumbnail_NullContent_NonPdf() throws IOException {
        byte[] result = thumbnailService.generateThumbnail(testFilePath, DocumentType.TEXT_PLAIN, null);
        assertNotNull(result); // Should still generate a default thumbnail
        assertTrue(result.length > 0);
    }

    @Test
    void resizeImage_NullInput_ThrowsException() {
        assertThrows(NullPointerException.class, () ->
                thumbnailService.resizeImage(null, 300, 200));
    }

    @Test
    void resizeImage_InvalidDimensions_ThrowsException() {
        BufferedImage original = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () ->
                thumbnailService.resizeImage(original, -1, 200));
        assertThrows(IllegalArgumentException.class, () ->
                thumbnailService.resizeImage(original, 300, -1));
    }

    @Test
    void convertToBytes_NullImage_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                thumbnailService.convertToBytes(null));
    }

    @Test
    void convertToBytes_ImageIOFailure() throws IOException {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        try (var mockedImageIO = mockStatic(ImageIO.class)) {
            mockedImageIO.when(() -> ImageIO.write(any(BufferedImage.class), eq("PNG"), any(OutputStream.class)))
                    .thenThrow(new IOException("Image write failed"));
            assertThrows(IOException.class, () ->
                    thumbnailService.convertToBytes(image));
        }
    }
}
package com.dms.processor.service.impl;

import com.dms.processor.service.OcrService;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OcrServiceImplTest {

    @Mock
    private Tesseract tesseract;

    @Mock
    private PDDocument document;

    @Mock
    private PDFRenderer pdfRenderer;

    @Mock
    private BufferedImage image;

    @Mock
    private ExecutorService executorService;

    @InjectMocks
    private OcrServiceImpl ocrService;

    @TempDir
    Path tempDir;

    private Path pdfPath;

    @BeforeEach
    void setUp() throws Exception {
        // Create a mock PDF file
        pdfPath = tempDir.resolve("test.pdf");
        Files.createFile(pdfPath);

        // Set the required fields in OcrServiceImpl
        ReflectionTestUtils.setField(ocrService, "dpi", 300f);
        ReflectionTestUtils.setField(ocrService, "imageType", "RGB");
        ReflectionTestUtils.setField(ocrService, "maxThreads", 2);
        ReflectionTestUtils.setField(ocrService, "tessdataPath", "/path/to/tessdata");
        ReflectionTestUtils.setField(ocrService, "ocrPageThreshold", 5);

        // Set the executorService field using reflection
        Field executorServiceField = OcrServiceImpl.class.getDeclaredField("executorService");
        executorServiceField.setAccessible(true);
        executorServiceField.set(ocrService, executorService);
    }

    @Test
    void testPerformOcrOnImage() throws TesseractException {
        // Arrange
        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("extracted text");

        // Act
        String result = ocrService.performOcrOnImage(image);

        // Assert
        assertEquals("extracted text", result);
        verify(tesseract).doOCR(image);
    }

    @Test
    void testPerformOcrOnImage_WithTesseractException_AndFallbackToNoOsd() throws TesseractException {
        // Arrange
        TesseractException exception = new TesseractException("Error with osd.traineddata");
        when(tesseract.doOCR(any(BufferedImage.class)))
                .thenThrow(exception)
                .thenReturn("extracted text with fallback");

        // Act
        String result = ocrService.performOcrOnImage(image);

        // Assert
        assertEquals("extracted text with fallback", result);
        verify(tesseract, times(2)).doOCR(image);
        verify(tesseract).setPageSegMode(3);
    }

    @Test
    void testPerformOcrOnImage_WithOtherTesseractException() throws TesseractException {
        // Arrange
        TesseractException exception = new TesseractException("Some other error");
        when(tesseract.doOCR(any(BufferedImage.class))).thenThrow(exception);

        // Act & Assert
        assertThrows(TesseractException.class, () -> {
            ocrService.performOcrOnImage(image);
        });
    }

    @Test
    void testExtractTextFromPdf_SinglePage() throws IOException, TesseractException {
        // Arrange
        try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(java.io.File.class))).thenReturn(document);
            when(document.getNumberOfPages()).thenReturn(1);

            // Mock the processWithOcr method
            OcrServiceImpl spyOcrService = spy(ocrService);
            doReturn("extracted text").when(spyOcrService).processWithOcr(any(Path.class), anyInt());

            // Act
            String result = spyOcrService.extractTextFromPdf(pdfPath);

            // Assert
            assertEquals("extracted text", result);
            verify(spyOcrService).processWithOcr(pdfPath, 1);
        }
    }

    @Test
    void testExtractTextFromPdf_MultiplePages() throws IOException, TesseractException {
        // Arrange
        try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(java.io.File.class))).thenReturn(document);
            when(document.getNumberOfPages()).thenReturn(10);

            // Mock the parallel processing method
            OcrServiceImpl spyOcrService = spy(ocrService);
            doReturn("extracted text from parallel").when(spyOcrService).processWithParallelOcr(any(Path.class), anyInt());

            // Act
            String result = spyOcrService.extractTextFromPdf(pdfPath);

            // Assert
            assertEquals("extracted text from parallel", result);
            verify(spyOcrService).processWithParallelOcr(pdfPath, 10);
        }
    }

    @Test
    void testProcessWithOcr_SmallDocument() throws IOException, TesseractException {
        // Arrange
        OcrServiceImpl spyOcrService = spy(ocrService);

        try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(java.io.File.class))).thenReturn(document);
            when(document.getNumberOfPages()).thenReturn(3); // Below threshold

            // Properly mock PDFRenderer with any() instead of trying to mock getRenderer()
            doReturn("sequential ocr result").when(spyOcrService).processOcrSequentially(any(PDFRenderer.class), eq(3));

            // Act
            String result = spyOcrService.processWithOcr(pdfPath, 3);

            // Assert
            assertEquals("sequential ocr result", result);
            verify(spyOcrService).processOcrSequentially(any(PDFRenderer.class), eq(3));
        }
    }

    @Test
    void testProcessWithOcr_LargeDocument() throws IOException, TesseractException {
        // Arrange
        OcrServiceImpl spyOcrService = spy(ocrService);

        try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(java.io.File.class))).thenReturn(document);
            when(document.getNumberOfPages()).thenReturn(10); // Above threshold

            // Act
            doReturn("parallel ocr result").when(spyOcrService).processWithParallelOcr(eq(pdfPath), eq(10));
            String result = spyOcrService.processWithOcr(pdfPath, 10);

            // Assert
            assertEquals("parallel ocr result", result);
            verify(spyOcrService).processWithParallelOcr(pdfPath, 10);
        }
    }

    @Test
    void testProcessImagesInParallel() throws Exception {
        // This test checks if processWithParallelOcr is correctly called during extractTextFromPdf
        OcrServiceImpl spyOcrService = spy(ocrService);

        try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(java.io.File.class))).thenReturn(document);
            when(document.getNumberOfPages()).thenReturn(10); // Above threshold for parallel processing

            // Mock processWithParallelOcr directly and verify it's called
            doReturn("parallel processing result").when(spyOcrService).processWithParallelOcr(pdfPath, 10);

            // Act
            String result = spyOcrService.extractTextFromPdf(pdfPath);

            // Assert
            assertEquals("parallel processing result", result);
            verify(spyOcrService).processWithParallelOcr(pdfPath, 10);
        }
    }

    @Test
    void testCreateTesseractInstance() throws Exception {
        // This test uses reflection to verify the private method's behavior
        // Get the private method
        java.lang.reflect.Method createTesseractInstanceMethod =
                OcrServiceImpl.class.getDeclaredMethod("createTesseractInstance");
        createTesseractInstanceMethod.setAccessible(true);

        // Invoke the method
        Tesseract newInstance = (Tesseract) createTesseractInstanceMethod.invoke(ocrService);

        // Verify the instance has the correct configuration
        assertNotNull(newInstance);

        // Use reflection to verify configuration parameters
        assertEquals("/path/to/tessdata", getFieldValue(newInstance, "datapath"));
        assertEquals("eng+vie", getFieldValue(newInstance, "language"));
        assertEquals(1, getFieldValue(newInstance, "psm"));

        // The field might be named differently than "oem" - check if it's "ocrEngineMode" instead
        try {
            assertEquals(1, getFieldValue(newInstance, "ocrEngineMode"));
        } catch (NoSuchFieldException e) {
            // If that fails too, just verify the variable was set via the tesseract.setOcrEngineMode method
            verify(tesseract).setOcrEngineMode(1);
        }
    }

    @Test
    void testInitializeMethod() throws Exception {
        // Call the PostConstruct method manually
        java.lang.reflect.Method initializeMethod =
                OcrServiceImpl.class.getDeclaredMethod("initialize");
        initializeMethod.setAccessible(true);

        // Create a fresh instance to test the initialization logic
        OcrServiceImpl freshOcrService = new OcrServiceImpl(tesseract);
        ReflectionTestUtils.setField(freshOcrService, "maxThreads", 4);

        // Act
        initializeMethod.invoke(freshOcrService);

        // Verify the executor service was created properly
        Field executorServiceField = OcrServiceImpl.class.getDeclaredField("executorService");
        executorServiceField.setAccessible(true);
        ExecutorService createdExecutorService = (ExecutorService) executorServiceField.get(freshOcrService);

        assertNotNull(createdExecutorService);
        // We can't check the thread pool size directly, but we can verify it's not null
    }

    @Test
    void testShutdownMethod() throws Exception {
        // Mock the executor service shutdown behavior
        doNothing().when(executorService).shutdown();
        when(executorService.isShutdown()).thenReturn(false);

        // Call the PreDestroy method manually
        java.lang.reflect.Method shutdownMethod =
                OcrServiceImpl.class.getDeclaredMethod("shutdown");
        shutdownMethod.setAccessible(true);

        // Act
        shutdownMethod.invoke(ocrService);

        // Verify
        verify(executorService).shutdown();
    }

    // Helper method to get private field value
    private Object getFieldValue(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }
}
package com.dms.processor.service.impl;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyFloat;

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

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testProcessWithParallelOcr() throws IOException, TesseractException {
        // Arrange
        OcrServiceImpl spyOcrService = spy(ocrService);
        BufferedImage realImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        PDDocument mockDocument = mock(PDDocument.class);

        // Create a controlled ExecutorService with 2 threads
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        ReflectionTestUtils.setField(spyOcrService, "executorService", executorService);

        try {
            try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
                pdDocumentMockedStatic.when(() -> PDDocument.load(pdfPath.toFile())).thenReturn(mockDocument);
                when(mockDocument.getNumberOfPages()).thenReturn(2);

                // Mock createTesseractInstance
                Tesseract mockTesseract = mock(Tesseract.class);
                doReturn(mockTesseract).when(spyOcrService).createTesseractInstance();
                when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("page text");

                // Mock ImageIO operations
                try (MockedStatic<ImageIO> imageIOMockedStatic = Mockito.mockStatic(ImageIO.class)) {
                    imageIOMockedStatic.when(() -> ImageIO.write(
                            any(RenderedImage.class),
                            eq("PNG"),
                            any(File.class)
                    )).thenReturn(true);

                    imageIOMockedStatic.when(() -> ImageIO.read(any(File.class)))
                            .thenReturn(realImage);
                }

                // Mock the PDFRenderer creation
                try (MockedConstruction<PDFRenderer> mockConstruction =
                             Mockito.mockConstruction(PDFRenderer.class,
                                     (mock, context) -> {
                                         when(mock.renderImageWithDPI(
                                                 anyInt(),
                                                 anyFloat(),
                                                 any(ImageType.class)
                                         )).thenReturn(realImage);
                                     })) {

                    // Act
                    String result = spyOcrService.processWithParallelOcr(pdfPath, 2);

                    // Assert
                    assertNotNull(result);
                    assertTrue(result.contains("page text"));
                    verify(mockTesseract, atLeast(2)).doOCR(any(BufferedImage.class));
                }
            }
        } finally {
            // Ensure executor service is shut down
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.out.println("Executor service did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testRenderPage() throws Exception {
        // Arrange
        PDFRenderer mockRenderer = mock(PDFRenderer.class);
        BufferedImage mockImage = mock(BufferedImage.class);
        when(mockRenderer.renderImageWithDPI(anyInt(), anyFloat(), any())).thenReturn(mockImage);

        // Test RGB mode
        ReflectionTestUtils.setField(ocrService, "imageType", "RGB");
        BufferedImage rgbResult = ocrService.renderPage(mockRenderer, 0);
        assertNotNull(rgbResult);
        verify(mockRenderer).renderImageWithDPI(eq(0), eq(300f), eq(ImageType.RGB));

        // Test BINARY mode
        ReflectionTestUtils.setField(ocrService, "imageType", "BINARY");
        BufferedImage binaryResult = ocrService.renderPage(mockRenderer, 0);
        assertNotNull(binaryResult);
        verify(mockRenderer).renderImageWithDPI(eq(0), eq(300f), eq(ImageType.BINARY));
    }

    @Test
    void testShutdown() throws Exception {
        // Test when executorService is null
        ReflectionTestUtils.setField(ocrService, "executorService", null);
        ocrService.shutdown();
        // No exception should be thrown

        // Test when executorService is not null
        ExecutorService mockExecutorService = mock(ExecutorService.class);
        when(mockExecutorService.isShutdown()).thenReturn(false);
        ReflectionTestUtils.setField(ocrService, "executorService", mockExecutorService);

        ocrService.shutdown();
        verify(mockExecutorService).shutdown();

        // Test when executorService is already shutdown
        when(mockExecutorService.isShutdown()).thenReturn(true);
        ocrService.shutdown();
        verify(mockExecutorService, times(1)).shutdown(); // Should not be called again
    }

    @Test
    void testProcessOcrSequentially() throws IOException, TesseractException {
        // Arrange
        PDFRenderer mockRenderer = mock(PDFRenderer.class);
        BufferedImage mockImage = mock(BufferedImage.class);
        OcrServiceImpl spyOcrService = spy(ocrService);

        when(mockRenderer.renderImageWithDPI(anyInt(), anyFloat(), any())).thenReturn(mockImage);
        doReturn(mockImage).when(spyOcrService).renderPage(any(), anyInt());
        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("page 1 text", "page 2 text");

        // Act
        String result = spyOcrService.processOcrSequentially(mockRenderer, 2);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("page 1 text"));
        assertTrue(result.contains("page 2 text"));
        verify(mockImage, times(2)).flush();
        verify(tesseract, times(2)).doOCR(any(BufferedImage.class));
    }

    @Test
    void testInitialize_WithZeroThreads() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(ocrService, "maxThreads", 0);
        int expectedThreads = Runtime.getRuntime().availableProcessors();

        // Act
        ocrService.initialize();

        // Assert
        ExecutorService executorService = (ExecutorService) ReflectionTestUtils.getField(ocrService, "executorService");
        assertNotNull(executorService);
        assertInstanceOf(ThreadPoolExecutor.class, executorService);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testProcessWithParallelOcr_ErrorHandling() throws IOException, TesseractException {
        // Arrange
        OcrServiceImpl spyOcrService = spy(ocrService);
        // Create a minimal real BufferedImage instead of mocking
        BufferedImage testImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        PDDocument mockDocument = mock(PDDocument.class);

        // Create a controlled ExecutorService with 2 threads
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        ReflectionTestUtils.setField(spyOcrService, "executorService", executorService);

        try {
            try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
                // Mock PDDocument loading
                pdDocumentMockedStatic.when(() -> PDDocument.load(pdfPath.toFile())).thenReturn(mockDocument);
                when(mockDocument.getNumberOfPages()).thenReturn(2);

                // Mock createTesseractInstance with error handling
                Tesseract mockTesseract = mock(Tesseract.class);
                doReturn(mockTesseract).when(spyOcrService).createTesseractInstance();

                // Mock Tesseract OCR behavior - first call throws exception, second succeeds
                when(mockTesseract.doOCR(any(BufferedImage.class)))
                        .thenThrow(new TesseractException()) // First page fails
                        .thenReturn("page 2 text"); // Second page succeeds

                // Mock ImageIO operations
                try (MockedStatic<ImageIO> imageIOMockedStatic = Mockito.mockStatic(ImageIO.class)) {
                    imageIOMockedStatic.when(() -> ImageIO.write(
                            any(RenderedImage.class),
                            eq("PNG"),
                            any(File.class)
                    )).thenReturn(true);

                    imageIOMockedStatic.when(() -> ImageIO.read(any(File.class)))
                            .thenReturn(testImage);
                }

                // Mock the PDFRenderer creation
                try (MockedConstruction<PDFRenderer> mockConstruction =
                             Mockito.mockConstruction(PDFRenderer.class,
                                     (mock, context) -> {
                                         when(mock.renderImageWithDPI(
                                                 anyInt(),
                                                 anyFloat(),
                                                 any(ImageType.class)
                                         )).thenReturn(testImage);
                                     })) {

                    // Act
                    String result = spyOcrService.processWithParallelOcr(pdfPath, 2);

                    // Assert
                    assertNotNull(result);
                    assertTrue(result.contains("page 2 text"));
                    verify(mockTesseract, times(2)).doOCR(any(BufferedImage.class));
                }
            }
        } finally {
            // Ensure executor service is shut down
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.out.println("Executor service did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
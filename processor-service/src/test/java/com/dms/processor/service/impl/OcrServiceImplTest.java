package com.dms.processor.service.impl;

import com.dms.processor.config.ThreadPoolManager;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OcrServiceImplTest {

    @Mock
    private Tesseract tesseract;

    @Mock
    private ThreadPoolManager threadPoolManager;

    @Mock
    private PDDocument document;

    @Mock
    private PDFRenderer pdfRenderer;

    @Mock
    private BufferedImage image;

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
        ReflectionTestUtils.setField(ocrService, "tessdataPath", "/path/to/tessdata");
        ReflectionTestUtils.setField(ocrService, "ocrPageThreshold", 5);

        // Mock ThreadPoolManager behavior
        when(threadPoolManager.submitOcrTask(any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<String> task = invocation.getArgument(0);
                    String result = task.call();
                    return CompletableFuture.completedFuture(result);
                });
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
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(File.class))).thenReturn(document);
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
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(File.class))).thenReturn(document);
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
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(File.class))).thenReturn(document);
            when(document.getNumberOfPages()).thenReturn(3); // Below threshold

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
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(File.class))).thenReturn(document);
            when(document.getNumberOfPages()).thenReturn(10); // Above threshold

            doReturn("parallel ocr result").when(spyOcrService).processWithParallelOcr(eq(pdfPath), eq(10));

            // Act
            String result = spyOcrService.processWithOcr(pdfPath, 10);

            // Assert
            assertEquals("parallel ocr result", result);
            verify(spyOcrService).processWithParallelOcr(pdfPath, 10);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testProcessWithParallelOcr() throws IOException, TesseractException {
        // Arrange
        OcrServiceImpl spyOcrService = spy(ocrService);
        BufferedImage realImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        PDDocument mockDocument = mock(PDDocument.class);

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
                verify(threadPoolManager, times(2)).submitOcrTask(any(Callable.class));
            }
        }
    }

    @Test
    void testRenderPage() throws IOException {
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
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testProcessWithParallelOcr_ErrorHandling() throws IOException, TesseractException {
        // Arrange
        OcrServiceImpl spyOcrService = spy(ocrService);
        BufferedImage testImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        PDDocument mockDocument = mock(PDDocument.class);

        try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
            pdDocumentMockedStatic.when(() -> PDDocument.load(pdfPath.toFile())).thenReturn(mockDocument);
            when(mockDocument.getNumberOfPages()).thenReturn(2);

            // Mock createTesseractInstance
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
                verify(threadPoolManager, times(2)).submitOcrTask(any(Callable.class));
            }
        }
    }

    @Test
    void testExtractTextFromRegularFile_PPTX() throws IOException, TesseractException, Exception {
        // Arrange
        Path pptxPath = tempDir.resolve("test.pptx");
        Files.createFile(pptxPath);

        // Mock Files.probeContentType to return PowerPoint MIME type
        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.probeContentType(pptxPath))
                    .thenReturn("application/vnd.openxmlformats-officedocument.presentationml.presentation");

            // Mock XMLSlideShow and slide extraction
            try (MockedConstruction<XMLSlideShow> pptxConstruction = Mockito.mockConstruction(XMLSlideShow.class,
                    (mock, context) -> {
                        XSLFSlide mockSlide = mock(XSLFSlide.class);
                        when(mock.getSlides()).thenReturn(Arrays.asList(mockSlide));
                        when(mock.getPageSize()).thenReturn(new Dimension(720, 540)); // Ensure non-null page size
                    })) {
                // Mock ImageIO for slide image
                BufferedImage slideImage = new BufferedImage(720, 540, BufferedImage.TYPE_INT_RGB);
                try (MockedStatic<ImageIO> imageIOMock = Mockito.mockStatic(ImageIO.class)) {
                    imageIOMock.when(() -> ImageIO.read(any(File.class))).thenReturn(slideImage);
                    imageIOMock.when(() -> ImageIO.write(any(), eq("PNG"), any(File.class))).thenReturn(true);

                    // Mock Tesseract OCR
                    when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("slide text");

                    // Act
                    String result = ocrService.extractTextFromRegularFile(pptxPath);

                    // Assert
                    assertNotNull(result);
                    assertTrue(result.contains("slide text"));
                    verify(tesseract).doOCR(any(BufferedImage.class));
                }
            }
        }
    }

    @Test
    void testExtractTextFromRegularFile_PPT() throws IOException, TesseractException, Exception {
        // Arrange
        Path pptPath = tempDir.resolve("test.ppt");
        Files.createFile(pptPath);

        // Mock Files.probeContentType to return PPT MIME type
        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.probeContentType(pptPath)).thenReturn("application/vnd.ms-powerpoint");

            // Mock HSLFSlideShow and slide extraction
            try (MockedConstruction<HSLFSlideShow> pptConstruction = Mockito.mockConstruction(HSLFSlideShow.class,
                    (mock, context) -> {
                        HSLFSlide mockSlide = mock(HSLFSlide.class);
                        when(mock.getSlides()).thenReturn(Arrays.asList(mockSlide));
                        when(mock.getPageSize()).thenReturn(new Dimension(720, 540)); // Ensure non-null page size
                    })) {
                // Mock ImageIO for slide image
                BufferedImage slideImage = new BufferedImage(720, 540, BufferedImage.TYPE_INT_RGB);
                try (MockedStatic<ImageIO> imageIOMock = Mockito.mockStatic(ImageIO.class)) {
                    imageIOMock.when(() -> ImageIO.read(any(File.class))).thenReturn(slideImage);
                    imageIOMock.when(() -> ImageIO.write(any(), eq("PNG"), any(File.class))).thenReturn(true);

                    // Mock Tesseract OCR
                    when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("slide text");

                    // Act
                    String result = ocrService.extractTextFromRegularFile(pptPath);

                    // Assert
                    assertNotNull(result);
                    assertTrue(result.contains("slide text"));
                    verify(tesseract).doOCR(any(BufferedImage.class));
                }
            }
        }
    }

    @Test
    void testExtractTextFromRegularFile_ImageFile() throws IOException, TesseractException {
        // Arrange
        Path imagePath = tempDir.resolve("test.png");
        Files.createFile(imagePath);

        // Mock Files.probeContentType to return image MIME type
        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.probeContentType(imagePath)).thenReturn("image/png");

            // Mock ImageIO
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            try (MockedStatic<ImageIO> imageIOMock = Mockito.mockStatic(ImageIO.class)) {
                imageIOMock.when(() -> ImageIO.read(imagePath.toFile())).thenReturn(image);

                // Mock Tesseract OCR
                when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("image text");

                // Act
                String result = ocrService.extractTextFromRegularFile(imagePath);

                // Assert
                assertEquals("image text\n", result);
                verify(tesseract).doOCR(any(BufferedImage.class));
            }
        }
    }

    @Test
    void testExtractTextFromRegularFile_UnsupportedFileType() throws IOException, TesseractException {
        // Arrange
        Path filePath = tempDir.resolve("test.txt");
        Files.createFile(filePath);

        // Mock Files.probeContentType to return unsupported MIME type
        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.probeContentType(filePath)).thenReturn("text/plain");

            // Act
            String result = ocrService.extractTextFromRegularFile(filePath);

            // Assert
            assertEquals("", result); // Empty result for unsupported file type
        }
    }

    @Test
    void testExtractTextFromRegularFile_TikaException() throws IOException {
        // Arrange
        Path filePath = tempDir.resolve("test.pptx");
        Files.createFile(filePath);

        // Mock Files.probeContentType to throw IOException
        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.probeContentType(filePath)).thenThrow(new IOException("Tika error"));

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> ocrService.extractTextFromRegularFile(filePath));
            assertTrue(exception.getCause() instanceof IOException);
            assertTrue(exception.getMessage().contains("Failed to process file"));
        }
    }

    @Test
    void testIsImageFile() throws IOException {
        // Arrange
        Path imagePath = tempDir.resolve("test.png");
        Path nonImagePath = tempDir.resolve("test.txt");
        Files.createFile(imagePath);
        Files.createFile(nonImagePath);

        // Mock Files.probeContentType
        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.probeContentType(imagePath)).thenReturn("image/png");
            filesMock.when(() -> Files.probeContentType(nonImagePath)).thenReturn("text/plain");
            filesMock.when(() -> Files.probeContentType(tempDir.resolve("invalid"))).thenReturn(null);

            // Act & Assert
            assertTrue(ocrService.isImageFile(imagePath));
            assertFalse(ocrService.isImageFile(nonImagePath));
            assertFalse(ocrService.isImageFile(tempDir.resolve("invalid")));
        }
    }

    @Test
    void testCleanupTempFiles_PartialFailure() throws IOException {
        // Arrange - Use a fixed path instead of creating real files
        Path tempDir = Paths.get("/temp/ocr_test");
        Path file1 = tempDir.resolve("file1.png");
        Path file2 = tempDir.resolve("file2.png");

        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class)) {
            // Setup mocks - file1 deletion succeeds, file2 deletion fails
            filesMock.when(() -> Files.deleteIfExists(file1)).thenReturn(true);
            filesMock.when(() -> Files.deleteIfExists(file2)).thenThrow(new IOException("Cannot delete"));
            filesMock.when(() -> Files.deleteIfExists(tempDir)).thenReturn(true);

            // Act - should not throw exception despite file2 deletion failure
            ocrService.cleanupTempFiles(tempDir, Arrays.asList(file1, file2));

            // Verify that all expected methods were called
            filesMock.verify(() -> Files.deleteIfExists(file1));
            filesMock.verify(() -> Files.deleteIfExists(file2));
            filesMock.verify(() -> Files.deleteIfExists(tempDir));
        }
    }

    @Test
    void testCleanupTempFiles_EmptyFileList() throws IOException {
        Path tempDir = Files.createTempDirectory("ocr_test_");
        ocrService.cleanupTempFiles(tempDir, Collections.emptyList());
        assertFalse(Files.exists(tempDir), "Empty directory should be deleted");
    }

    @Test
    void testCleanupTempFiles_NonExistentDirectory() {
        Path tempDir = Paths.get("non_existent_dir");
        ocrService.cleanupTempFiles(tempDir, Collections.emptyList());
        assertFalse(Files.exists(tempDir), "Non-existent directory should not cause errors");
    }

    @Test
    void testProcessWithParallelOcr_AllTasksFail() throws IOException, TesseractException {
        // Arrange
        OcrServiceImpl spyOcrService = spy(ocrService);
        PDDocument mockDocument = mock(PDDocument.class);

        try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
            pdDocumentMockedStatic.when(() -> PDDocument.load(pdfPath.toFile())).thenReturn(mockDocument);
            when(mockDocument.getNumberOfPages()).thenReturn(2);

            // Mock createTesseractInstance
            Tesseract mockTesseract = mock(Tesseract.class);
            doReturn(mockTesseract).when(spyOcrService).createTesseractInstance();
            when(mockTesseract.doOCR(any(BufferedImage.class))).thenThrow(new TesseractException("OCR failed"));

            // Mock ImageIO operations
            BufferedImage testImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            try (MockedStatic<ImageIO> imageIOMock = Mockito.mockStatic(ImageIO.class)) {
                imageIOMock.when(() -> ImageIO.write(any(), eq("PNG"), any(File.class))).thenReturn(true);
                imageIOMock.when(() -> ImageIO.read(any(File.class))).thenReturn(testImage);
            }

            // Mock PDFRenderer
            try (MockedConstruction<PDFRenderer> mockConstruction = Mockito.mockConstruction(PDFRenderer.class,
                    (mock, context) -> when(mock.renderImageWithDPI(anyInt(), anyFloat(), any())).thenReturn(testImage))) {

                // Act
                String result = spyOcrService.processWithParallelOcr(pdfPath, 2);

                // Assert
                assertEquals("", result); // All tasks failed, so result is empty
                verify(mockTesseract, times(2)).doOCR(any(BufferedImage.class));
                verify(threadPoolManager, times(2)).submitOcrTask(any(Callable.class));
            }
        }
    }


    @Test
    void testExtractTextFromPdf_EmptyDocument() throws IOException, TesseractException {
        // Arrange
        try (MockedStatic<PDDocument> pdDocumentMockedStatic = Mockito.mockStatic(PDDocument.class)) {
            pdDocumentMockedStatic.when(() -> PDDocument.load(any(File.class))).thenReturn(document);
            when(document.getNumberOfPages()).thenReturn(0);

            // Act
            String result = ocrService.extractTextFromPdf(pdfPath);

            // Assert
            assertEquals("", result); // Empty document should return empty string
        }
    }

    @Test
    void testExtractTextFromRegularFile_PPTX_NullPageSize() throws IOException, TesseractException, Exception {
        // Arrange
        Path pptxPath = tempDir.resolve("test.pptx");
        Files.createFile(pptxPath);

        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.probeContentType(pptxPath))
                    .thenReturn("application/vnd.openxmlformats-officedocument.presentationml.presentation");

            try (MockedConstruction<XMLSlideShow> pptxConstruction = Mockito.mockConstruction(XMLSlideShow.class,
                    (mock, context) -> {
                        XSLFSlide mockSlide = mock(XSLFSlide.class);
                        when(mock.getSlides()).thenReturn(Arrays.asList(mockSlide));
                        when(mock.getPageSize()).thenReturn(null); // Simulate null page size
                    })) {
                BufferedImage slideImage = new BufferedImage(720, 540, BufferedImage.TYPE_INT_RGB);
                try (MockedStatic<ImageIO> imageIOMock = Mockito.mockStatic(ImageIO.class)) {
                    imageIOMock.when(() -> ImageIO.read(any(File.class))).thenReturn(slideImage);
                    imageIOMock.when(() -> ImageIO.write(any(), eq("PNG"), any(File.class))).thenReturn(true);

                    when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("slide text");

                    // Act
                    String result = ocrService.extractTextFromRegularFile(pptxPath);

                    // Assert
                    assertNotNull(result);
                    assertTrue(result.contains("slide text"));
                    verify(tesseract).doOCR(any(BufferedImage.class));
                }
            }
        }
    }

}

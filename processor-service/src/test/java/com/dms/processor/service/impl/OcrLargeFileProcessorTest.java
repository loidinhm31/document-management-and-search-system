package com.dms.processor.service.impl;

import com.dms.processor.config.ThreadPoolManager;
import com.dms.processor.service.OcrService;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OcrLargeFileProcessorTest {

    @Mock
    private OcrService ocrService;

    @Mock
    private ThreadPoolManager threadPoolManager;

    @Spy
    @InjectMocks
    private OcrLargeFileProcessor ocrLargeFileProcessor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ocrService = mock(OcrService.class);
        threadPoolManager = mock(ThreadPoolManager.class);
        ocrLargeFileProcessor = spy(new OcrLargeFileProcessor(ocrService, threadPoolManager));

        // Set required fields
        ReflectionTestUtils.setField(ocrLargeFileProcessor, "chunkSize", 10);
        ReflectionTestUtils.setField(ocrLargeFileProcessor, "tempDir", tempDir.toString());
        ReflectionTestUtils.setField(ocrLargeFileProcessor, "timeoutMinutes", 60);
        ReflectionTestUtils.setField(ocrLargeFileProcessor, "tessdataPath", "/usr/share/tessdata");
        ReflectionTestUtils.setField(ocrLargeFileProcessor, "dpi", 300.0f);

        // Mock thread pool behavior
        lenient().when(threadPoolManager.submitOcrTask(any())).thenAnswer(invocation ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return ((Callable<String>)invocation.getArgument(0)).call();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }));
    }

    @AfterEach
    void tearDown() {
        // No need to shutdown executor service anymore
    }

    @Test
    public void testProcessLarge_File_DirectTextExtraction() throws IOException, TesseractException {
        // Setup - create a valid dummy PDF file
        Path pdfPath = createDummyPdfFile();

        // Mock the OcrService to return a meaningful text
        String expectedText = "This is sufficient text for direct extraction";
        when(ocrService.extractTextFromPdf(any(Path.class))).thenReturn(expectedText);

        // Mock temp directory creation
        File mockTempDir = mock(File.class);
        doReturn(mockTempDir).when(ocrLargeFileProcessor).createTempDirectory();
        doReturn(new ArrayList<File>()).when(ocrLargeFileProcessor).extractPagesToImages(any(Path.class), any(File.class));

        // Make isTextSufficient return true
        doReturn(true).when(ocrLargeFileProcessor).isTextSufficient(anyString());

        // Execute
        String result = ocrLargeFileProcessor.processLargeFile(pdfPath);

        // Verify
        verify(ocrService).extractTextFromPdf(any(Path.class));
        verify(ocrLargeFileProcessor).cleanupTempDirectory(mockTempDir);
        assertNotNull(result);
        assertEquals(expectedText, result);
    }

    @Test
    public void testProcessLarge_File_WithImageExtraction() throws IOException, TesseractException {
        // Setup - create a valid dummy PDF file
        Path pdfPath = createDummyPdfFile();

        // Mock the OcrService to return insufficient text
        when(ocrService.extractTextFromPdf(any(Path.class))).thenReturn("short");

        // Make isTextSufficient return false
        doReturn(false).when(ocrLargeFileProcessor).isTextSufficient(anyString());

        // Mock the createTempDirectory method
        File mockTempDir = new File(tempDir.toFile(), "ocr-temp");
        mockTempDir.mkdir();
        doReturn(mockTempDir).when(ocrLargeFileProcessor).createTempDirectory();

        // Mock the extractPagesToImages method
        List<File> mockImageFiles = createMockImageFiles(mockTempDir);
        doReturn(mockImageFiles).when(ocrLargeFileProcessor).extractPagesToImages(any(Path.class), any(File.class));

        // Mock the processPageImagesInChunks method
        String expectedResult = "OCR extracted text from images";
        doReturn(expectedResult).when(ocrLargeFileProcessor).processPageImagesInChunks(anyList(), anyInt());

        // Execute
        String result = ocrLargeFileProcessor.processLargeFile(pdfPath);

        // Verify
        verify(ocrService).extractTextFromPdf(any(Path.class));
        verify(ocrLargeFileProcessor).extractPagesToImages(any(Path.class), any(File.class));
        verify(ocrLargeFileProcessor).processPageImagesInChunks(eq(mockImageFiles), eq(mockImageFiles.size()));
        verify(ocrLargeFileProcessor).cleanupTempDirectory(mockTempDir);

        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    @Test
    public void testCreateTempDirectory() throws IOException {
        // Set temp dir
        ReflectionTestUtils.setField(ocrLargeFileProcessor, "tempDir", tempDir.toString());

        File result = ocrLargeFileProcessor.createTempDirectory();

        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.isDirectory());
        assertTrue(result.getPath().startsWith(tempDir.toString()));
    }

    @Test
    public void testCleanupTempDirectory() throws IOException {
        // Create a temporary directory with some files
        File testDir = new File(tempDir.toFile(), "test-cleanup");
        testDir.mkdir();
        File testFile = new File(testDir, "test.txt");
        testFile.createNewFile();

        ocrLargeFileProcessor.cleanupTempDirectory(testDir);

        // Verify that files and directory were deleted
        assertFalse(testFile.exists());
        assertFalse(testDir.exists());
    }

    @Test
    public void testIsTextSufficient() {
        // Test with sufficient text
        boolean result1 = ocrLargeFileProcessor.isTextSufficient(
                "This is a sufficiently long text that should pass the check because it has more than 100 recognizable characters.");
        assertTrue(result1);

        // Test with insufficient text
        boolean result2 = ocrLargeFileProcessor.isTextSufficient("Short text");
        assertFalse(result2);

        // Test with null
        boolean result3 = ocrLargeFileProcessor.isTextSufficient(null);
        assertFalse(result3);

        // Test with empty string
        boolean result4 = ocrLargeFileProcessor.isTextSufficient("");
        assertFalse(result4);
    }

    @Test
    void testProcessLarge_File_WithNullPath() {
        assertThrows(IllegalArgumentException.class, () ->
                ocrLargeFileProcessor.processLargeFile(null));
    }

    @Test
    void testProcessLarge_File_WithNonExistentFile() {
        Path nonExistentPath = tempDir.resolve("non-existent.pdf");
        assertThrows(IOException.class, () ->
                ocrLargeFileProcessor.processLargeFile(nonExistentPath));
    }

    @Test
    void testProcessPageImagesInChunks_EmptyImageList() throws Exception {
        List<File> emptyImageList = new ArrayList<>();
        String result = ocrLargeFileProcessor.processPageImagesInChunks(emptyImageList, 0);
        assertTrue(result.trim().isEmpty());
    }

    @Test
    void testProcessPageImagesInChunks_TimeoutException() throws Exception {
        List<File> mockImageFiles = Collections.singletonList(
                new File(tempDir.toFile(), "test.png")
        );

        ReflectionTestUtils.setField(ocrLargeFileProcessor, "timeoutMinutes", 0);
        ReflectionTestUtils.setField(ocrLargeFileProcessor, "chunkSize", 1);

        CompletableFuture<String> future = new CompletableFuture<>();
        doReturn(future).when(ocrLargeFileProcessor).processImageChunk(any(), anyInt(), anyInt());

        assertThrows(TesseractException.class, () ->
                ocrLargeFileProcessor.processPageImagesInChunks(mockImageFiles, 1));
    }

    @Test
    void testProcessPageImagesInChunks_InterruptedException() throws Exception {
        List<File> mockImageFiles = Collections.singletonList(
                new File(tempDir.toFile(), "test.png")
        );

        ReflectionTestUtils.setField(ocrLargeFileProcessor, "chunkSize", 1);

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            Thread.currentThread().interrupt();
            throw new CompletionException(new InterruptedException());
        });
        doReturn(future).when(ocrLargeFileProcessor).processImageChunk(any(), anyInt(), anyInt());

        assertThrows(TesseractException.class, () ->
                ocrLargeFileProcessor.processPageImagesInChunks(mockImageFiles, 1));
    }

    @Test
    void testExtractPagesToImages_EmptyPdf() throws IOException {
        Path emptyPdfPath = tempDir.resolve("empty.pdf");

        try (FileOutputStream fos = new FileOutputStream(emptyPdfPath.toFile())) {
            String minimalPdf =
                    "%PDF-1.4\n" +
                    "1 0 obj\n" +
                    "<</Type/Catalog/Pages 2 0 R>>\n" +
                    "endobj\n" +
                    "2 0 obj\n" +
                    "<</Type/Pages/Kids[]/Count 0>>\n" +
                    "endobj\n" +
                    "xref\n" +
                    "0 3\n" +
                    "0000000000 65535 f\n" +
                    "0000000010 00000 n\n" +
                    "0000000053 00000 n\n" +
                    "trailer\n" +
                    "<</Size 3/Root 1 0 R>>\n" +
                    "startxref\n" +
                    "101\n" +
                    "%%EOF";
            fos.write(minimalPdf.getBytes());
        }

        File tempDirectory = tempDir.toFile();
        List<File> result = ocrLargeFileProcessor.extractPagesToImages(emptyPdfPath, tempDirectory);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Expected empty list for PDF with no pages");
    }

    @Test
    void testProcessLarge_File_CleanupOnSuccess() throws IOException, TesseractException {
        Path pdfPath = createDummyPdfFile();
        File mockTempDir = spy(new File(tempDir.toFile(), "cleanup-test"));
        mockTempDir.mkdir();

        doReturn(mockTempDir).when(ocrLargeFileProcessor).createTempDirectory();
        doReturn("test content").when(ocrService).extractTextFromPdf(any(Path.class));
        doReturn(true).when(ocrLargeFileProcessor).isTextSufficient(anyString());

        ocrLargeFileProcessor.processLargeFile(pdfPath);

        verify(ocrLargeFileProcessor).cleanupTempDirectory(mockTempDir);
        assertFalse(mockTempDir.exists());
    }

    @Test
    void testThreadNaming() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        when(threadPoolManager.submitOcrTask(any())).thenAnswer(invocation -> {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                // Simulate thread naming as done by ThreadPoolManager
                Thread.currentThread().setName("ocr-large-processor-" + UUID.randomUUID());
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
                try {
                    return ((Callable<String>)invocation.getArgument(0)).call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
            return future;
        });

        // Act
        ocrLargeFileProcessor.processImageChunk(Collections.emptyList(), 0, 0);

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("ocr-large-processor-"),
                "Thread name should start with 'ocr-large-processor-' but was: " + threadName.get());
    }

    @Test
    void testCleanupTempDirectory_NonExistentDirectory() {
        File nonExistentDir = new File(tempDir.toFile(), "non-existent");
        assertDoesNotThrow(() -> ocrLargeFileProcessor.cleanupTempDirectory(nonExistentDir));
    }

    @Test
    void testCleanupTempDirectory_UndeleteableFiles() throws IOException {
        File mockDir = spy(new File(tempDir.toFile(), "undeleteable"));
        mockDir.mkdir();
        File mockFile = spy(new File(mockDir, "test.txt"));
        Files.write(mockFile.toPath(), "test".getBytes());

        doReturn(new File[]{mockFile}).when(mockDir).listFiles();
        doReturn(false).when(mockFile).delete();
        doReturn(false).when(mockDir).delete();

        assertDoesNotThrow(() -> ocrLargeFileProcessor.cleanupTempDirectory(mockDir));
    }

    @Test
    void testCreateTempDirectory_CreationFailure() {
        String invalidPath = "\0invalid";
        ReflectionTestUtils.setField(ocrLargeFileProcessor, "tempDir", invalidPath);
        assertThrows(IOException.class, () -> ocrLargeFileProcessor.createTempDirectory());
    }

    @Test
    void testProcessLarge_File_ExceptionDuringExtraction() throws IOException, TesseractException {
        Path pdfPath = createDummyPdfFile();
        doThrow(new IOException("Extraction failed"))
                .when(ocrLargeFileProcessor).extractPagesToImages(any(Path.class), any(File.class));

        assertThrows(IOException.class, () -> ocrLargeFileProcessor.processLargeFile(pdfPath));
    }

    @Test
    void testProcessImageChunk_Success() throws Exception {
        List<File> mockImageFiles = createMockImageFiles(tempDir.toFile());
        String expectedText = "Test OCR Text";
        Tesseract mockTesseract = mock(Tesseract.class);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn(expectedText);

        doReturn(mockTesseract).when(ocrLargeFileProcessor).createTesseractInstance();

        CompletableFuture<String> future = ocrLargeFileProcessor.processImageChunk(mockImageFiles, 0, 2);
        String result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.contains(expectedText));
        verify(mockTesseract, times(mockImageFiles.size())).doOCR(any(BufferedImage.class));
    }

    @Test
    void testProcessImageChunk_EmptyOcrResult() throws Exception {
        List<File> mockImageFiles = createMockImageFiles(tempDir.toFile());
        Tesseract mockTesseract = mock(Tesseract.class);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("");

        doReturn(mockTesseract).when(ocrLargeFileProcessor).createTesseractInstance();

        CompletableFuture<String> future = ocrLargeFileProcessor.processImageChunk(mockImageFiles, 0, 2);
        String result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.trim().isEmpty());
        verify(mockTesseract, times(mockImageFiles.size())).doOCR(any(BufferedImage.class));
    }

    @Test
    void testProcessImageChunk_OcrException() throws Exception {
        List<File> mockImageFiles = createMockImageFiles(tempDir.toFile());
        Tesseract mockTesseract = mock(Tesseract.class);
        when(mockTesseract.doOCR(any(BufferedImage.class)))
                .thenThrow(new TesseractException("OCR failed", new RuntimeException()));

        doReturn(mockTesseract).when(ocrLargeFileProcessor).createTesseractInstance();

        CompletableFuture<String> future = ocrLargeFileProcessor.processImageChunk(mockImageFiles, 0, 2);
        String result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.trim().isEmpty());
        verify(mockTesseract, times(mockImageFiles.size())).doOCR(any(BufferedImage.class));
    }

    @Test
    void testProcessImageChunk_InvalidImageFile() throws Exception {
        File invalidFile = new File(tempDir.toFile(), "invalid.png");
        List<File> mockImageFiles = Collections.singletonList(invalidFile);
        Tesseract mockTesseract = mock(Tesseract.class);

        doReturn(mockTesseract).when(ocrLargeFileProcessor).createTesseractInstance();

        CompletableFuture<String> future = ocrLargeFileProcessor.processImageChunk(mockImageFiles, 0, 1);
        String result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.trim().isEmpty());
        verify(mockTesseract, never()).doOCR(any(BufferedImage.class));
    }

    @Test
    void testProcessImageChunk_NullOcrResult() throws Exception {
        List<File> mockImageFiles = createMockImageFiles(tempDir.toFile());
        Tesseract mockTesseract = mock(Tesseract.class);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn(null);

        doReturn(mockTesseract).when(ocrLargeFileProcessor).createTesseractInstance();

        CompletableFuture<String> future = ocrLargeFileProcessor.processImageChunk(mockImageFiles, 0, 2);
        String result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.trim().isEmpty());
        verify(mockTesseract, times(mockImageFiles.size())).doOCR(any(BufferedImage.class));
    }

    @Test
    void testProcessImageChunk_ProgressLogging() throws Exception {
        List<File> mockImageFiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            File imageFile = new File(tempDir.toFile(), "page_" + i + ".png");
            createDummyImageFile(imageFile);
            mockImageFiles.add(imageFile);
        }

        Tesseract mockTesseract = mock(Tesseract.class);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("Test OCR Text");

        doReturn(mockTesseract).when(ocrLargeFileProcessor).createTesseractInstance();

        ReflectionTestUtils.setField(ocrLargeFileProcessor, "processedPages", new AtomicInteger(0));

        CompletableFuture<String> future = ocrLargeFileProcessor.processImageChunk(mockImageFiles, 0, 10);
        future.get(5, TimeUnit.SECONDS);

        AtomicInteger processedPages = (AtomicInteger) ReflectionTestUtils.getField(ocrLargeFileProcessor, "processedPages");
        assertEquals(10, processedPages.get());
    }

    // Helper methods
    private Path createDummyPdfFile() throws IOException {
        Path pdfPath = tempDir.resolve("test.pdf");
        try (FileOutputStream fos = new FileOutputStream(pdfPath.toFile())) {
            String pdfContent =
                    "%PDF-1.4\n" +
                    "1 0 obj\n" +
                    "<</Type /Catalog /Pages 2 0 R>>\n" +
                    "endobj\n" +
                    "2 0 obj\n" +
                    "<</Type /Pages /Kids [3 0 R] /Count 1>>\n" +
                    "endobj\n" +
                    "3 0 obj\n" +
                    "<</Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R>>\n" +
                    "endobj\n" +
                    "4 0 obj\n" +
                    "<</Length 10>>\n" +
                    "stream\n" +
                    "BT /F1 12 Tf 100 700 Td (Test PDF) Tj ET\n" +
                    "endstream\n" +
                    "endobj\n" +
                    "xref\n" +
                    "0 5\n" +
                    "0000000000 65535 f\n" +
                    "0000000010 00000 n\n" +
                    "0000000053 00000 n\n" +
                    "0000000102 00000 n\n" +
                    "0000000183 00000 n\n" +
                    "trailer <</Size 5 /Root 1 0 R>>\n" +
                    "startxref\n" +
                    "284\n" +
                    "%%EOF";
            fos.write(pdfContent.getBytes());
        }
        return pdfPath;
    }

    private void createDummyImageFile(File file) throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "PNG", file);
    }

    private List<File> createMockImageFiles(File tempDirectory) throws IOException {
        File page0 = new File(tempDirectory, "page_0.png");
        File page1 = new File(tempDirectory, "page_1.png");
        createDummyImageFile(page0);
        createDummyImageFile(page1);
        return Arrays.asList(page0, page1);
    }
}
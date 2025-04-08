package com.dms.processor.service.impl;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.dto.ExtractedText;
import com.dms.processor.enums.DocumentType;
import com.dms.processor.util.MimeTypeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentExtractorServiceImplTest {
    @Mock
    private SmartPdfExtractor smartPdfExtractor;

    @Mock
    private OcrLargeFileProcessor ocrLargeFileProcessor;

    @Mock
    private LargeFileProcessor largeFileProcessor;

    @Spy
    @InjectMocks
    private ContentExtractorServiceImpl contentExtractorService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Set configuration values needed for testing
        ReflectionTestUtils.setField(contentExtractorService, "largeSizeThreshold", DataSize.ofMegabytes(5));
    }

    @Test
    void extractContent_nonExistentFile_returnsEmptyContent() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("non-existent.txt");

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(nonExistentFile);

        // Assert
        assertNotNull(result);
        assertEquals("", result.content());
        assertTrue(result.metadata().isEmpty());
    }

    @Test
    void extractContent_pdfFile_usesPdfExtractor() throws Exception {
        // Arrange
        Path pdfFile = Files.createFile(tempDir.resolve("test.pdf"));
        Files.write(pdfFile, "PDF content".getBytes());

        // Mock the PDF extractor
        when(smartPdfExtractor.extractText(any(Path.class)))
                .thenReturn(new ExtractedText("PDF content extracted", false));

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(pdfFile);

        // Assert
        verify(smartPdfExtractor).extractText(pdfFile);
        assertEquals("PDF content extracted", result.content());
        assertTrue(result.metadata().containsKey("Document-Type"));
        assertEquals("PDF", result.metadata().get("Document-Type"));
    }

    @Test
    void extractContent_pdfFile_populatesMetadata() throws Exception {
        // Arrange
        Path pdfFile = Files.createFile(tempDir.resolve("metadata.pdf"));
        Files.write(pdfFile, "PDF content".getBytes());

        when(smartPdfExtractor.extractText(any(Path.class)))
                .thenReturn(new ExtractedText("PDF with metadata", false));

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(pdfFile);

        // Assert
        assertTrue(result.metadata().containsKey("Document-Type"));
        assertTrue(result.metadata().containsKey("Document-Type-Display"));
        assertTrue(result.metadata().containsKey("Processing-Method"));
        assertEquals("direct", result.metadata().get("Processing-Method"));
        assertEquals("false", result.metadata().get("Used-OCR"));
    }

    @Test
    void extractContent_pdfFile_withOcr_populatesCorrectMetadata() throws Exception {
        // Arrange
        Path pdfFile = Files.createFile(tempDir.resolve("ocr-metadata.pdf"));
        Files.write(pdfFile, "PDF content".getBytes());

        when(smartPdfExtractor.extractText(any(Path.class)))
                .thenReturn(new ExtractedText("PDF with OCR metadata", true));

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(pdfFile);

        // Assert
        assertTrue(result.metadata().containsKey("Processing-Method"));
        assertEquals("ocr", result.metadata().get("Processing-Method"));
        assertEquals("true", result.metadata().get("Used-OCR"));
    }

    @Test
    void extractContent_docxFile_detectsMimeType() throws Exception {
        // Arrange
        Path docxFile = Files.createFile(tempDir.resolve("test.docx"));
        Files.write(docxFile, "DOCX content".getBytes());

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(docxFile);

        // Assert
        assertNotNull(result);
        assertTrue(result.metadata().containsKey("Detected-MIME-Type"));
        assertTrue(result.metadata().containsKey("Document-Type"));
        assertEquals("WORD_DOCX", result.metadata().get("Document-Type"));
        assertTrue(result.content().contains("DOCX content"));
    }

    @Test
    void extractContent_textFile_extractsTextContent() throws Exception {
        // Arrange
        Path textFile = Files.createFile(tempDir.resolve("test.txt"));
        Files.write(textFile, "Text content for extraction test".getBytes());

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(textFile);

        // Assert
        assertNotNull(result);
        assertTrue(result.content().contains("Text content for extraction test"));
        assertTrue(result.metadata().containsKey("Document-Type"));
        assertEquals("TEXT_PLAIN", result.metadata().get("Document-Type"));
    }

    @Test
    void extractContent_emptyFile_returnsEmptyContent() throws Exception {
        // Arrange
        Path emptyFile = Files.createFile(tempDir.resolve("empty.txt"));

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(emptyFile);

        // Assert
        assertNotNull(result);
        assertTrue(result.content().isEmpty() || result.content().isBlank());
        assertTrue(result.metadata().containsKey("Document-Type"));
    }

    @Test
    void extractContent_pdfFileThrowsException_handlesGracefully() throws Exception {
        // Arrange
        Path pdfFile = Files.createFile(tempDir.resolve("error.pdf"));
        Files.write(pdfFile, "PDF content".getBytes());

        when(smartPdfExtractor.extractText(any(Path.class)))
                .thenThrow(new IOException("Test exception"));

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(pdfFile);

        // Assert
        assertNotNull(result);
        assertEquals("", result.content());
        assertTrue(result.metadata().isEmpty());
    }

    @Test
    void extractContent_specialCharactersInFilename_sanitizesForMimeDetection() throws Exception {
        // Arrange
        Path specialFile = Files.createFile(tempDir.resolve("test@#$%^&.txt"));
        Files.write(specialFile, "Content with special chars in filename".getBytes());

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(specialFile);

        // Assert
        assertNotNull(result);
        assertFalse(result.content().isEmpty());
        assertTrue(result.metadata().containsKey("Document-Type"));
    }

    @Test
    void extractContent_unreadableFile_returnsEmptyContent() {
        // Arrange
        // We can use a real path but mock the static Files methods
        Path filePath = tempDir.resolve("unreadable.txt");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Setup mocks for static methods
            filesMock.when(() -> Files.exists(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(eq(filePath))).thenReturn(false);

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            assertNotNull(result);
            assertEquals("", result.content());
            assertTrue(result.metadata().isEmpty());
        }
    }

    @Test
    void extractContent_jsonFile_detectsCorrectDocumentType() throws Exception {
        // Arrange
        Path jsonFile = Files.createFile(tempDir.resolve("test.json"));
        Files.write(jsonFile, "{\"key\": \"value\"}".getBytes());

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(jsonFile);

        // Assert
        assertNotNull(result);
        assertTrue(result.content().contains("{\"key\": \"value\"}"));
        assertEquals("JSON", result.metadata().get("Document-Type"));
    }

    @Test
    void extractContent_largeNonPdfFile_usesLargeFileProcessor() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("large.txt"));
        Files.write(filePath, "Sample content".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Setup mocks for static methods
            filesMock.when(() -> Files.exists(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");
            filesMock.when(() -> Files.size(eq(filePath))).thenReturn(DataSize.ofMegabytes(10).toBytes());

            // Allow basic file operations to work
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("Sample content".getBytes()));

            // Setup the large file processor mock
            CompletableFuture<String> future = CompletableFuture.completedFuture("Large file processed content");
            when(largeFileProcessor.processLargeFile(any(Path.class))).thenReturn(future);

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            verify(largeFileProcessor).processLargeFile(filePath);
            assertEquals("Large file processed content", result.content());
        }
    }

    @Test
    void extractContent_largePdfFile_usesOcrLargeFileProcessor() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("large.pdf"));
        Files.write(filePath, "Sample PDF content".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Setup mocks for static methods
            filesMock.when(() -> Files.exists(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("application/pdf");
            filesMock.when(() -> Files.size(eq(filePath))).thenReturn(DataSize.ofMegabytes(10).toBytes());

            // Allow basic file operations to work
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("Sample PDF content".getBytes()));

            // Setup OCR processor mock
            when(ocrLargeFileProcessor.processLargePdf(any(Path.class))).thenReturn("Large PDF OCR content");

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            verify(ocrLargeFileProcessor).processLargePdf(filePath);
            assertEquals("Large PDF OCR content", result.content());
            assertEquals("chunked", result.metadata().get("Processing-Method"));
        }
    }

    @Test
    void extractContent_tikaFailure_fallsBackToBasicExtraction() throws Exception {
        // Arrange
        Path textFile = Files.createFile(tempDir.resolve("tika-fail.txt"));
        String content = "Text content for fallback test";
        Files.write(textFile, content.getBytes(StandardCharsets.UTF_8));

        // Make tikaExtractTextContent return an empty string to trigger fallback
        doReturn("").when(contentExtractorService).tikaExtractTextContent(eq(textFile), anyMap());

        // Allow basicExtractTextContent to run the real implementation
        doCallRealMethod().when(contentExtractorService).basicExtractTextContent(any(Path.class));

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(textFile);

        // Assert
        assertNotNull(result);
        assertTrue(result.content().contains(content));
        verify(contentExtractorService).tikaExtractTextContent(eq(textFile), anyMap());
        verify(contentExtractorService).basicExtractTextContent(textFile);
    }

    @Test
    void extractContent_largeFileCompletableFutureTimeout_handlesGracefully() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("large_timeout.txt"));
        Files.write(filePath, "Sample content".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Setup mocks for static methods
            filesMock.when(() -> Files.exists(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");
            filesMock.when(() -> Files.size(eq(filePath))).thenReturn(DataSize.ofMegabytes(10).toBytes());

            // Allow basic file operations to work
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("Sample content".getBytes()));

            // Create a CompletableFuture that will timeout
            CompletableFuture<String> mockFuture = mock(CompletableFuture.class);
            when(largeFileProcessor.processLargeFile(any(Path.class))).thenReturn(mockFuture);

            // Make the future.get() throw a TimeoutException
            when(mockFuture.get(anyLong(), any(TimeUnit.class)))
                    .thenThrow(new TimeoutException("Timeout waiting for large file processing"));

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            assertNotNull(result);
            assertEquals("", result.content());
            assertTrue(result.metadata().isEmpty());
        }
    }

    @Test
    void extractContent_largeFileCompletableFutureInterrupted_handlesGracefully() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("large_interrupted.txt"));
        Files.write(filePath, "Sample content".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Setup mocks for static methods
            filesMock.when(() -> Files.exists(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");
            filesMock.when(() -> Files.size(eq(filePath))).thenReturn(DataSize.ofMegabytes(10).toBytes());

            // Allow basic file operations to work
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("Sample content".getBytes()));

            // Create a CompletableFuture that will be interrupted
            CompletableFuture<String> mockFuture = mock(CompletableFuture.class);
            when(largeFileProcessor.processLargeFile(any(Path.class))).thenReturn(mockFuture);

            // Make the future.get() throw an InterruptedException
            when(mockFuture.get(anyLong(), any(TimeUnit.class)))
                    .thenThrow(new InterruptedException("Thread interrupted"));

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            assertNotNull(result);
            assertEquals("", result.content());
            assertTrue(result.metadata().isEmpty());
        }
    }

    @Test
    void extractContent_largeFileCompletableFutureReturnsSuccessfully() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("large_success.txt"));
        Files.write(filePath, "Sample content".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Setup mocks for static methods
            filesMock.when(() -> Files.exists(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");
            filesMock.when(() -> Files.size(eq(filePath))).thenReturn(DataSize.ofMegabytes(10).toBytes());

            // Allow basic file operations to work
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("Sample content".getBytes()));

            // Create a CompletableFuture that will return successfully
            CompletableFuture<String> future = CompletableFuture.completedFuture("Large file processed successfully");
            when(largeFileProcessor.processLargeFile(any(Path.class))).thenReturn(future);

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            assertNotNull(result);
            assertEquals("Large file processed successfully", result.content());
        }
    }

    @Test
    void extractContent_largeFileWithExecutionException_handlesGracefully() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("large_execution_error.txt"));
        Files.write(filePath, "Sample content".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Setup mocks for static methods
            filesMock.when(() -> Files.exists(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(eq(filePath))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");
            filesMock.when(() -> Files.size(eq(filePath))).thenReturn(DataSize.ofMegabytes(10).toBytes());

            // Allow basic file operations to work
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("Sample content".getBytes()));

            // Create a CompletableFuture that will throw an ExecutionException
            CompletableFuture<String> mockFuture = mock(CompletableFuture.class);
            when(largeFileProcessor.processLargeFile(any(Path.class))).thenReturn(mockFuture);

            // Make the future.get() throw an ExecutionException
            when(mockFuture.get(anyLong(), any(TimeUnit.class)))
                    .thenThrow(new ExecutionException(new IOException("Execution error during processing")));

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            assertNotNull(result);
            assertEquals("", result.content());
            assertTrue(result.metadata().isEmpty());
        }
    }

    @Test
    void extractContent_whenFileHasComplexMimeType_handlesCorrectly() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("complex.xml"));
        Files.write(filePath, "<root>XML content</root>".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Return a complex MIME type with suffix
            filesMock.when(() -> Files.probeContentType(any(Path.class)))
                    .thenReturn("application/vnd.something+xml");

            // For other method calls, let them pass through to real implementation
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.size(any(Path.class))).thenReturn(100L);
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("<root>XML content</root>".getBytes()));

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            assertNotNull(result);
            assertEquals("XML", result.metadata().get("Document-Type"));
            assertTrue(result.content().contains("XML content"));
        }
    }

    @Test
    void extractContent_whenExceptionInTikaAndBasicExtract_returnsEmptyContent() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("problematic.txt"));
        Files.write(filePath, "Some content".getBytes());

        // Force tika extraction to fail
        doReturn("").when(contentExtractorService).tikaExtractTextContent(any(Path.class), anyMap());

        // Force basic extraction to fail by using a spy that returns empty string
        doReturn("").when(contentExtractorService).basicExtractTextContent(any(Path.class));

        // Act
        DocumentExtractContent result = contentExtractorService.extractContent(filePath);

        // Assert
        assertNotNull(result);
        assertEquals("", result.content());
    }

    @Test
    void extractContent_whenMimeTypeIsNull_fallsBackToExtensionDetection() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("no-mime.pdf"));
        Files.write(filePath, "PDF content".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            // Return null for probeContentType to trigger fallback
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn(null);
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.size(any(Path.class))).thenReturn(100L);
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("PDF content".getBytes()));

            // Mock MimeTypeUtil for extension based detection
            try (MockedStatic<MimeTypeUtil> mimeUtilMock = mockStatic(MimeTypeUtil.class)) {
                mimeUtilMock.when(() -> MimeTypeUtil.getMimeTypeFromExtension(any(Path.class)))
                        .thenReturn("application/pdf");
                mimeUtilMock.when(() -> MimeTypeUtil.sanitizeFilePathForMimeDetection(any(Path.class)))
                        .thenCallRealMethod();

                when(smartPdfExtractor.extractText(any(Path.class)))
                        .thenReturn(new ExtractedText("PDF content from extension detection", false));

                // Act
                DocumentExtractContent result = contentExtractorService.extractContent(filePath);

                // Assert
                assertNotNull(result);
                assertEquals("PDF", result.metadata().get("Document-Type"));
                assertEquals("PDF content from extension detection", result.content());
                verify(smartPdfExtractor).extractText(any(Path.class));
            }
        }
    }

    @Test
    void basicExtractTextContent_success_returnsFileContent() throws Exception {
        // Arrange
        Path textFile = Files.createFile(tempDir.resolve("basic-extract.txt"));
        String expectedContent = "Content for basic extraction test";
        Files.write(textFile, expectedContent.getBytes());

        // Act - call the method directly since it's protected
        String result = contentExtractorService.basicExtractTextContent(textFile);

        // Assert
        assertEquals(expectedContent, result);
    }

    @Test
    void basicExtractTextContent_ioException_returnsEmptyString() throws Exception {
        // Arrange
        Path textFile = Files.createFile(tempDir.resolve("basic-error.txt"));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenThrow(new IOException("File read error"));

            // Act - call the method directly since it's protected
            String result = contentExtractorService.basicExtractTextContent(textFile);

            // Assert
            assertEquals("", result);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "test.doc,application/msword,WORD",
            "test.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,EXCEL_XLSX",
            "test.ppt,application/vnd.ms-powerpoint,POWERPOINT",
            "test.csv,text/csv,CSV",
            "test.xml,application/xml,XML"
    })
    void extractContent_differentFileTypes_detectsCorrectly(String fileName, String mimeType, String expectedType) throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve(fileName));
        Files.write(filePath, "Test content".getBytes());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn(mimeType);
            filesMock.when(() -> Files.size(any(Path.class))).thenReturn(100L);
            filesMock.when(() -> Files.newInputStream(any(Path.class))).thenAnswer(i ->
                    new ByteArrayInputStream("Test content".getBytes()));

            // Mock readAttributes call
            BasicFileAttributes mockAttributes = mock(BasicFileAttributes.class);
            filesMock.when(() -> Files.readAttributes(any(Path.class), eq(BasicFileAttributes.class)))
                    .thenReturn(mockAttributes);

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            assertNotNull(result);
            assertEquals(expectedType, result.metadata().get("Document-Type"));
            assertEquals(mimeType, result.metadata().get("Detected-MIME-Type"));
        }
    }
}
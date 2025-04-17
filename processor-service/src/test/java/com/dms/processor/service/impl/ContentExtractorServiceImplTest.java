package com.dms.processor.service.impl;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.enums.DocumentType;
import com.dms.processor.service.extraction.DocumentExtractorFactory;
import com.dms.processor.service.extraction.DocumentExtractionStrategy;
import com.dms.processor.util.MimeTypeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentExtractorServiceImplTest {

    @Mock
    private DocumentExtractorFactory extractorFactory;

    @Mock
    private DocumentExtractionStrategy extractionStrategy;

    @InjectMocks
    private ContentExtractorServiceImpl contentExtractorService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(extractorFactory, extractionStrategy);
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
    void extractContent_unreadableFile_returnsEmptyContent() throws IOException {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("unreadable.txt"));
        try (var filesMock = mockStatic(Files.class)) {
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
    void extractContent_pdfFile_usesCorrectStrategy() throws Exception {
        // Arrange
        Path pdfFile = Files.createFile(tempDir.resolve("test.pdf"));
        Files.write(pdfFile, "PDF content".getBytes());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", "application/pdf");
        metadata.put("Document-Type", "PDF");
        metadata.put("Document-Type-Display", "PDF Document");

        DocumentExtractContent expectedResult = new DocumentExtractContent("Extracted PDF content", metadata);

        when(extractorFactory.getStrategy(eq(pdfFile), eq("application/pdf"))).thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(pdfFile), eq("application/pdf"))).thenReturn(true);
        when(extractionStrategy.extract(eq(pdfFile), eq("application/pdf"), anyMap())).thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("application/pdf");

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(pdfFile);

            // Assert
            verify(extractorFactory).getStrategy(eq(pdfFile), eq("application/pdf"));
            verify(extractionStrategy).extract(eq(pdfFile), eq("application/pdf"), anyMap());
            assertEquals("Extracted PDF content", result.content());
            assertEquals("PDF", result.metadata().get("Document-Type"));
            assertEquals("application/pdf", result.metadata().get("Detected-MIME-Type"));
        }
    }

    @Test
    void extractContent_docxFile_detectsMimeType() throws Exception {
        // Arrange
        Path docxFile = Files.createFile(tempDir.resolve("test.docx"));
        Files.write(docxFile, "DOCX content".getBytes());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        metadata.put("Document-Type", "WORD_DOCX");
        metadata.put("Document-Type-Display", "Word Document");

        DocumentExtractContent expectedResult = new DocumentExtractContent("Extracted DOCX content", metadata);

        when(extractorFactory.getStrategy(eq(docxFile), eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(docxFile), eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .thenReturn(true);
        when(extractionStrategy.extract(eq(docxFile), eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), anyMap()))
                .thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class)))
                    .thenReturn("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(docxFile);

            // Assert
            assertNotNull(result);
            assertEquals("Extracted DOCX content", result.content());
            assertEquals("WORD_DOCX", result.metadata().get("Document-Type"));
            assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", result.metadata().get("Detected-MIME-Type"));
        }
    }

    @Test
    void extractContent_textFile_extractsTextContent() throws Exception {
        // Arrange
        Path textFile = Files.createFile(tempDir.resolve("test.txt"));
        Files.write(textFile, "Text content for extraction test".getBytes());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", "text/plain");
        metadata.put("Document-Type", "TEXT_PLAIN");
        metadata.put("Document-Type-Display", "Plain Text");

        DocumentExtractContent expectedResult = new DocumentExtractContent("Text content for extraction test", metadata);

        when(extractorFactory.getStrategy(eq(textFile), eq("text/plain"))).thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(textFile), eq("text/plain"))).thenReturn(true);
        when(extractionStrategy.extract(eq(textFile), eq("text/plain"), anyMap())).thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(textFile);

            // Assert
            assertNotNull(result);
            assertEquals("Text content for extraction test", result.content());
            assertEquals("TEXT_PLAIN", result.metadata().get("Document-Type"));
        }
    }

    @Test
    void extractContent_emptyFile_returnsEmptyContent() throws Exception {
        // Arrange
        Path emptyFile = Files.createFile(tempDir.resolve("empty.txt"));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", "text/plain");
        metadata.put("Document-Type", "TEXT_PLAIN");
        metadata.put("Document-Type-Display", "Plain Text");

        DocumentExtractContent expectedResult = new DocumentExtractContent("", metadata);

        when(extractorFactory.getStrategy(eq(emptyFile), eq("text/plain"))).thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(emptyFile), eq("text/plain"))).thenReturn(true);
        when(extractionStrategy.extract(eq(emptyFile), eq("text/plain"), anyMap())).thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(emptyFile);

            // Assert
            assertNotNull(result);
            assertTrue(result.content().isEmpty());
            assertEquals("TEXT_PLAIN", result.metadata().get("Document-Type"));
        }
    }

    @Test
    void extractContent_strategyThrowsException_handlesGracefully() throws Exception {
        // Arrange
        Path pdfFile = Files.createFile(tempDir.resolve("error.pdf"));
        Files.write(pdfFile, "PDF content".getBytes());

        when(extractorFactory.getStrategy(eq(pdfFile), eq("application/pdf"))).thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(pdfFile), eq("application/pdf"))).thenReturn(true);
        when(extractionStrategy.extract(eq(pdfFile), eq("application/pdf"), anyMap()))
                .thenThrow(new RuntimeException("Extraction error"));

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn("application/pdf");

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(pdfFile);

            // Assert
            assertNotNull(result);
            assertEquals("", result.content());
            assertTrue(result.metadata().isEmpty());
        }
    }

    @Test
    void extractContent_specialCharactersInFilename_sanitizesForMimeDetection() throws Exception {
        // Arrange
        Path specialFile = Files.createFile(tempDir.resolve("test@#$%^&.txt"));
        Files.write(specialFile, "Content with special chars in filename".getBytes());

        Path sanitizedPath = tempDir.resolve("test.txt");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", "text/plain");
        metadata.put("Document-Type", "TEXT_PLAIN");
        metadata.put("Document-Type-Display", "Plain Text");

        DocumentExtractContent expectedResult = new DocumentExtractContent("Content with special chars in filename", metadata);

        when(extractorFactory.getStrategy(eq(specialFile), eq("text/plain"))).thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(specialFile), eq("text/plain"))).thenReturn(true);
        when(extractionStrategy.extract(eq(specialFile), eq("text/plain"), anyMap())).thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            try (var mimeUtilMock = mockStatic(MimeTypeUtil.class)) {
                filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.probeContentType(eq(specialFile))).thenReturn(null);
                filesMock.when(() -> Files.probeContentType(eq(sanitizedPath))).thenReturn("text/plain");

                mimeUtilMock.when(() -> MimeTypeUtil.sanitizeFilePathForMimeDetection(eq(specialFile)))
                        .thenReturn(sanitizedPath);

                // Act
                DocumentExtractContent result = contentExtractorService.extractContent(specialFile);

                // Assert
                assertNotNull(result);
                assertFalse(result.content().isEmpty());
                assertEquals("TEXT_PLAIN", result.metadata().get("Document-Type"));
            }
        }
    }

    @Test
    void extractContent_whenMimeTypeIsNull_fallsBackToExtensionDetection() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("no-mime.pdf"));
        Files.write(filePath, "PDF content".getBytes());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", "application/pdf");
        metadata.put("Document-Type", "PDF");
        metadata.put("Document-Type-Display", "PDF Document");

        DocumentExtractContent expectedResult = new DocumentExtractContent("PDF content from extension detection", metadata);

        when(extractorFactory.getStrategy(eq(filePath), eq("application/pdf"))).thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(filePath), eq("application/pdf"))).thenReturn(true);
        when(extractionStrategy.extract(eq(filePath), eq("application/pdf"), anyMap())).thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            try (var mimeUtilMock = mockStatic(MimeTypeUtil.class)) {
                filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn(null);

                mimeUtilMock.when(() -> MimeTypeUtil.getMimeTypeFromExtension(eq(filePath)))
                        .thenReturn("application/pdf");
                mimeUtilMock.when(() -> MimeTypeUtil.sanitizeFilePathForMimeDetection(any(Path.class)))
                        .thenReturn(filePath);

                // Act
                DocumentExtractContent result = contentExtractorService.extractContent(filePath);

                // Assert
                assertNotNull(result);
                assertEquals("PDF", result.metadata().get("Document-Type"));
                assertEquals("PDF content from extension detection", result.content());
            }
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

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", mimeType);
        metadata.put("Document-Type", expectedType);
        metadata.put("Document-Type-Display", DocumentType.valueOf(expectedType).getDisplayName());

        DocumentExtractContent expectedResult = new DocumentExtractContent("Test content", metadata);

        when(extractorFactory.getStrategy(eq(filePath), eq(mimeType))).thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(filePath), eq(mimeType))).thenReturn(true);
        when(extractionStrategy.extract(eq(filePath), eq(mimeType), anyMap())).thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn(mimeType);
            filesMock.when(() -> Files.size(any(Path.class))).thenReturn(100L);
            filesMock.when(() -> Files.newInputStream(any(Path.class)))
                    .thenAnswer(i -> new ByteArrayInputStream("Test content".getBytes()));

            // Act
            DocumentExtractContent result = contentExtractorService.extractContent(filePath);

            // Assert
            assertNotNull(result);
            assertEquals(expectedType, result.metadata().get("Document-Type"));
            assertEquals(mimeType, result.metadata().get("Detected-MIME-Type"));
            assertEquals("Test content", result.content());
        }
    }

    @Test
    void extractContent_whenAllMimeDetectionsFail_handlesDocxFallback() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("test.docx"));
        Path sanitizedPath = tempDir.resolve("sanitized_test.docx");
        Files.write(filePath, "dummy content".getBytes());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        metadata.put("Document-Type", "WORD_DOCX");
        metadata.put("Document-Type-Display", "Word Document");

        DocumentExtractContent expectedResult = new DocumentExtractContent("dummy content", metadata);

        when(extractorFactory.getStrategy(eq(filePath), eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(filePath), eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .thenReturn(true);
        when(extractionStrategy.extract(eq(filePath), eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), anyMap()))
                .thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            try (var mimeUtilMock = mockStatic(MimeTypeUtil.class)) {
                filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.size(any(Path.class))).thenReturn(100L);
                filesMock.when(() -> Files.newInputStream(any(Path.class)))
                        .thenReturn(new ByteArrayInputStream("dummy content".getBytes()));
                filesMock.when(() -> Files.isHidden(any(Path.class))).thenReturn(false);
                filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn(null);

                mimeUtilMock.when(() -> MimeTypeUtil.sanitizeFilePathForMimeDetection(eq(filePath)))
                        .thenReturn(sanitizedPath);
                mimeUtilMock.when(() -> MimeTypeUtil.getMimeTypeFromExtension(any(Path.class)))
                        .thenReturn(null);

                try (var tikaMock = mockConstruction(org.apache.tika.Tika.class,
                        (mock, context) -> when(mock.detect(any(File.class)))
                                .thenThrow(new IOException("Tika detection failed")))) {

                    // Act
                    DocumentExtractContent result = contentExtractorService.extractContent(filePath);

                    // Assert
                    assertNotNull(result);
                    assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            result.metadata().get("Detected-MIME-Type"));
                    assertEquals("WORD_DOCX", result.metadata().get("Document-Type"));
                }
            }
        }
    }

    @Test
    void extractContent_whenAllMimeDetectionsFail_handlesNonDocxFallback() throws Exception {
        // Arrange
        Path filePath = Files.createFile(tempDir.resolve("test.unknown"));
        Path sanitizedPath = tempDir.resolve("sanitized_test.unknown");
        Files.write(filePath, "dummy content".getBytes());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Detected-MIME-Type", "application/octet-stream");
        metadata.put("Document-Type", "UNKNOWN");

        DocumentExtractContent expectedResult = new DocumentExtractContent("dummy content", metadata);

        when(extractorFactory.getStrategy(eq(filePath), eq("application/octet-stream"))).thenReturn(extractionStrategy);
        lenient().when(extractionStrategy.canHandle(eq(filePath), eq("application/octet-stream"))).thenReturn(true);
        when(extractionStrategy.extract(eq(filePath), eq("application/octet-stream"), anyMap())).thenReturn(expectedResult);

        try (var filesMock = mockStatic(Files.class)) {
            try (var mimeUtilMock = mockStatic(MimeTypeUtil.class)) {
                filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.size(any(Path.class))).thenReturn(100L);
                filesMock.when(() -> Files.newInputStream(any(Path.class)))
                        .thenReturn(new ByteArrayInputStream("dummy content".getBytes()));
                filesMock.when(() -> Files.isHidden(any(Path.class))).thenReturn(false);
                filesMock.when(() -> Files.probeContentType(any(Path.class))).thenReturn(null);

                mimeUtilMock.when(() -> MimeTypeUtil.sanitizeFilePathForMimeDetection(eq(filePath)))
                        .thenReturn(sanitizedPath);
                mimeUtilMock.when(() -> MimeTypeUtil.getMimeTypeFromExtension(any(Path.class)))
                        .thenReturn(null);

                try (var tikaMock = mockConstruction(org.apache.tika.Tika.class,
                        (mock, context) -> when(mock.detect(any(File.class)))
                                .thenThrow(new IOException("Tika detection failed")))) {

                    // Act
                    DocumentExtractContent result = contentExtractorService.extractContent(filePath);

                    // Assert
                    assertNotNull(result);
                    assertEquals("application/octet-stream", result.metadata().get("Detected-MIME-Type"));
                    assertEquals("UNKNOWN", result.metadata().get("Document-Type"));
                }
            }
        }
    }
}
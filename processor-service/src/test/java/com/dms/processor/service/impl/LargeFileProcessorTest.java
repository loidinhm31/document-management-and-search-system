package com.dms.processor.service.impl;

import com.dms.processor.config.ThreadPoolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

class LargeFileProcessorTest {

    @Spy
    @InjectMocks
    private LargeFileProcessor largeFileProcessor;

    @Mock
    private ThreadPoolManager threadPoolManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(largeFileProcessor, "chunkSizeMB", 5); // Match default value from @Value
    }

    @Test
    void processLargeFile_ShouldReturnFileContent() throws Exception {
        // Arrange
        Path testFile = createTestFile("test-content");
        String expectedContent = "test-content";

        // Mock the processFileInChunks method
        lenient().doReturn(expectedContent).when(largeFileProcessor).processFileInChunks(testFile);

        // Mock ThreadPoolManager to execute the Callable and return a CompletableFuture
        CompletableFuture<String> mockFuture = CompletableFuture.completedFuture(expectedContent);
        when(threadPoolManager.submitDocumentTask(any(Callable.class))).thenReturn(mockFuture);

        // Act
        CompletableFuture<String> result = largeFileProcessor.processLargeFile(testFile);
        String content = result.get(5, TimeUnit.SECONDS);

        // Assert
        assertEquals(expectedContent, content);
        verify(threadPoolManager, times(1)).submitDocumentTask(any(Callable.class));
    }

    @Test
    void processLargeFile_ShouldHandleExceptions() throws Exception {
        // Arrange
        Path testFile = createTestFile("test-content");
        IOException testException = new IOException("Test exception");

        // Mock ThreadPoolManager to execute the Callable that throws an exception
        when(threadPoolManager.submitDocumentTask(any(Callable.class))).thenAnswer(invocation -> {
            Callable<String> callable = invocation.getArgument(0);
            try {
                callable.call(); // This will call processFileInChunks and throw the exception
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                CompletableFuture<String> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(e);
                return failedFuture;
            }
        });

        // Make processFileInChunks throw an exception
        doThrow(testException).when(largeFileProcessor).processFileInChunks(any(Path.class));

        // Act
        CompletableFuture<String> result = largeFileProcessor.processLargeFile(testFile);

        // Assert
        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> result.get(5, TimeUnit.SECONDS)
        );
        // Check if the cause is the IOException directly or wrapped in a CompletionException
        Throwable cause = exception.getCause();
        if (cause instanceof CompletionException) {
            assertEquals(testException, cause.getCause());
        } else {
            assertEquals(testException, cause);
        }
        verify(threadPoolManager, times(1)).submitDocumentTask(any(Callable.class));
    }

    @Test
    void processFileInChunks_ShouldProcessLargeFilesInChunks() throws IOException {
        // Arrange
        String content = "This is a test content that should be processed in chunks. ";
        content = content.repeat(20); // Make it long enough to be split into chunks
        Path testFile = createTestFile(content);

        // Mock the processChunk method
        doAnswer(invocation -> {
            byte[] buffer = invocation.getArgument(0);
            int bytesRead = invocation.getArgument(1);
            return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        }).when(largeFileProcessor).processChunk(any(byte[].class), anyInt(), any(), any());

        // Act
        String result = largeFileProcessor.processFileInChunks(testFile);

        // Assert
        assertEquals(content, result);
    }

    @Test
    void processFileInChunks_ShouldHandleEmptyFile() throws IOException {
        // Arrange
        Path emptyFile = createTestFile("");

        // Act: This is a test content that should be processed in chunks.
        // Act
        String result = largeFileProcessor.processFileInChunks(emptyFile);

        // Assert
        assertEquals("", result);
    }

    @Test
    void processChunk_ShouldHandleExceptions() throws Exception {
        // Arrange
        Path testFile = createTestFile("test content");
        IOException testException = new IOException("Test exception");

        // Mock ThreadPoolManager to capture the Callable
        ArgumentCaptor<Callable<String>> callableCaptor = ArgumentCaptor.forClass(Callable.class);
        CompletableFuture<String> mockFuture = new CompletableFuture<>();
        when(threadPoolManager.submitDocumentTask(callableCaptor.capture())).thenReturn(mockFuture);

        // Mock processFileInChunks to throw an exception
        doThrow(testException).when(largeFileProcessor).processFileInChunks(any(Path.class));

        // Act
        largeFileProcessor.processLargeFile(testFile);

        // Execute the captured Callable to trigger the exception
        Callable<String> capturedCallable = callableCaptor.getValue();
        try {
            capturedCallable.call();
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            // Assert
            assertTrue(e instanceof CompletionException);
            assertEquals(testException, e.getCause());
        }

        verify(threadPoolManager, times(1)).submitDocumentTask(any(Callable.class));
    }

    private Path createTestFile(String content) throws IOException {
        Path filePath = tempDir.resolve("test-file.txt");
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        return filePath;
    }
}
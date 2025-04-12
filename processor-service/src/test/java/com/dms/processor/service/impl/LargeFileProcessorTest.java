package com.dms.processor.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LargeFileProcessorTest {

    @Spy
    private LargeFileProcessor largeFileProcessor;

    @Mock
    private ExecutorService executorService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(largeFileProcessor, "executorService", executorService);
        ReflectionTestUtils.setField(largeFileProcessor, "chunkSizeMB", 1);

        // Initialize processingTasks map if needed
        if (ReflectionTestUtils.getField(largeFileProcessor, "processingTasks") == null) {
            ReflectionTestUtils.setField(largeFileProcessor, "processingTasks",
                    new java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<String>>());
        }
    }

    @Test
    void processLargeFile_ShouldReturnFileContent() throws Exception {
        // Arrange
        Path testFile = createTestFile("test-content");

        // Mock the processFileInChunks method to return our test content
        doReturn("test-content").when(largeFileProcessor).processFileInChunks(testFile);

        // Set up the mock to execute the runnable directly
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        // Act
        CompletableFuture<String> result = largeFileProcessor.processLargeFile(testFile);
        String content = result.get(5, TimeUnit.SECONDS);

        // Assert
        assertEquals("test-content", content);
        verify(executorService, times(1)).execute(any(Runnable.class));
    }

    @Test
    void processLargeFile_ShouldHandleExceptions() throws Exception {
        // Arrange
        Path testFile = createTestFile("test-content");
        IOException testException = new IOException("Test exception");

        // Set up the mock to execute the runnable but throw an exception
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            // This will execute the Runnable which will try to process the file
            try {
                runnable.run();
            } catch (Exception e) {
                // Do nothing, we'll verify the future is completed exceptionally
            }
            return null;
        }).when(executorService).execute(any(Runnable.class));

        // Make the real method throw an exception when called
        doThrow(testException).when(largeFileProcessor).processFileInChunks(any(Path.class));

        // Act
        CompletableFuture<String> result = largeFileProcessor.processLargeFile(testFile);

        // Assert
        Exception exception = assertThrows(
                ExecutionException.class,
                () -> result.get(5, TimeUnit.SECONDS)
        );
        assertEquals(testException, exception.getCause());
        verify(executorService, times(1)).execute(any(Runnable.class));
    }

    @Test
    void processFileInChunks_ShouldProcessLargeFilesInChunks() throws IOException {
        // Arrange
        String content = "This is a test content that should be processed in chunks. ";
        content = content.repeat(20); // make it long enough to be split into chunks
        Path testFile = createTestFile(content);

        // Mock the processChunk method since it depends on external parser
        doAnswer(invocation -> {
            byte[] buffer = invocation.getArgument(0);
            int bytesRead = invocation.getArgument(1);
            // Return the string representation of the buffer (trimmed to bytesRead)
            return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        }).when(largeFileProcessor).processChunk(any(byte[].class), anyInt(), any(), any());

        // Act
        String result = largeFileProcessor.processFileInChunks(testFile);

        // Assert
        assertEquals(content, result);
    }

    @Test
    void cancelProcessing_ShouldCancelTask() {
        // Arrange
        String fileId = "test-file-id";
        CompletableFuture<String> mockFuture = mock(CompletableFuture.class);

        // Access the processingTasks map via reflection
        var processingTasks = (java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<String>>)
                ReflectionTestUtils.getField(largeFileProcessor, "processingTasks");

        // Add the mock future directly to the map
        processingTasks.put(fileId, mockFuture);

        // Act
        largeFileProcessor.cancelProcessing(fileId);

        // Assert
        verify(mockFuture, times(1)).cancel(true);
    }

    @Test
    void getProcessingProgress_ShouldReturnProgress() {
        // Arrange
        String existingFileId = "test-file-id";
        String nonExistingFileId = "non-existing-file-id";
        CompletableFuture<String> mockFuture = new CompletableFuture<>();

        // Add the mock future to the processing tasks map
        var processingTasks = (java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<String>>)
                ReflectionTestUtils.getField(largeFileProcessor, "processingTasks");
        processingTasks.put(existingFileId, mockFuture);

        // Act & Assert
        assertEquals(-1, largeFileProcessor.getProcessingProgress(existingFileId));
        assertEquals(100, largeFileProcessor.getProcessingProgress(nonExistingFileId));
    }

    @Test
    void processFileInChunks_ShouldHandleEmptyFile() throws IOException {
        // Arrange
        Path emptyFile = createTestFile("");

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

        // Return a CompletableFuture that we control
        CompletableFuture<String> future = largeFileProcessor.processLargeFile(testFile);

        // Capture the Runnable that will be executed
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executorService).execute(runnableCaptor.capture());

        // Make processFileInChunks throw an exception when it's called
        doThrow(testException).when(largeFileProcessor).processFileInChunks(any(Path.class));

        // Execute the captured Runnable to trigger the exception
        runnableCaptor.getValue().run();

        // Assert that the future completes exceptionally with our exception
        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> future.get()
        );

        assertEquals(testException, exception.getCause());
    }

    private Path createTestFile(String content) throws IOException {
        Path filePath = tempDir.resolve("test-file.txt");
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        return filePath;
    }
}
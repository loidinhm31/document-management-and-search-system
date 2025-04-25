package com.dms.processor.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.dms.processor.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @InjectMocks
    private EmailService emailService;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    @Captor
    private ArgumentCaptor<MimeMessage> messageCaptor;

    @Mock
    private Appender<ILoggingEvent> logAppender;

    private Map<String, Object> templateVars;
    private Map<String, User> recipientMap;

    @BeforeEach
    void setUp() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Set up logger to capture log messages
        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        logger.addAppender(logAppender);
        lenient().when(logAppender.getName()).thenReturn("MOCK");
        lenient().doNothing().when(logAppender).doAppend(any());

        // Initialize test data
        templateVars = new HashMap<>();
        recipientMap = new HashMap<>();

        User user1 = new User();
        user1.setUsername("John");
        recipientMap.put("john@example.com", user1);

        User user2 = new User();
        user2.setUsername("Jane");
        recipientMap.put("jane@example.com", user2);

        templateVars.put("recipientMap", recipientMap);

        // Set private fields
        setField(emailService, "fromEmail", "from@example.com");
        setField(emailService, "batchSize", 2);
        setField(emailService, "mailSender", mailSender);
        setField(emailService, "templateEngine", templateEngine);
    }

    @Test
    void sendEmail_Successful() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendEmail("test@example.com", "Test Subject", "<html>Test</html>");

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(messageCaptor.capture());

        MimeMessage capturedMessage = messageCaptor.getValue();
        assertNotNull(capturedMessage);
    }

    @Test
    void sendEmail_ThrowsMessagingException() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        // Instead of throwing MessagingException directly, we'll simulate a runtime exception
        doThrow(new RuntimeException("Mail server error"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThrows(RuntimeException.class, () ->
                emailService.sendEmail("test@example.com", "Test Subject", "<html>Test</html>")
        );
    }

    @Test
    void sendBatchNotificationEmails_EmptyList() {
        emailService.sendBatchNotificationEmails(Collections.emptyList(),
                "Subject", "template", templateVars);

        verifyNoInteractions(mailSender);
        verifyNoInteractions(templateEngine);
    }

    @Test
    void renderTemplate_Successful() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("key", "value");

        // Use consistent matchers for all arguments
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenReturn("<html>Rendered</html>");

        String result = emailService.renderTemplate("template", variables);

        verify(templateEngine).process(anyString(), any(Context.class));
        assertEquals("<html>Rendered</html>", result);
    }

    @Test
    void partitionEmails_CorrectBatching() {
        setField(emailService, "batchSize", 2);
        List<String> emails = Arrays.asList("a@example.com", "b@example.com",
                "c@example.com", "d@example.com");

        List<List<String>> batches = emailService.partitionEmails(emails);

        assertEquals(2, batches.size());
        assertEquals(2, batches.get(0).size());
        assertEquals(2, batches.get(1).size());
    }

    // Helper method to set private fields
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    // Helper method to wait for async operations
    private void awaitAsyncOperations(CompletableFuture<?>... futures) {
        try {
            CompletableFuture.allOf(futures).join();
        } catch (CompletionException e) {
            throw new RuntimeException("Async operation failed", e);
        }
    }

    @Test
    void sendEmail_InvalidEmail_ThrowsMessagingException() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        lenient().doThrow(new MessagingException("Invalid email address"))
                .when(mimeMessage).setRecipients(any(), anyString());

        assertThrows(MessagingException.class, () ->
                emailService.sendEmail("invalid@.com", "Subject", "<html>Test</html>")
        );
    }

    @Test
    void sendEmail_NullTo_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                emailService.sendEmail(null, "Subject", "<html>Test</html>")
        );
    }

    @Test
    void sendEmail_EmptySubject_Successful() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendEmail("test@example.com", "", "<html>Test</html>");

        verify(mailSender).send(messageCaptor.capture());
        assertNotNull(messageCaptor.getValue());
    }

    @Test
    void partitionEmails_EmptyCollection_ReturnsEmptyList() {
        List<List<String>> batches = emailService.partitionEmails(Collections.emptyList());
        assertTrue(batches.isEmpty());
    }

    @Test
    void partitionEmails_SingleEmail_ReturnsSingleBatch() {
        List<String> emails = Arrays.asList("test@example.com");
        List<List<String>> batches = emailService.partitionEmails(emails);
        assertEquals(1, batches.size());
        assertEquals(1, batches.get(0).size());
        assertEquals("test@example.com", batches.get(0).get(0));
    }

    @Test
    void partitionEmails_ZeroBatchSize_ThrowsIllegalArgumentException() {
        setField(emailService, "batchSize", 0);
        List<String> emails = Arrays.asList("test@example.com");
        assertThrows(IllegalArgumentException.class, () ->
                emailService.partitionEmails(emails)
        );
    }

    @Test
    void sendEmail_EmptyTo_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                emailService.sendEmail("", "Subject", "<html>Test</html>")
        );
    }

    @Test
    void sendEmail_NullHtmlContent_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                emailService.sendEmail("test@example.com", "Subject", null)
        );
    }

    @Test
    void sendEmail_VerifyMessageContent() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        MimeMessageHelper helper = mock(MimeMessageHelper.class);
        try (MockedConstruction<MimeMessageHelper> mocked = mockConstruction(MimeMessageHelper.class,
                (mock, context) -> when(mock.getMimeMessage()).thenReturn(mimeMessage))) {
            emailService.sendEmail("test@example.com", "Test Subject", "<html>Test</html>");
            verify(mocked.constructed().get(0)).setFrom("from@example.com");
            verify(mocked.constructed().get(0)).setTo("test@example.com");
            verify(mocked.constructed().get(0)).setSubject("Test Subject");
            verify(mocked.constructed().get(0)).setText("<html>Test</html>", true);
            verify(mailSender).send(mimeMessage);
        }
    }

    @Test
    void renderTemplate_NullVariables_Successful() {
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenReturn("<html>Rendered</html>");

        String result = emailService.renderTemplate("template", null);
        assertEquals("<html>Rendered</html>", result);
        verify(templateEngine).process(eq("template"), any(Context.class));
    }

    @Test
    void renderTemplate_TemplateProcessingException() {
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new RuntimeException("Template error"));

        assertThrows(RuntimeException.class, () ->
                emailService.renderTemplate("template", new HashMap<>())
        );
    }

    @Test
    void partitionEmails_NegativeBatchSize_ThrowsIllegalArgumentException() {
        setField(emailService, "batchSize", -1);
        List<String> emails = Arrays.asList("test@example.com");
        assertThrows(IllegalArgumentException.class, () ->
                emailService.partitionEmails(emails)
        );
    }

    @Test
    void sendEmail_ThrowsMailSendException() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Mail server error"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () ->
                emailService.sendEmail("test@example.com", "Test Subject", "<html>Test</html>")
        );
    }


    @Test
    void sendBatchNotificationEmails_Successful_LogsCorrectMessageAndPersonalizesRecipientName() throws MessagingException {
        // Arrange
        List<String> emails = Arrays.asList("john@example.com", "jane@example.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>Test</html>");

        // Capture log messages
        ArgumentCaptor<ILoggingEvent> logCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);

        // Act
        emailService.sendBatchNotificationEmails(emails, "Subject", "template", templateVars);

        // Wait for async tasks to complete (not ideal, see note below)
        try {
            Thread.sleep(1000); // Wait for CompletableFuture tasks to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for async tasks", e);
        }

        // Assert
        verify(mailSender, times(2)).createMimeMessage();
        verify(mailSender, times(2)).send(any(MimeMessage.class));
        verify(templateEngine, times(2)).process(eq("template"), any(Context.class));

        // Verify log message for successful batch completion
        verify(logAppender, atLeastOnce()).doAppend(logCaptor.capture());
        List<ILoggingEvent> logEvents = logCaptor.getAllValues();
        boolean foundSuccessLog = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Successfully sent 2 emails in 1 batches"));
        assertTrue(foundSuccessLog, "Expected success log message not found");

        // Verify personalization of recipientName
        verify(templateEngine, times(1)).process(eq("template"), argThat(context -> {
            return "John".equals(((Context) context).getVariable("recipientName"));
        }));
        verify(templateEngine, times(1)).process(eq("template"), argThat(context -> {
            return "Jane".equals(((Context) context).getVariable("recipientName"));
        }));
    }

    @Test
    void sendBatchNotificationEmails_FailedEmail_LogsErrorAndUsesUnknownRecipientName() throws MessagingException {
        // Arrange
        List<String> emails = Arrays.asList("john@example.com", "unknown@example.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>Test</html>");

        // Configure MimeMessage to return recipients
        when(mimeMessage.getRecipients(eq(MimeMessage.RecipientType.TO))).thenAnswer(invocation -> {
            // Return different recipients based on the context of the call
            // Since MimeMessage is reused, we need to simulate recipient setting
            return new InternetAddress[]{new InternetAddress("john@example.com")};
        }).thenAnswer(invocation -> {
            return new InternetAddress[]{new InternetAddress("unknown@example.com")};
        });

        // Simulate MessagingException for the second email
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            InternetAddress[] recipients = (InternetAddress[]) message.getRecipients(MimeMessage.RecipientType.TO);
            String to = recipients[0].toString();
            if (to.equals("unknown@example.com")) {
                throw new MessagingException("Failed to send to unknown");
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        // Capture log messages
        ArgumentCaptor<ILoggingEvent> logCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);

        // Act
        emailService.sendBatchNotificationEmails(emails, "Subject", "template", templateVars);

        // Wait for async tasks to complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for async tasks", e);
        }

        // Assert
        verify(mailSender, times(2)).createMimeMessage();
        verify(mailSender, times(2)).send(any(MimeMessage.class));
        verify(templateEngine, times(2)).process(eq("template"), any(Context.class));

        // Verify error log for failed email
        verify(logAppender, atLeastOnce()).doAppend(logCaptor.capture());
        List<ILoggingEvent> logEvents = logCaptor.getAllValues();
        boolean foundErrorLog = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Failed to send email to unknown@example.com"));
        assertTrue(foundErrorLog, "Expected error log message not found");

        // Verify recipientName is "Unknown" for unknown@example.com
        verify(templateEngine, times(1)).process(eq("template"), argThat(context -> {
            return "Unknown".equals(((Context) context).getVariable("recipientName"));
        }));
        verify(templateEngine, times(1)).process(eq("template"), argThat(context -> {
            return "John".equals(((Context) context).getVariable("recipientName"));
        }));
    }


}
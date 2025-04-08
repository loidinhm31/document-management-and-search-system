package com.dms.processor.service.impl;

import com.dms.processor.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

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

    private Map<String, Object> templateVars;
    private Map<String, User> recipientMap;

    @BeforeEach
    void setUp() {
        // Set private fields using reflection or direct access
        emailService = new EmailService();
        MockitoAnnotations.openMocks(this);

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
        setField(emailService, "batchSize", 1); // Small batch size for testing
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
    void sendBatchNotificationEmails_SingleBatch() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("template"), any(Context.class)))
                .thenReturn("<html>Test</html>");

        List<String> emails = Arrays.asList("john@example.com");
        emailService.sendBatchNotificationEmails(emails, "Subject", "template", templateVars);

        // Wait for async operations to complete
        awaitAsyncOperations();

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(templateEngine, times(1)).process(eq("template"), any(Context.class));
    }

    @Test
    void sendBatchNotificationEmails_MultipleBatches() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("template"), any(Context.class)))
                .thenReturn("<html>Test</html>");

        List<String> emails = Arrays.asList("john@example.com", "jane@example.com");
        emailService.sendBatchNotificationEmails(emails, "Subject", "template", templateVars);

        // Wait for async operations to complete
        awaitAsyncOperations();

        verify(mailSender, times(2)).send(any(MimeMessage.class));
        verify(templateEngine, times(2)).process(eq("template"), any(Context.class));
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
    private void awaitAsyncOperations() {
        try {
            Thread.sleep(1000); // Wait for async operations to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
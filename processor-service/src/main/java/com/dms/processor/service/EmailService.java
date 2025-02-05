package com.dms.processor.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.batch-size:50}")
    private int batchSize;

    public void sendBatchNotificationEmails(Collection<String> toEmails, String subject,
                                            String templateName, Map<String, Object> templateVars) {
        if (toEmails == null || toEmails.isEmpty()) {
            log.info("No email recipients provided");
            return;
        }

        // Prepare the email content once
        Context context = new Context();
        context.setVariables(templateVars);
        String htmlContent = templateEngine.process(templateName, context);

        // Split recipients into batches
        List<List<String>> batches = toEmails.stream()
                .collect(Collectors.groupingBy(email ->
                        toEmails.stream().toList().indexOf(email) / batchSize))
                .values()
                .stream()
                .toList();

        // Process each batch asynchronously
        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() ->
                        sendBatch(batch, subject, htmlContent)))
                .toList();

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error sending batch emails", throwable);
                    } else {
                        log.info("Successfully sent {} emails in {} batches",
                                toEmails.size(), batches.size());
                    }
                });
    }

    private void sendBatch(List<String> batchEmails, String subject, String htmlContent) {
        try {
            for (String email : batchEmails) {
                try {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                    helper.setFrom(fromEmail);
                    helper.setTo(email);
                    helper.setSubject(subject);
                    helper.setText(htmlContent, true);

                    mailSender.send(message);
                    log.debug("Sent email to {}", email);
                } catch (MessagingException e) {
                    log.error("Failed to send email to {}", email, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process email batch", e);
            throw new RuntimeException("Failed to send batch emails", e);
        }
    }
}
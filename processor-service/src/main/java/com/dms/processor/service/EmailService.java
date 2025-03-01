package com.dms.processor.service;

import com.dms.processor.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmailService {
    @Value("${app.email.batch-size:50}")
    private int batchSize;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mail-sender.from-email}")
    private String fromEmail;

    public void sendEmail(String to, String subject, String htmlContent) throws MessagingException {
        log.info("Sending email");
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

//        mailSender.send(message);
        log.info("Email sent successfully to: {}", to);
    }

    /**
     * Sends notification emails in batches
     */
    protected void sendBatchNotificationEmails(Collection<String> toEmails, String subject,
                                     String templateName, Map<String, Object> templateVars) {
        if (toEmails == null || toEmails.isEmpty()) {
            log.info("No email recipients provided");
            return;
        }

        Map<String, User> recipientMap = (Map<String, User>) templateVars.get("recipientMap");

        // Split recipients into batches
        List<List<String>> batches = partitionEmails(toEmails);

        // Process each batch asynchronously
        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() ->
                        processBatch(batch, subject, templateName, templateVars, recipientMap)))
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

    /**
     * Divides emails into batches of the configured size
     */
    private List<List<String>> partitionEmails(Collection<String> emails) {
        return emails.stream()
                .collect(Collectors.groupingBy(email ->
                        emails.stream().toList().indexOf(email) / batchSize))
                .values()
                .stream()
                .toList();
    }

    /**
     * Processes a batch of emails
     */
    private void processBatch(List<String> batchEmails, String subject, String templateName,
                              Map<String, Object> templateVars, Map<String, User> recipientMap) {
        try {
            for (String email : batchEmails) {
                try {
                    // Create a copy of template vars for this specific recipient
                    Map<String, Object> personalizedVars = new HashMap<>(templateVars);
                    User recipient = recipientMap.get(email);
                    personalizedVars.put("recipientName", recipient.getUsername());

                    // Prepare email content
                    String htmlContent = renderTemplate(templateName, personalizedVars);
                    sendEmail(email, subject, htmlContent);
                } catch (MessagingException e) {
                    log.error("Failed to send email to {}", email, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process email batch", e);
            throw new RuntimeException("Failed to send batch emails", e);
        }
    }

    /**
     * Renders an email template with the provided variables
     */
    protected String renderTemplate(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

}
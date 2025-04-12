package com.dms.processor.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;

import java.util.UUID;

@Slf4j
public class HandleRepublishMessageRecoverer extends RepublishMessageRecoverer {

    private final String deadLetterExchange;
    private final RabbitTemplate rabbitTemplate;

    public HandleRepublishMessageRecoverer(RabbitTemplate rabbitTemplate, String deadLetterExchange) {
        super(rabbitTemplate, deadLetterExchange);
        this.deadLetterExchange = deadLetterExchange;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        MessageProperties props = message.getMessageProperties();
        String queueName = props.getConsumerQueue();

        // Don't republish messages that come from a DLQ to avoid infinite loops
        if (queueName != null && queueName.endsWith("-dlq")) {
            log.warn("Not republishing message from DLQ {}, breaking potential loop", queueName);
            return;
        }

        String routingKey;

        // Determine appropriate routing key based on queue name
        if (StringUtils.contains(queueName, "email-auth")) {
            routingKey = "dead.letter.routing-key" + ".email-auth";
        } else if (StringUtils.contains(queueName, "email.document")) {
            routingKey = "dead.letter.routing-key" + ".email.document";
        } else if (StringUtils.contains(queueName, "document-process")) {
            routingKey = "dead.letter.routing-key" + ".document";
        } else {
            routingKey = "dead.letter.routing-key";
        }

        // Extract the root cause for better error reporting
        Throwable rootCause = extractRootCause(cause);

        // Add helpful headers for troubleshooting
        props.setHeader("x-original-queue", queueName);
        props.setHeader("x-exception-message", rootCause.getMessage());
        props.setHeader("x-exception-type", rootCause.getClass().getName());
        props.setHeader("x-original-exception-type", cause.getClass().getName());

        // Create a simplified stack trace
        StringBuilder stackTraceBuilder = new StringBuilder();
        // Add the root cause stack trace first
        stackTraceBuilder.append("Root cause: ").append(rootCause.getClass().getName())
                .append(": ").append(rootCause.getMessage()).append("\n");
        for (StackTraceElement element : rootCause.getStackTrace()) {
            stackTraceBuilder.append(element.toString()).append("\n");
        }
        // Also add full exception stack trace
        stackTraceBuilder.append("\nFull stacktrace:\n");
        for (StackTraceElement element : cause.getStackTrace()) {
            stackTraceBuilder.append(element.toString()).append("\n");
        }
        props.setHeader("x-exception-stacktrace", stackTraceBuilder.toString());

        // Ensure message ID is set
        if (props.getMessageId() == null) {
            String generatedId = UUID.randomUUID().toString();
            props.setMessageId(generatedId);
            log.info("Generated message ID for failed message: {}", generatedId);
        }

        // Record original publish timestamp if available
        if (props.getTimestamp() != null) {
            props.setHeader("x-original-timestamp", props.getTimestamp().getTime());
        }

        // Send to the dead letter exchange
        rabbitTemplate.send(deadLetterExchange, routingKey, message);
        log.warn("Republishing failed message to exchange '{}' with routing key '{}', message ID: '{}'",
                deadLetterExchange, routingKey, props.getMessageId());
    }

    /**
     * Extract the root cause from a nested exception
     */
    private Throwable extractRootCause(Throwable throwable) {
        Throwable cause = throwable;

        // Unwrap Spring's ListenerExecutionFailedException to get to the actual error
        if (cause.getCause() != null) {
            Throwable rootCause = cause.getCause();
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            return rootCause;
        }

        return cause;
    }
}
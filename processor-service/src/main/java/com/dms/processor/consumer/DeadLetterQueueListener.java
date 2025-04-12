package com.dms.processor.consumer;

import com.dms.processor.model.FailedMessage;
import com.dms.processor.repository.FailedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeadLetterQueueListener {

    private final FailedMessageRepository failedMessageRepository;

    @RabbitListener(queues = {
            "${rabbitmq.queues.document-process-dlq}",
            "${rabbitmq.queues.email-document-dlq}",
            "${rabbitmq.queues.email-auth-dlq}"
    }, containerFactory = "dlqListenerContainerFactory")
    public void processFailedMessages(Message failedMessage) {
        try {
            MessageProperties props = failedMessage.getMessageProperties();

            // Extract message properties and log them - safely converting header values
            String originalQueue = getHeaderAsString(props, "x-original-queue");
            String error = getHeaderAsString(props, "x-exception-message");

            log.info("Processing failed message. Queue: {}, DeliveryTag: {}, Error: {}",
                    originalQueue, props.getDeliveryTag(), error);

            // Get messageId or generate one if null
            String messageId = props.getMessageId();
            if (messageId == null) {
                // Generate a deterministic ID based on message content and headers
                messageId = generateFallbackMessageId(failedMessage, props);
                log.info("Original messageId was null, generated fallback ID: {}", messageId);
            }

            // Check if this message was already processed
            Optional<FailedMessage> existingMessage =
                    failedMessageRepository.findByOriginalMessageId(messageId);

            if (existingMessage.isPresent()) {
                FailedMessage existingFailedMessage = existingMessage.get();
                existingFailedMessage.setRetryCount(existingFailedMessage.getRetryCount() + 1);
                failedMessageRepository.save(existingFailedMessage);
                log.warn("Message already processed and stored, delivery tag: {}, id: {}",
                        props.getDeliveryTag(), messageId);
                return;
            }

            // Extract message properties - safely converting header values
            String stackTrace = getHeaderAsString(props, "x-exception-stacktrace");
            String routingKey = props.getReceivedRoutingKey();

            // Convert message body to string
            String messageBody = new String(failedMessage.getBody(), StandardCharsets.UTF_8);
            log.info("Processing failed message. Body: {}", messageBody);

            // Create headers map - converting all values to String to avoid LongString issues
            Map<String, Object> safeHeaders = new HashMap<>();
            if (props.getHeaders() != null) {
                props.getHeaders().forEach((key, value) -> {
                    safeHeaders.put(key, value != null ? value.toString() : null);
                });
            }

            // Build failed message document
            FailedMessage failedMessageDoc = FailedMessage.builder()
                    .originalMessageId(messageId)
                    .originalQueue(originalQueue)
                    .routingKey(routingKey)
                    .errorMessage(error)
                    .stackTrace(stackTrace)
                    .messageBody(messageBody)
                    .headers(safeHeaders)
                    .retryCount(0)
                    .status(FailedMessage.FailedMessageStatus.NEW)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            // Save to MongoDB
            failedMessageRepository.save(failedMessageDoc);

            log.info("Failed message saved to MongoDB. Original queue: {}, Error: {}",
                    originalQueue, error);

        } catch (Exception e) {
            log.error("Error processing failed message", e);
            // Here you might want to implement a fallback mechanism
            // such as saving to a backup storage or sending alerts
        }
    }

    /**
     * Generate a fallback message ID when the original messageId is null
     * This creates a deterministic ID based on message content and headers
     */
    private String generateFallbackMessageId(Message message, MessageProperties props) {
        StringBuilder idBuilder = new StringBuilder();

        // Add queue name if available
        if (props.getConsumerQueue() != null) {
            idBuilder.append(props.getConsumerQueue()).append(":");
        }

        // Add delivery tag
        idBuilder.append(props.getDeliveryTag()).append(":");

        // Add timestamp if available
        if (props.getTimestamp() != null) {
            idBuilder.append(props.getTimestamp().getTime()).append(":");
        } else {
            idBuilder.append(System.currentTimeMillis()).append(":");
        }

        // Add a hash of the message body content if available
        if (message.getBody() != null && message.getBody().length > 0) {
            String bodyContent = new String(message.getBody(), StandardCharsets.UTF_8);
            idBuilder.append(bodyContent.hashCode()).append(":");
        }

        // Add a random UUID to ensure uniqueness
        idBuilder.append(UUID.randomUUID().toString());

        return idBuilder.toString();
    }

    /**
     * Helper method to safely get string headers regardless of their object type
     */
    private String getHeaderAsString(MessageProperties props, String headerName) {
        Object headerValue = props.getHeader(headerName);
        if (headerValue == null) {
            return null;
        }

        // Handle any object type by converting to string
        return headerValue.toString();
    }
}
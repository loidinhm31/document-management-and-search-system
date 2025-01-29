package com.dms.processor.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@Document(collection = "failed_messages")
public class FailedMessage {
    @Id
    private String id;

    @Field("original_message_id")
    private String originalMessageId;

    @Field("original_queue")
    private String originalQueue;

    @Field("routing_key")
    private String routingKey;

    @Field("error_message")
    private String errorMessage;

    @Field("stack_trace")
    private String stackTrace;

    @Field("message_body")
    private String messageBody;

    @Field("headers")
    private Map<String, Object> headers;

    @Field("retry_count")
    private int retryCount;

    @Indexed
    @Field("status")
    private FailedMessageStatus status;

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;

    @Field("last_retry_at")
    private Date lastRetryAt;

    public enum FailedMessageStatus {
        NEW,
        RETRY_SCHEDULED,
        RETRY_IN_PROGRESS,
        RETRY_FAILED,
        RESOLVED,
        ARCHIVED
    }
}
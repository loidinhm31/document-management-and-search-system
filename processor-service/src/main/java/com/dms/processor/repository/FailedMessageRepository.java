package com.dms.processor.repository;

import com.dms.processor.model.FailedMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FailedMessageRepository extends MongoRepository<FailedMessage, String> {
    List<FailedMessage> findByStatus(FailedMessage.FailedMessageStatus status);

    @Query("{'status': ?0, 'retryCount': {$lt: ?1}}")
    List<FailedMessage> findRetryableMessages(FailedMessage.FailedMessageStatus status, int maxRetries);

    Optional<FailedMessage> findByOriginalMessageId(String originalMessageId);
}
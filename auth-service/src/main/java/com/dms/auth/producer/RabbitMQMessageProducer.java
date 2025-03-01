package com.dms.auth.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

/**
 * Generic message producer for RabbitMQ.
 * This is a low-level component that should be used by higher-level services.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitMQMessageProducer {

    private final AmqpTemplate amqpTemplate;

    /**
     * Publish a message to a RabbitMQ exchange with a specific routing key
     *
     * @param payload The message payload
     * @param exchange The exchange to publish to
     * @param routingKey The routing key to use
     */
    public void publish(Object payload, String exchange, String routingKey) {
        log.info("Publishing to {} using routingKey {}. Payload type: {}",
                exchange, routingKey, payload.getClass().getSimpleName());

        amqpTemplate.convertAndSend(exchange, routingKey, payload);

        log.info("Published to {} using routingKey {}", exchange, routingKey);
    }
}
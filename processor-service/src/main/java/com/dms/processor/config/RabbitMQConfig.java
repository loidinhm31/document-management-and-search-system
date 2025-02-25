package com.dms.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class RabbitMQConfig {

    @Value("${rabbitmq.exchanges.internal}")
    private String internalExchange;

    @Value("${rabbitmq.exchanges.dlx}")
    private String deadLetterExchange;

    @Value("${rabbitmq.exchanges.notification}")
    private String notificationExchange;

    @Value("${rabbitmq.queues.document-sync}")
    private String documentSyncQueue;

    @Value("${rabbitmq.queues.document-sync-dlq}")
    private String documentSyncDlq;

    @Value("${rabbitmq.queues.notification}")
    private String notificationQueue;

    @Value("${rabbitmq.queues.notification-dlq}")
    private String notificationDlq;

    @Value("${rabbitmq.routing-keys.internal-document-sync}")
    private String internalNotificationRoutingKey;

    @Value("${rabbitmq.routing-keys.notification}")
    private String notificationRoutingKey;

    @Value("${rabbitmq.routing-keys.dlq}")
    private String deadLetterRoutingKey;

    @Value("${rabbitmq.queues.otp}")
    private String otpQueue;

    @Value("${rabbitmq.routing-keys.otp}")
    private String otpRoutingKey;

    @Value("${rabbitmq.queues.password-reset}")
    private String passwordResetQueue;

    @Value("${rabbitmq.routing-keys.password-reset}")
    private String passwordResetRoutingKey;

    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public RabbitMQConfig(ConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    @Bean
    public TopicExchange internalTopicExchange() {
        return new TopicExchange(this.internalExchange);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(this.deadLetterExchange);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(this.notificationExchange);
    }

    @Bean
    public Queue documentSyncQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchange);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey);
        args.put("x-message-ttl", 300000); // 5 minutes
        args.put("x-max-length", 10000);
        return new Queue(documentSyncQueue, true, false, false, args);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(documentSyncDlq);
    }

    @Bean
    public Queue notificationQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchange);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey + ".notification");
        args.put("x-message-ttl", 300000); // 5 minutes
        args.put("x-max-length", 10000);
        return new Queue(notificationQueue, true, false, false, args);
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return new Queue(notificationDlq);
    }

    @Bean
    public Binding internalToNotificationBinding() {
        return BindingBuilder
                .bind(documentSyncQueue())
                .to(internalTopicExchange())
                .with(internalNotificationRoutingKey);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(deadLetterRoutingKey);
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(notificationRoutingKey);
    }

    @Bean
    public Binding notificationDeadLetterBinding() {
        return BindingBuilder
                .bind(notificationDeadLetterQueue())
                .to(deadLetterExchange())
                .with(deadLetterRoutingKey + ".notification");
    }


    @Bean
    public Queue otpQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchange);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey + ".otp");
        args.put("x-message-ttl", 300000); // 5 minutes
        args.put("x-max-length", 10000);
        return new Queue(otpQueue, true, false, false, args);
    }

    @Bean
    public Binding otpBinding() {
        return BindingBuilder
                .bind(otpQueue())
                .to(notificationExchange())
                .with(otpRoutingKey);
    }

    @Bean
    public Queue passwordResetQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchange);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey + ".password-reset");
        args.put("x-message-ttl", 300000); // 5 minutes
        args.put("x-max-length", 10000);
        return new Queue(passwordResetQueue, true, false, false, args);
    }

    @Bean
    public Binding passwordResetBinding() {
        return BindingBuilder
                .bind(passwordResetQueue())
                .to(notificationExchange())
                .with(passwordResetRoutingKey);
    }

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(messageRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonConverter());
        factory.setAdviceChain(retryInterceptor());
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }

    @Bean
    public MessageRecoverer messageRecoverer() {
        return new RepublishMessageRecoverer(rabbitTemplate(), deadLetterExchange, deadLetterRoutingKey);
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonConverter());
        template.setConfirmCallback((correlation, ack, reason) -> {
            if (!ack) {
                log.error("Message delivery failed: {}", reason);
            }
        });
        template.setReturnsCallback(returned -> {
            log.error("Message returned: {}", returned.getMessage());
        });
        return template;
    }

    @Bean
    public MessageConverter jacksonConverter() {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
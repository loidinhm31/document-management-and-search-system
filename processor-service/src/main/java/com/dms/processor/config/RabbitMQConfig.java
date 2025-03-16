package com.dms.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;

@Configuration
@Slf4j
public class RabbitMQConfig {

    // Exchange names
    @Value("${rabbitmq.exchanges.document}")
    private String documentExchange;

    @Value("${rabbitmq.exchanges.notification}")
    private String notificationExchange;

    @Value("${rabbitmq.exchanges.dlx}")
    private String deadLetterExchange;

    // Queue names
    @Value("${rabbitmq.queues.document-process}")
    private String documentProcessQueue;

    @Value("${rabbitmq.queues.document-process-dlq}")
    private String documentProcessDlq;

    @Value("${rabbitmq.queues.email-document}")
    private String emailDocumentQueue;

    @Value("${rabbitmq.queues.email-document-dlq}")
    private String emailDocumentDlq;

    @Value("${rabbitmq.queues.email-auth}")
    private String emailAuthQueue;

    @Value("${rabbitmq.queues.email-auth-dlq}")
    private String emailAuthDlq;

    // Routing key
    @Value("${rabbitmq.routing-keys.document-process}")
    private String documentProcessRoutingKey;

    @Value("${rabbitmq.routing-keys.email-auth}")
    private String emailAuthRoutingKey;

    @Value("${rabbitmq.routing-keys.dead-letter}")
    private String deadLetterRoutingKey;

    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public RabbitMQConfig(ConnectionFactory connectionFactory,
                          ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    // Exchanges

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(this.documentExchange);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(this.notificationExchange);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(this.deadLetterExchange);
    }

    // Queues
    @Bean
    public Queue documentProcessQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchange);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey + ".document");
        args.put("x-message-ttl", 300000); // 5 minutes
        args.put("x-max-length", 10000);
        return new Queue(documentProcessQueue, true, false, false, args);
    }

    @Bean
    public Queue documentProcessDlq() {
        return new Queue(documentProcessDlq);
    }

    @Bean
    public Queue emailDocumentQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchange);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey + ".email-document");
        args.put("x-message-ttl", 300000); // 5 minutes
        args.put("x-max-length", 10000);
        return new Queue(emailDocumentQueue, true, false, false, args);
    }

    @Bean
    public Queue emailDocumentDlq() {
        return new Queue(emailDocumentDlq);
    }


    @Bean
    public Queue emailAuthQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchange);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey + ".email-auth");
        args.put("x-message-ttl", 300000); // 5 minutes
        args.put("x-max-length", 10000);
        return new Queue(emailAuthQueue, true, false, false, args);
    }

    @Bean
    public Queue emailAuthDlq() {
        return new Queue(emailAuthDlq);
    }

    // Bindings

    @Bean
    public Binding documentProcessBinding() {
        return BindingBuilder
                .bind(documentProcessQueue())
                .to(documentExchange())
                .with(documentProcessRoutingKey);
    }

    @Bean
    public Binding emailDocumentBinding() {
        return BindingBuilder
                .bind(emailDocumentQueue())
                .to(notificationExchange())
                .with(documentProcessRoutingKey);
    }

    @Bean
    public Binding emailAuthBinding() {
        return BindingBuilder
                .bind(emailAuthQueue())
                .to(notificationExchange())
                .with(emailAuthRoutingKey);
    }


    @Bean
    public Binding documentProcessDlqBinding() {
        return BindingBuilder
                .bind(documentProcessDlq())
                .to(deadLetterExchange())
                .with(deadLetterRoutingKey + ".document");
    }

    @Bean
    public Binding emailDocumentDlqBinding() {
        return BindingBuilder
                .bind(emailDocumentDlq())
                .to(deadLetterExchange())
                .with(deadLetterRoutingKey + ".email.document");
    }

    @Bean
    public Binding emailAuthDlqBinding() {
        return BindingBuilder
                .bind(emailAuthDlq())
                .to(deadLetterExchange())
                .with(deadLetterRoutingKey + ".email-auth");
    }

    // Retry configuration

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

    /**
     * Configure a special listener factory for the dead letter queues
     * that doesn't attempt retries or republishing
     */
    /**
     * Special container factory for dead letter queues that uses a simple string converter
     * to avoid deserialization issues with failed messages
     */
    @Bean
    public SimpleRabbitListenerContainerFactory dlqListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);

        // Use a simpler message converter that doesn't try to deserialize to specific types
        // This way we can handle messages with types that might not exist in our classpath
        factory.setMessageConverter(new SimpleMessageConverter());

        // Configure error handling
        factory.setErrorHandler(new ConditionalRejectingErrorHandler(new ConditionalRejectingErrorHandler.DefaultExceptionStrategy() {
            @Override
            public boolean isFatal(Throwable t) {
                if (t instanceof MessageConversionException) {
                    log.warn("Message conversion error in DLQ handler, treating as non-fatal: {}", t.getMessage());
                    return false;
                }
                return super.isFatal(t);
            }
        }));

        // No retry interceptor for DLQ - we don't want to retry failed messages again
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        return factory;
    }

    @Bean
    public MessageRecoverer messageRecoverer() {
        return new HandleRepublishMessageRecoverer(rabbitTemplate(), deadLetterExchange);
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
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);

        // Configure type mapping to handle classes from different packages
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");  // Trust all packages
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
}
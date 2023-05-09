package com.example.demo.config;

import com.example.demo.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Configuration
public class MessageListenerConfig {

    private static final Logger log = LoggerFactory.getLogger("appLogger");

    @Bean
    public Consumer<Flux<Message>> messageListener() {
        return inbound -> inbound
                .doOnNext(message -> log.info("Processing message: {}", message))
                .handle((message, sink) -> {
                    // some processing happens here
                    if (message.hasError()) sink.error(new RuntimeException("Message has error"));
                    else sink.next(message);
                })
                .doOnError(error -> log.error("Error occurred during message processing", error))
                .retry() // in case of error we need to re-subscribe to continue processing the incoming messages
                .subscribe();
    }
}

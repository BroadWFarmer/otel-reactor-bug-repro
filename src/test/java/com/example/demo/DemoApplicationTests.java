package com.example.demo;

import com.example.demo.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(TestChannelBinderConfiguration.class)
@SpringBootTest
class DemoApplicationTests {

    private static final String DESTINATION = "demo-exchange";

    @Autowired
    private InputDestination source;

    @Test
    void traceIdAndSpanIdRepeatAfterSubscriptionRetry() {
        /*
         send several messages without error to demonstrate that
         all of them are logged with unique trace_id and span_id
        */
        source.send(new GenericMessage<>(new Message(1L)), DESTINATION);
        source.send(new GenericMessage<>(new Message(2L)), DESTINATION);
        source.send(new GenericMessage<>(new Message(3L)), DESTINATION);

        /*
         send message with error to trigger subscription retry
        */
        source.send(new GenericMessage<>(new Message(4L).withError()), DESTINATION);

        /*
         send another group of messages to demonstrate that
         all of them are logged with the same trace_id and span_id
         as the erroneous message
        */
        source.send(new GenericMessage<>(new Message(5L)), DESTINATION);
        source.send(new GenericMessage<>(new Message(6L)), DESTINATION);
        source.send(new GenericMessage<>(new Message(7L)), DESTINATION);
    }
}

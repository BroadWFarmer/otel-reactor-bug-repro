**Bug description**

In our spring-boot project we use spring-cloud-reactive-streams together with rabbit binder to consume messages from
rabbitmq. We also use opentelemetry-javaagent-1.24.0 for tracing, and we have a problem when we get repeated `span_id`
and `trace_id` for our log messages after an error occurs somewhere in our reactive chain, which processes messages from
the message broker. The problem also persists with the opentelemetry-javaagent-1.25.1.

Here is a simplified version of our message processing chain:

```java
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
```

Reactor has a mechanism of indefinitely re-subscribing to a Flux sequence if it signals any error by calling the `retry()`
method on it. The thing is, when an error occurs somewhere in a Flux sequence and re-subscription happens, all 
application log messages starting from the moment of error receive the same `span_id` and `trace_id`.

Here are some logs to demonstrate the behavior:

```text
2023-05-09T13:27:14,825 INFO  [main] appLogger - {span_id=b0e90d73a1bf1267, trace_flags=01, trace_id=23df9807ab08a96400a2fb3b2149b2f9} - Processing message: Message{id=1, error=false}
[otel.javaagent 2023-05-09 13:27:14:827 +0200] [main] INFO io.opentelemetry.exporter.logging.LoggingSpanExporter - 'application.messageListener-in-0 process' : 23df9807ab08a96400a2fb3b2149b2f9 b0e90d73a1bf1267 CONSUMER [tracer: io.opentelemetry.spring-integration-4.1:1.25.1-alpha] AttributesMap{data={thread.id=1, messaging.operation=process, thread.name=main}, capacity=128, totalAddedValues=3}
2023-05-09T13:27:14,829 INFO  [main] appLogger - {span_id=b0e69e0d609049b2, trace_flags=01, trace_id=e0f7deeab9baf9c47fed432da5023f6f} - Processing message: Message{id=2, error=false}
[otel.javaagent 2023-05-09 13:27:14:829 +0200] [main] INFO io.opentelemetry.exporter.logging.LoggingSpanExporter - 'application.messageListener-in-0 process' : e0f7deeab9baf9c47fed432da5023f6f b0e69e0d609049b2 CONSUMER [tracer: io.opentelemetry.spring-integration-4.1:1.25.1-alpha] AttributesMap{data={thread.id=1, messaging.operation=process, thread.name=main}, capacity=128, totalAddedValues=3}
2023-05-09T13:27:14,829 INFO  [main] appLogger - {span_id=b2ebac7883b33a4f, trace_flags=01, trace_id=1aeed04583ac345132f260a2d125a219} - Processing message: Message{id=3, error=false}
[otel.javaagent 2023-05-09 13:27:14:829 +0200] [main] INFO io.opentelemetry.exporter.logging.LoggingSpanExporter - 'application.messageListener-in-0 process' : 1aeed04583ac345132f260a2d125a219 b2ebac7883b33a4f CONSUMER [tracer: io.opentelemetry.spring-integration-4.1:1.25.1-alpha] AttributesMap{data={thread.id=1, messaging.operation=process, thread.name=main}, capacity=128, totalAddedValues=3}
2023-05-09T13:27:14,830 INFO  [main] appLogger - {span_id=70e168f36f9a7ef6, trace_flags=01, trace_id=9d8565a4319f9de2ddb047f107a0a514} - Processing message: Message{id=4, error=true}
2023-05-09T13:27:14,834 INFO  [main] org.springframework.cloud.stream.messaging.DirectWithAttributesChannel - {span_id=70e168f36f9a7ef6, trace_flags=01, trace_id=9d8565a4319f9de2ddb047f107a0a514} - Channel 'application.messageListener-in-0' has 0 subscriber(s).
2023-05-09T13:27:14,834 ERROR [main] appLogger - {span_id=70e168f36f9a7ef6, trace_flags=01, trace_id=9d8565a4319f9de2ddb047f107a0a514} - Error occurred during message processing
java.lang.RuntimeException: Message has error (stack trace omitted)
2023-05-09T13:27:14,837 INFO  [main] org.springframework.cloud.stream.messaging.DirectWithAttributesChannel - {span_id=70e168f36f9a7ef6, trace_flags=01, trace_id=9d8565a4319f9de2ddb047f107a0a514} - Channel 'application.messageListener-in-0' has 1 subscriber(s).
[otel.javaagent 2023-05-09 13:27:14:838 +0200] [main] INFO io.opentelemetry.exporter.logging.LoggingSpanExporter - 'application.messageListener-in-0 process' : 9d8565a4319f9de2ddb047f107a0a514 70e168f36f9a7ef6 CONSUMER [tracer: io.opentelemetry.spring-integration-4.1:1.25.1-alpha] AttributesMap{data={thread.id=1, messaging.operation=process, thread.name=main}, capacity=128, totalAddedValues=3}
2023-05-09T13:27:14,838 INFO  [main] appLogger - {span_id=70e168f36f9a7ef6, trace_flags=01, trace_id=9d8565a4319f9de2ddb047f107a0a514} - Processing message: Message{id=5, error=false}
[otel.javaagent 2023-05-09 13:27:14:839 +0200] [main] INFO io.opentelemetry.exporter.logging.LoggingSpanExporter - 'application.messageListener-in-0 process' : 3d8e0800e9986253dba25d34d316d9f7 f216bfca4cd80929 CONSUMER [tracer: io.opentelemetry.spring-integration-4.1:1.25.1-alpha] AttributesMap{data={thread.id=1, messaging.operation=process, thread.name=main}, capacity=128, totalAddedValues=3}
2023-05-09T13:27:14,839 INFO  [main] appLogger - {span_id=70e168f36f9a7ef6, trace_flags=01, trace_id=9d8565a4319f9de2ddb047f107a0a514} - Processing message: Message{id=6, error=false}
[otel.javaagent 2023-05-09 13:27:14:839 +0200] [main] INFO io.opentelemetry.exporter.logging.LoggingSpanExporter - 'application.messageListener-in-0 process' : a39a3b1693e481a9742296c8a6395743 5dcf5c11159c5f72 CONSUMER [tracer: io.opentelemetry.spring-integration-4.1:1.25.1-alpha] AttributesMap{data={thread.id=1, messaging.operation=process, thread.name=main}, capacity=128, totalAddedValues=3}
2023-05-09T13:27:14,840 INFO  [main] appLogger - {span_id=70e168f36f9a7ef6, trace_flags=01, trace_id=9d8565a4319f9de2ddb047f107a0a514} - Processing message: Message{id=7, error=false}
[otel.javaagent 2023-05-09 13:27:14:840 +0200] [main] INFO io.opentelemetry.exporter.logging.LoggingSpanExporter - 'application.messageListener-in-0 process' : 120862798318f8eb66e67522b03addde 7712c21d8ae1aca4 CONSUMER [tracer: io.opentelemetry.spring-integration-4.1:1.25.1-alpha] AttributesMap{data={thread.id=1, messaging.operation=process, thread.name=main}, capacity=128, totalAddedValues=3}
```

It is clear from the logs that all `appLogger` log messages starting from "Processing message: Message{id=4, error=true}" 
have identical `span_id` and `trace_id`, although they have to be unique as they were for the messages 1 through 3.

After some experimenting I found out that the root cause might be somewhere in the reactor-instrumentation module, but
I'm not 100% sure.

**Steps to reproduce**

Go to the project root and depending on your operating system run either the first (for Windows) or the second (for
Linux) command:

1. `./mvnw.cmd clean test`
2. `./mvnw clean test`

Then in the logs you will be able to see the issue with the repeated `span_id` and `trace_id`. The test runs with the 
opentelemetry-javaagent attached (see the maven-surefire-plugin configuration in [pom.xml](pom.xml)). The javaagent 
itself and its configuration file can be found in the [otel-javaagent directory](otel-javaagent).

Of course, you can also run the test in your favorite IDE.

**Expected behavior**

Each message is processed under unique `span_id` and `trace_id`

**Actual behavior**

All messages received after the erroneous one share its `span_id` and `trace_id`

**Versions**

* Java 11
* spring-boot-2.7.6
* spring-cloud-2021.0.5
* project-reactor-3.4.25
* opentelemetry-javaagent-1.24.0 (same behavior with 1.25.1)

**Environment**

* Compiler: "Azul JDK 11.64.19-ca-jdk11.0.19"
* OS: "Debian 11 based container image"

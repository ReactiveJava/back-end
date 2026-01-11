package com.example.reactive.notification.service;

import com.example.reactive.notification.model.NotificationEvent;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Sinks.Many<NotificationEvent> sink;

    public NotificationService() {
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
    }

    public Mono<Void> publish(NotificationEvent event) {
        return Mono.fromRunnable(() -> {
            sink.tryEmitNext(event);
            if (event != null) {
                log.info("Notification published: type={}, userId={}", event.type(), event.userId());
            }
        });
    }

    public Flux<ServerSentEvent<NotificationEvent>> stream(String userId) {
        return sink.asFlux()
                .filter(event -> Objects.equals(event.userId(), userId) || "all".equalsIgnoreCase(event.userId()))
                .map(event -> ServerSentEvent.builder(event)
                        .event(event.type())
                        .id(event.timestamp().toString())
                        .build());
    }
}

package com.example.reactive.notification.service;

import com.example.reactive.notification.model.NotificationEvent;
import java.util.Objects;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class NotificationService {
    private final Sinks.Many<NotificationEvent> sink;

    public NotificationService() {
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
    }

    public Mono<Void> publish(NotificationEvent event) {
        sink.tryEmitNext(event);
        return Mono.empty();
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

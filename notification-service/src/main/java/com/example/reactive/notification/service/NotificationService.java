package com.example.reactive.notification.service;

import com.example.reactive.notification.model.NotificationEvent;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
@Slf4j
public class NotificationService {
    private final Sinks.Many<NotificationEvent> sink = Sinks.many().replay().limit(1);

    public Mono<Void> publish(NotificationEvent event) {
        return Mono.fromRunnable(() -> {
            sink.tryEmitNext(event);
            if (event != null) {
                log.info("Notification published: type={}, userId={}", event.type(), event.userId());
            }
        });
    }

    public Flux<ServerSentEvent<NotificationEvent>> stream(String userId) {
        ServerSentEvent<NotificationEvent> connected = ServerSentEvent.<NotificationEvent>builder()
                .comment("connected")
                .build();
        return Flux.concat(Mono.just(connected), sink.asFlux()
                .filter(event -> Objects.equals(event.userId(), userId) || "all".equalsIgnoreCase(event.userId()))
                .map(event -> ServerSentEvent.builder(event)
                        .event(event.type())
                        .id(event.timestamp().toString())
                        .build()));
    }
}

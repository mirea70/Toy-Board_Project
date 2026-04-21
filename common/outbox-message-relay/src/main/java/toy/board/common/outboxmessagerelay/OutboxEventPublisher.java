package toy.board.common.outboxmessagerelay;

import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.common.event.EventType;
import toy.board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {
    private final Snowflake outboxIdSnowFlake = new Snowflake();
    private final Snowflake eventIdSnowFlake = new Snowflake();
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(EventType type, EventPayload payload, Long shardKey) {
        Outbox outbox = Outbox.create(
                outboxIdSnowFlake.nextId(),
                type,
                Event.of(
                        eventIdSnowFlake.nextId(),
                        type,
                        payload
                ).toJson(),
                shardKey % MessageRelayConstants.SHARD_COUNT
        );
        applicationEventPublisher.publishEvent(OutboxEvent.of(outbox));
    }
}

package toy.board.common.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import toy.board.common.snowflake.Snowflake;

@Component
@RequiredArgsConstructor
public class EventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Snowflake snowflake;

    public void publish(EventType type, EventPayload payload) {
        Event<EventPayload> event = Event.of(snowflake.nextId(), type, payload);
        applicationEventPublisher.publishEvent(event);
    }
}

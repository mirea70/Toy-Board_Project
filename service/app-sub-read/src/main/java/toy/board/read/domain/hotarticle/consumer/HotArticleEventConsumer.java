package toy.board.read.domain.hotarticle.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.common.event.EventType;
import toy.board.read.domain.hotarticle.service.HotArticleService;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleEventConsumer {
    private final HotArticleService hotArticleService;

    @KafkaListener(topics = {
            EventType.Topic.TOY_BOARD_ARTICLE,
            EventType.Topic.TOY_BOARD_COMMENT,
            EventType.Topic.TOY_BOARD_LIKE,
            EventType.Topic.TOY_BOARD_VIEW
    }, groupId = "toy-board-hot-article")
    public void listen(String message, Acknowledgment ack) {
        log.info("[HotArticleEventConsumer.listen] message={}", message);
        Event<EventPayload> event = Event.fromJson(message);
        if (event != null) {
            hotArticleService.handleEvent(event);
        }
        ack.acknowledge();
    }
}

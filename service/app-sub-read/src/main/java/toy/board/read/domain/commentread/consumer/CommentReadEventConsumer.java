package toy.board.read.domain.commentread.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.common.event.EventType;
import toy.board.read.domain.commentread.service.CommentReadService;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentReadEventConsumer {
    private final CommentReadService commentReadService;

    @KafkaListener(topics = EventType.Topic.TOY_BOARD_COMMENT,
                   groupId = "toy-board-comment-read")
    public void listen(String message, Acknowledgment ack) {
        log.info("[CommentReadEventConsumer.listen] message={}", message);
        Event<EventPayload> event = Event.fromJson(message);
        if (event != null) {
            commentReadService.handleEvent(event);
        }
        ack.acknowledge();
    }
}

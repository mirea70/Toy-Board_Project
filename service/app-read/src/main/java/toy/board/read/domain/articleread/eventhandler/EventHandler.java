package toy.board.read.domain.articleread.eventhandler;

import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;

public interface EventHandler<T extends EventPayload> {
    void handle(Event<T> event);
    boolean supports(Event<T> event);
}

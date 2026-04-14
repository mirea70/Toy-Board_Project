package toy.board.domain.hotarticle.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.domain.hotarticle.service.HotArticleService;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleEventListener {
    private final HotArticleService hotArticleService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(Event<EventPayload> event) {
        try {
            hotArticleService.handleEvent(event);
        } catch (Exception e) {
            log.error("[HotArticleEventListener.on] error", e);
        }
    }
}

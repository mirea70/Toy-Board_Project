package toy.board.domain.articleread.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.domain.articleread.service.ArticleReadService;

/**
 * Kafka consumer 대체. Article/Comment/Like/View 서비스가 커밋하는 순간
 * ApplicationEventPublisher로 전파된 Event를 수신해 article-read 쿼리 모델을 갱신한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleReadEventListener {
    private final ArticleReadService articleReadService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(Event<EventPayload> event) {
        try {
            articleReadService.handleEvent(event);
        } catch (Exception e) {
            log.error("[ArticleReadEventListener.on] error", e);
        }
    }
}

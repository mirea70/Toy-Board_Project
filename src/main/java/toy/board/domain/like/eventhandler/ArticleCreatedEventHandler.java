package toy.board.domain.like.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleCreatedEventPayload;
import toy.board.domain.like.entity.ArticleLikeCount;
import toy.board.domain.like.repository.ArticleLikeCountRepository;

@Component("likeArticleCreatedEventHandler")
@RequiredArgsConstructor
public class ArticleCreatedEventHandler {
    private final ArticleLikeCountRepository articleLikeCountRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(Event<EventPayload> event) {
        if (event.getType() != EventType.ARTICLE_CREATED) {
            return;
        }
        ArticleCreatedEventPayload payload = (ArticleCreatedEventPayload) event.getPayload();
        articleLikeCountRepository.save(ArticleLikeCount.init(payload.getArticleId(), 0L));
    }
}

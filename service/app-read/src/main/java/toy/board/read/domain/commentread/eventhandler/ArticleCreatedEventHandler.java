package toy.board.read.domain.commentread.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleCreatedEventPayload;
import toy.board.read.domain.commentread.repository.CommentCountQueryRepository;

import java.time.Duration;

@Component("commentReadArticleCreatedEventHandler")
@RequiredArgsConstructor
public class ArticleCreatedEventHandler {
    private final CommentCountQueryRepository commentCountQueryRepository;

    private static final Duration TTL = Duration.ofDays(1);

    public void handle(Event<ArticleCreatedEventPayload> event) {
        commentCountQueryRepository.createOrUpdate(
                event.getPayload().getArticleId(), 0L, TTL);
    }

    public boolean supports(Event<?> event) {
        return event.getType() == EventType.ARTICLE_CREATED;
    }
}

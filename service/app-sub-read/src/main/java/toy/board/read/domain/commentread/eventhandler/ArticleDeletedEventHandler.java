package toy.board.read.domain.commentread.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleDeletedEventPayload;
import toy.board.read.repository.CommentCountQueryRepository;

@Component("commentReadArticleDeletedEventHandler")
@RequiredArgsConstructor
public class ArticleDeletedEventHandler {
    private final CommentCountQueryRepository commentCountQueryRepository;

    public void handle(Event<ArticleDeletedEventPayload> event) {
        commentCountQueryRepository.delete(event.getPayload().getArticleId());
    }

    public boolean supports(Event<?> event) {
        return event.getType() == EventType.ARTICLE_DELETED;
    }
}

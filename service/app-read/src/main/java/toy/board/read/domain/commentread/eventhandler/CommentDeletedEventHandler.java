package toy.board.read.domain.commentread.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.CommentDeletedEventPayload;
import toy.board.read.domain.commentread.repository.CommentCountQueryRepository;
import toy.board.read.domain.commentread.repository.CommentIdListRepository;
import toy.board.read.domain.commentread.repository.CommentQueryModelRepository;

import java.time.Duration;

@Component("commentReadCommentDeletedEventHandler")
@RequiredArgsConstructor
public class CommentDeletedEventHandler {
    private static final Duration COUNT_TTL = Duration.ofDays(1);

    private final CommentQueryModelRepository commentQueryModelRepository;
    private final CommentIdListRepository commentIdListRepository;
    private final CommentCountQueryRepository commentCountQueryRepository;

    public boolean supports(Event<?> event) {
        return event.getType() == EventType.COMMENT_DELETED;
    }

    public void handle(Event<CommentDeletedEventPayload> event) {
        CommentDeletedEventPayload payload = event.getPayload();
        commentQueryModelRepository.delete(payload.getCommentId());
        commentIdListRepository.delete(payload.getArticleId(), payload.getCommentId());
        commentCountQueryRepository.createOrUpdate(
                payload.getArticleId(),
                payload.getArticleCommentCount(),
                COUNT_TTL);
    }
}

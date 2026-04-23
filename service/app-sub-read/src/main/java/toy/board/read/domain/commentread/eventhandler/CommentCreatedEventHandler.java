package toy.board.read.domain.commentread.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.CommentCreatedEventPayload;
import toy.board.read.repository.CommentCountQueryRepository;
import toy.board.read.domain.commentread.repository.CommentIdListRepository;
import toy.board.read.domain.commentread.repository.CommentQueryModel;
import toy.board.read.domain.commentread.repository.CommentQueryModelRepository;

import java.time.Duration;

@Component("commentReadCommentCreatedEventHandler")
@RequiredArgsConstructor
public class CommentCreatedEventHandler {
    private static final Duration COUNT_TTL = Duration.ofDays(1);

    private final CommentQueryModelRepository commentQueryModelRepository;
    private final CommentIdListRepository commentIdListRepository;
    private final CommentCountQueryRepository commentCountQueryRepository;

    public boolean supports(Event<?> event) {
        return event.getType() == EventType.COMMENT_CREATED;
    }

    public void handle(Event<CommentCreatedEventPayload> event) {
        CommentCreatedEventPayload payload = event.getPayload();
        commentQueryModelRepository.create(CommentQueryModel.from(payload), Duration.ofDays(1));
        commentIdListRepository.add(
                payload.getArticleId(),
                payload.getCommentId(),
                payload.getCommentId().doubleValue(),
                1000L
        );
        commentCountQueryRepository.createOrUpdate(
                payload.getArticleId(),
                payload.getArticleCommentCount(),
                COUNT_TTL);
    }
}

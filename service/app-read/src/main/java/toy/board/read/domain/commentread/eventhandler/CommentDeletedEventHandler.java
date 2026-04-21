package toy.board.read.domain.commentread.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.CommentDeletedEventPayload;
import toy.board.read.domain.commentread.repository.CommentIdListRepository;
import toy.board.read.domain.commentread.repository.CommentQueryModelRepository;

@Component("commentReadCommentDeletedEventHandler")
@RequiredArgsConstructor
public class CommentDeletedEventHandler {
    private final CommentQueryModelRepository commentQueryModelRepository;
    private final CommentIdListRepository commentIdListRepository;

    public boolean supports(Event<?> event) {
        return event.getType() == EventType.COMMENT_DELETED;
    }

    public void handle(Event<CommentDeletedEventPayload> event) {
        CommentDeletedEventPayload payload = event.getPayload();
        commentQueryModelRepository.delete(payload.getCommentId());
        commentIdListRepository.delete(payload.getArticleId(), payload.getCommentId());
    }
}

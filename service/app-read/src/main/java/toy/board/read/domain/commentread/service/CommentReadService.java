package toy.board.read.domain.commentread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.read.client.CommentClient;
import toy.board.read.domain.commentread.eventhandler.ArticleCreatedEventHandler;
import toy.board.read.domain.commentread.eventhandler.ArticleDeletedEventHandler;
import toy.board.read.domain.commentread.eventhandler.CommentCreatedEventHandler;
import toy.board.read.domain.commentread.eventhandler.CommentDeletedEventHandler;
import toy.board.read.domain.commentread.repository.CommentCountQueryRepository;
import toy.board.read.domain.commentread.repository.CommentIdListRepository;
import toy.board.read.domain.commentread.repository.CommentQueryModel;
import toy.board.read.domain.commentread.repository.CommentQueryModelRepository;
import toy.board.read.domain.commentread.response.CommentReadPageResponse;
import toy.board.read.domain.commentread.response.CommentReadResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommentReadService {
    private static final Duration COUNT_TTL = Duration.ofDays(1);

    private final CommentQueryModelRepository commentQueryModelRepository;
    private final CommentIdListRepository commentIdListRepository;
    private final CommentCountQueryRepository commentCountQueryRepository;
    private final CommentClient commentClient;
    private final CommentCreatedEventHandler createdHandler;
    private final CommentDeletedEventHandler deletedHandler;
    private final ArticleCreatedEventHandler articleCreatedHandler;
    private final ArticleDeletedEventHandler articleDeletedHandler;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void handleEvent(Event<EventPayload> event) {
        if (createdHandler.supports(event)) {
            createdHandler.handle((Event) event);
        } else if (deletedHandler.supports(event)) {
            deletedHandler.handle((Event) event);
        } else if (articleCreatedHandler.supports(event)) {
            articleCreatedHandler.handle((Event) event);
        } else if (articleDeletedHandler.supports(event)) {
            articleDeletedHandler.handle((Event) event);
        }
    }

    public CommentReadPageResponse readAll(Long articleId, Long page, Long pageSize) {
        List<Long> commentIds = commentIdListRepository.readAll(articleId, (page - 1) * pageSize, pageSize);
        Map<Long, CommentQueryModel> models = commentQueryModelRepository.readAll(commentIds);
        List<CommentReadResponse> responses = commentIds.stream()
                .map(models::get)
                .filter(Objects::nonNull)
                .map(CommentReadResponse::from)
                .toList();
        Long count = readCountWithFallback(articleId);
        return CommentReadPageResponse.of(responses, count);
    }

    public CommentReadResponse read(Long commentId) {
        return commentQueryModelRepository.read(commentId)
                .map(CommentReadResponse::from)
                .orElseThrow();
    }

    public List<CommentReadResponse> readAllInfiniteScroll(Long articleId, String lastPath, Long pageSize) {
        // For infinite scroll: for stage9 simplicity, paginate via offset from start of ZSET
        List<Long> commentIds = commentIdListRepository.readAll(articleId, 0L, pageSize);
        Map<Long, CommentQueryModel> models = commentQueryModelRepository.readAll(commentIds);
        return commentIds.stream()
                .map(models::get)
                .filter(Objects::nonNull)
                .map(CommentReadResponse::from)
                .toList();
    }

    public Long count(Long articleId) {
        return readCountWithFallback(articleId);
    }

    private Long readCountWithFallback(Long articleId) {
        return commentCountQueryRepository.read(articleId)
                .orElseGet(() -> {
                    long fetched = commentClient.count(articleId);
                    commentCountQueryRepository.createOrUpdate(articleId, fetched, COUNT_TTL);
                    return fetched;
                });
    }
}

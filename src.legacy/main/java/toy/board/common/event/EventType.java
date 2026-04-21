package toy.board.common.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import toy.board.common.event.payload.ArticleCreatedEventPayload;
import toy.board.common.event.payload.ArticleDeletedEventPayload;
import toy.board.common.event.payload.ArticleLikedEventPayload;
import toy.board.common.event.payload.ArticleUnlikedEventPayload;
import toy.board.common.event.payload.ArticleUpdatedEventPayload;
import toy.board.common.event.payload.ArticleViewedEventPayload;
import toy.board.common.event.payload.CommentCreatedEventPayload;
import toy.board.common.event.payload.CommentDeletedEventPayload;

@Slf4j
@Getter
@RequiredArgsConstructor
public enum EventType {
    ARTICLE_CREATED(ArticleCreatedEventPayload.class),
    ARTICLE_UPDATED(ArticleUpdatedEventPayload.class),
    ARTICLE_DELETED(ArticleDeletedEventPayload.class),
    COMMENT_CREATED(CommentCreatedEventPayload.class),
    COMMENT_DELETED(CommentDeletedEventPayload.class),
    ARTICLE_LIKED(ArticleLikedEventPayload.class),
    ARTICLE_UNLIKED(ArticleUnlikedEventPayload.class),
    ARTICLE_VIEWED(ArticleViewedEventPayload.class);

    private final Class<? extends EventPayload> payloadClass;

    public static EventType from(String type) {
        try {
            return valueOf(type);
        } catch (Exception e) {
            log.error("[EventType.from] type={}", type, e);
            return null;
        }
    }
}

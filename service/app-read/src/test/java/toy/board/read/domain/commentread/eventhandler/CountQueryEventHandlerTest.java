package toy.board.read.domain.commentread.eventhandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleCreatedEventPayload;
import toy.board.common.event.payload.ArticleDeletedEventPayload;
import toy.board.common.event.payload.CommentCreatedEventPayload;
import toy.board.common.event.payload.CommentDeletedEventPayload;
import toy.board.read.domain.commentread.repository.CommentCountQueryRepository;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CountQueryEventHandlerTest {

    @Autowired
    CommentCountQueryRepository repository;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ArticleCreatedEventHandler articleCreatedHandler;

    @Autowired
    ArticleDeletedEventHandler articleDeletedHandler;

    @Autowired
    toy.board.read.domain.commentread.eventhandler.CommentCreatedEventHandler commentCreatedHandler;

    @Autowired
    toy.board.read.domain.commentread.eventhandler.CommentDeletedEventHandler commentDeletedHandler;

    @BeforeEach
    void flushRedis() {
        try (RedisConnection conn = redisTemplate.getConnectionFactory().getConnection()) {
            conn.serverCommands().flushDb();
        }
    }

    @Test
    void articleCreated_initsCountToZeroWithDailyTtl() {
        long articleId = 100L;
        Event<ArticleCreatedEventPayload> event = buildArticleCreatedEvent(articleId);

        articleCreatedHandler.handle(event);

        assertThat(repository.read(articleId)).contains(0L);
        Long ttl = redisTemplate.getExpire("comment-read::article::" + articleId + "::comment-count");
        assertThat(ttl).isGreaterThan(Duration.ofHours(23).toSeconds());
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofDays(1).toSeconds());
    }

    @Test
    void articleDeleted_removesCountKey() {
        long articleId = 200L;
        repository.createOrUpdate(articleId, 5L, Duration.ofDays(1));

        ArticleDeletedEventPayload payload = ArticleDeletedEventPayload.builder()
                .articleId(articleId)
                .boardId(1L)
                .build();
        @SuppressWarnings({"rawtypes", "unchecked"})
        Event<ArticleDeletedEventPayload> event =
                (Event) Event.of(1L, EventType.ARTICLE_DELETED, payload);

        articleDeletedHandler.handle(event);

        assertThat(repository.read(articleId)).isEmpty();
    }

    @Test
    void commentCreated_updatesCountFromPayload() {
        long articleId = 300L;
        CommentCreatedEventPayload payload = CommentCreatedEventPayload.builder()
                .articleId(articleId)
                .commentId(1L).content("x").path("00001").writerId(1L).deleted(false)
                .createdAt(LocalDateTime.now())
                .articleCommentCount(42L)
                .build();

        @SuppressWarnings({"rawtypes", "unchecked"})
        Event<CommentCreatedEventPayload> event =
                (Event) Event.of(1L, EventType.COMMENT_CREATED, payload);

        commentCreatedHandler.handle(event);

        assertThat(repository.read(articleId)).contains(42L);
        Long ttl = redisTemplate.getExpire("comment-read::article::" + articleId + "::comment-count");
        assertThat(ttl).isGreaterThan(Duration.ofHours(23).toSeconds());
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofDays(1).toSeconds());
    }

    @Test
    void commentCreated_resetsTtlOnEachInvocation() {
        long articleId = 301L;
        // Pre-seed with short TTL
        repository.createOrUpdate(articleId, 0L, Duration.ofSeconds(60));

        CommentCreatedEventPayload payload = CommentCreatedEventPayload.builder()
                .articleId(articleId)
                .commentId(1L).content("x").path("00001").writerId(1L).deleted(false)
                .createdAt(LocalDateTime.now())
                .articleCommentCount(1L)
                .build();

        @SuppressWarnings({"rawtypes", "unchecked"})
        Event<CommentCreatedEventPayload> event =
                (Event) Event.of(1L, EventType.COMMENT_CREATED, payload);

        commentCreatedHandler.handle(event);

        Long ttl = redisTemplate.getExpire("comment-read::article::" + articleId + "::comment-count");
        assertThat(ttl).isGreaterThan(Duration.ofHours(23).toSeconds());  // 60s reset to ~1 day
        assertThat(repository.read(articleId)).contains(1L);
    }

    @Test
    void commentDeleted_updatesCountFromPayload() {
        long articleId = 400L;
        repository.createOrUpdate(articleId, 10L, Duration.ofDays(1));

        CommentDeletedEventPayload payload = CommentDeletedEventPayload.builder()
                .articleId(articleId)
                .commentId(1L).content("x").path("00001").writerId(1L).deleted(true)
                .createdAt(LocalDateTime.now())
                .articleCommentCount(9L)
                .build();

        @SuppressWarnings({"rawtypes", "unchecked"})
        Event<CommentDeletedEventPayload> event =
                (Event) Event.of(1L, EventType.COMMENT_DELETED, payload);

        commentDeletedHandler.handle(event);

        assertThat(repository.read(articleId)).contains(9L);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Event<ArticleCreatedEventPayload> buildArticleCreatedEvent(long articleId) {
        ArticleCreatedEventPayload payload = ArticleCreatedEventPayload.builder()
                .articleId(articleId)
                .title("t")
                .content("c")
                .boardId(1L)
                .writerId(1L)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();
        Event<EventPayload> event = Event.of(1L, EventType.ARTICLE_CREATED, payload);
        return (Event) event;
    }
}

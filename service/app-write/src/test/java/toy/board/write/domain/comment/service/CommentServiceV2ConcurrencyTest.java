package toy.board.write.domain.comment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import toy.board.common.outboxmessagerelay.MessageRelay;
import toy.board.common.outboxmessagerelay.MessageRelayCoordinator;
import toy.board.common.outboxmessagerelay.OutboxEventPublisher;
import toy.board.write.domain.comment.dto.CommentCreateRequestV2;
import toy.board.write.domain.comment.repository.ArticleCommentCountRepository;
import toy.board.write.domain.comment.repository.CommentRepositoryV2;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@MockBean({
        OutboxEventPublisher.class,
        MessageRelay.class,
        MessageRelayCoordinator.class
})
class CommentServiceV2ConcurrencyTest {
    private static final Long ARTICLE_ID = Long.MAX_VALUE;
    private static final int REQUEST_COUNT = 30;

    @Autowired
    CommentServiceV2 commentService;

    @Autowired
    CommentRepositoryV2 commentRepository;

    @Autowired
    ArticleCommentCountRepository articleCommentCountRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        commentRepository.deleteAllInBatch(
                commentRepository.findAllInfiniteScroll(ARTICLE_ID, Long.MAX_VALUE)
        );
        articleCommentCountRepository.deleteById(ARTICLE_ID);
    }

    @DisplayName("동시에 댓글 생성 요청이 여러개 오더라도 댓글 수 데이터가 유실되지 않으며, Duplicate Key 에러도 발생하지 않는다.")
    @Test
    void create_increasesArticleCommentCountWithoutLostUpdates() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                long writerId = i + 1L;
                futures.add(executorService.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        commentService.create(createRequest(writerId));
                        return null;
                    } catch (Throwable throwable) {
                        return throwable;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable failure = future.get(30, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }

            assertThat(failures).noneMatch(this::hasDuplicateKeyError);
            assertThat(failures).isEmpty();
        } finally {
            executorService.shutdownNow();
        }

        assertThat(commentService.count(ARTICLE_ID)).isEqualTo(REQUEST_COUNT);
        assertThat(commentRepository.count(ARTICLE_ID, REQUEST_COUNT + 1L)).isEqualTo(REQUEST_COUNT);
    }

    private CommentCreateRequestV2 createRequest(Long writerId) {
        CommentCreateRequestV2 request = new CommentCreateRequestV2();
        ReflectionTestUtils.setField(request, "articleId", ARTICLE_ID);
        ReflectionTestUtils.setField(request, "content", "content");
        ReflectionTestUtils.setField(request, "writerId", writerId);
        return request;
    }

    private boolean hasDuplicateKeyError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof DuplicateKeyException
                    || cause instanceof SQLException sqlException && sqlException.getErrorCode() == 1062) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

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
import toy.board.write.domain.comment.response.CommentResponse;

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
    private static final int REQUEST_COUNT = 4;

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

    @DisplayName("루트 댓글을 동시에 생성해도 댓글 수가 유실되지 않고 경로가 중복되지 않는다.")
    @Test
    void createRootComments_concurrentlyCreatesUniquePathsWithoutLostUpdates() throws Exception {
        // given
        String parentPath = null;

        // when
        ConcurrentCreateResult result = createConcurrently(parentPath);

        // then
        assertThat(result.failures()).noneMatch(this::hasDuplicateKeyError);
        assertThat(result.failures()).isEmpty();
        assertThat(result.responses()).extracting(CommentResponse::getPath).doesNotHaveDuplicates();
        assertThat(commentService.count(ARTICLE_ID)).isEqualTo(REQUEST_COUNT);
        assertThat(commentRepository.count(ARTICLE_ID, REQUEST_COUNT + 1L)).isEqualTo(REQUEST_COUNT);
    }

    @DisplayName("동일 부모의 자식 댓글을 동시에 생성해도 경로가 중복되지 않는다.")
    @Test
    void createChildComments_concurrentlyCreatesUniquePaths() throws Exception {
        // given
        CommentResponse parent = commentService.create(createRequest(1L, null));

        // when
        ConcurrentCreateResult result = createConcurrently(parent.getPath());

        // then
        assertThat(result.failures()).isEmpty();
        assertThat(result.responses()).extracting(CommentResponse::getPath).doesNotHaveDuplicates();
        assertThat(commentService.count(ARTICLE_ID)).isEqualTo(REQUEST_COUNT + 1L);
        assertThat(commentRepository.count(ARTICLE_ID, REQUEST_COUNT + 2L)).isEqualTo(REQUEST_COUNT + 1L);
    }

    private ConcurrentCreateResult createConcurrently(String parentPath) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CreateResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                long writerId = i + 1L;
                futures.add(executorService.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        return new CreateResult(
                                commentService.create(createRequest(writerId, parentPath)),
                                null
                        );
                    } catch (Throwable throwable) {
                        return new CreateResult(null, throwable);
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            List<CommentResponse> responses = new ArrayList<>();
            for (Future<CreateResult> future : futures) {
                CreateResult result = future.get(30, TimeUnit.SECONDS);
                if (result.failure() != null) {
                    failures.add(result.failure());
                }
                if (result.response() != null) {
                    responses.add(result.response());
                }
            }
            return new ConcurrentCreateResult(responses, failures);
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }
    }

    private CommentCreateRequestV2 createRequest(Long writerId, String parentPath) {
        CommentCreateRequestV2 request = new CommentCreateRequestV2();
        ReflectionTestUtils.setField(request, "articleId", ARTICLE_ID);
        ReflectionTestUtils.setField(request, "content", "content");
        ReflectionTestUtils.setField(request, "writerId", writerId);
        ReflectionTestUtils.setField(request, "parentPath", parentPath);
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

    private record CreateResult(CommentResponse response, Throwable failure) {
    }

    private record ConcurrentCreateResult(List<CommentResponse> responses, List<Throwable> failures) {
    }
}

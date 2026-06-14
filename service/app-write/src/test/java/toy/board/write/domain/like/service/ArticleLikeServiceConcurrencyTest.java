package toy.board.write.domain.like.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import toy.board.common.outboxmessagerelay.MessageRelay;
import toy.board.common.outboxmessagerelay.MessageRelayCoordinator;
import toy.board.common.outboxmessagerelay.OutboxEventPublisher;
import toy.board.write.domain.like.entity.ArticleLike;
import toy.board.write.domain.like.entity.ArticleLikeCount;
import toy.board.write.domain.like.repository.ArticleLikeCountRepository;
import toy.board.write.domain.like.repository.ArticleLikeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@MockBean({
        OutboxEventPublisher.class,
        MessageRelay.class,
        MessageRelayCoordinator.class
})
class ArticleLikeServiceConcurrencyTest {
    private static final Long ARTICLE_ID = Long.MAX_VALUE - 1;
    private static final int REQUEST_COUNT = 30;

    @Autowired
    ArticleLikeService articleLikeService;

    @Autowired
    ArticleLikeRepository articleLikeRepository;

    @Autowired
    ArticleLikeCountRepository articleLikeCountRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        articleLikeRepository.deleteAllInBatch(findArticleLikes());
        articleLikeCountRepository.deleteById(ARTICLE_ID);
    }

    @DisplayName("여러 사용자가 동시에 좋아요를 눌러도 좋아요 수 데이터가 유실되지 않는다.")
    @Test
    void likePessimisticLock1_increasesArticleLikeCountWithoutLostUpdates() throws Exception {
        // given
        articleLikeCountRepository.save(ArticleLikeCount.init(ARTICLE_ID, 0L));

        // when
        List<Throwable> failures = executeConcurrently(
                userId -> articleLikeService.likePessimisticLock1(ARTICLE_ID, userId)
        );

        // then
        assertThat(failures).isEmpty();
        assertThat(articleLikeService.count(ARTICLE_ID)).isEqualTo(REQUEST_COUNT);
        assertThat(findArticleLikes()).hasSize(REQUEST_COUNT);
    }

    @DisplayName("카운트 행이 존재하면 여러 사용자의 동시 좋아요 요청을 비관적 락으로 직렬화한다.")
    @Test
    void likePessimisticLock2_increasesArticleLikeCountWithoutLostUpdates() throws Exception {
        // given
        articleLikeCountRepository.save(ArticleLikeCount.init(ARTICLE_ID, 0L));

        // when
        List<Throwable> failures = executeConcurrently(
                userId -> articleLikeService.likePessimisticLock2(ARTICLE_ID, userId)
        );

        // then
        assertThat(failures).isEmpty();
        assertThat(articleLikeService.count(ARTICLE_ID)).isEqualTo(REQUEST_COUNT);
        assertThat(findArticleLikes()).hasSize(REQUEST_COUNT);
    }

    @DisplayName("카운트 행이 없으면 좋아요 요청을 실패시키고 좋아요 저장을 롤백한다.")
    @Test
    void likePessimisticLock2_rollsBackWhenArticleLikeCountDoesNotExist() {
        // given
        long userId = 1L;

        // when & then
        assertThatThrownBy(() -> articleLikeService.likePessimisticLock2(ARTICLE_ID, userId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(articleLikeRepository.findByArticleIdAndUserId(ARTICLE_ID, userId)).isEmpty();
    }

    @DisplayName("카운트 행이 존재하면 여러 사용자의 동시 좋아요 취소 요청을 비관적 락으로 직렬화한다.")
    @Test
    void unlikePessimisticLock2_decreasesArticleLikeCountWithoutLostUpdates() throws Exception {
        // given
        articleLikeCountRepository.save(ArticleLikeCount.init(ARTICLE_ID, (long) REQUEST_COUNT));
        LongStream.rangeClosed(1, REQUEST_COUNT)
                .mapToObj(userId -> ArticleLike.create(ARTICLE_ID - userId, ARTICLE_ID, userId))
                .forEach(articleLikeRepository::save);

        // when
        List<Throwable> failures = executeConcurrently(
                userId -> articleLikeService.unlikePessimisticLock2(ARTICLE_ID, userId)
        );

        // then
        assertThat(failures).isEmpty();
        assertThat(articleLikeService.count(ARTICLE_ID)).isZero();
        assertThat(findArticleLikes()).isEmpty();
    }

    @DisplayName("카운트 행이 없으면 좋아요 취소 요청을 실패시키고 좋아요 삭제를 롤백한다.")
    @Test
    void unlikePessimisticLock2_rollsBackWhenArticleLikeCountDoesNotExist() {
        // given
        long userId = 1L;
        articleLikeRepository.save(ArticleLike.create(ARTICLE_ID - userId, ARTICLE_ID, userId));

        // when & then
        assertThatThrownBy(() -> articleLikeService.unlikePessimisticLock2(ARTICLE_ID, userId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(articleLikeRepository.findByArticleIdAndUserId(ARTICLE_ID, userId)).isPresent();
    }

    private List<ArticleLike> findArticleLikes() {
        return LongStream.rangeClosed(1, REQUEST_COUNT)
                .mapToObj(userId -> articleLikeRepository.findByArticleIdAndUserId(ARTICLE_ID, userId))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private List<Throwable> executeConcurrently(LongConsumer operation) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        try {
            for (long userId = 1; userId <= REQUEST_COUNT; userId++) {
                long concurrentUserId = userId;
                futures.add(executorService.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        operation.accept(concurrentUserId);
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
            return failures;
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }
    }
}

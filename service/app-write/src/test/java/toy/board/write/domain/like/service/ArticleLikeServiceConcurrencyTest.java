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
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

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
        ExecutorService executorService = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        // when
        try {
            for (long userId = 1; userId <= REQUEST_COUNT; userId++) {
                long concurrentUserId = userId;
                futures.add(executorService.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        articleLikeService.likePessimisticLock1(ARTICLE_ID, concurrentUserId);
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

            // then
            assertThat(failures).isEmpty();
            assertThat(articleLikeService.count(ARTICLE_ID)).isEqualTo(REQUEST_COUNT);
            assertThat(findArticleLikes()).hasSize(REQUEST_COUNT);
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }
    }

    private List<ArticleLike> findArticleLikes() {
        return LongStream.rangeClosed(1, REQUEST_COUNT)
                .mapToObj(userId -> articleLikeRepository.findByArticleIdAndUserId(ARTICLE_ID, userId))
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}

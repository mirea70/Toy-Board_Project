package toy.board.write.domain.article.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.ArgumentCaptor;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleDeletedEventPayload;
import toy.board.common.outboxmessagerelay.MessageRelay;
import toy.board.common.outboxmessagerelay.MessageRelayCoordinator;
import toy.board.common.outboxmessagerelay.OutboxEventPublisher;
import toy.board.write.domain.article.entity.Article;
import toy.board.write.domain.article.repository.ArticleRepository;
import toy.board.write.domain.like.entity.ArticleLikeCount;
import toy.board.write.domain.like.repository.ArticleLikeCountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@MockBean({
        MessageRelay.class,
        MessageRelayCoordinator.class
})
class ArticleServiceTest {
    private static final Long ARTICLE_ID = Long.MAX_VALUE - 2;

    @Autowired
    ArticleService articleService;

    @Autowired
    ArticleRepository articleRepository;

    @Autowired
    ArticleLikeCountRepository articleLikeCountRepository;

    @MockBean
    OutboxEventPublisher outboxEventPublisher;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        articleLikeCountRepository.deleteById(ARTICLE_ID);
        articleRepository.deleteById(ARTICLE_ID);
    }

    @DisplayName("게시글을 삭제하면 게시글 좋아요 수 데이터도 함께 삭제하고, 이벤트를 발행한다.")
    @Test
    void delete_removesArticleAndArticleLikeCount() {
        // given
        createArticleAndArticleLikeCount();

        // when
        articleService.delete(ARTICLE_ID);

        // then
        assertThat(articleRepository.findById(ARTICLE_ID)).isEmpty();
        assertThat(articleLikeCountRepository.findById(ARTICLE_ID)).isEmpty();
        ArgumentCaptor<ArticleDeletedEventPayload> payloadCaptor =
                ArgumentCaptor.forClass(ArticleDeletedEventPayload.class);
        verify(outboxEventPublisher).publish(
                eq(EventType.ARTICLE_DELETED),
                payloadCaptor.capture(),
                eq(ARTICLE_ID)
        );
        assertThat(payloadCaptor.getValue())
                .extracting(
                        ArticleDeletedEventPayload::getArticleId,
                        ArticleDeletedEventPayload::getTitle,
                        ArticleDeletedEventPayload::getContent,
                        ArticleDeletedEventPayload::getBoardId,
                        ArticleDeletedEventPayload::getWriterId
                )
                .containsExactly(ARTICLE_ID, "title", "content", 1L, 1L);
    }

    @DisplayName("게시글 삭제 중 예외가 발생하면 게시글과 좋아요 수 데이터 삭제를 모두 롤백한다.")
    @Test
    void delete_rollsBackArticleAndArticleLikeCountWhenEventPublishFails() {
        // given
        createArticleAndArticleLikeCount();
        doThrow(new RuntimeException("publish failed"))
                .when(outboxEventPublisher)
                .publish(eq(EventType.ARTICLE_DELETED), any(), eq(ARTICLE_ID));

        // when & then
        assertThatThrownBy(() -> articleService.delete(ARTICLE_ID))
                .isInstanceOf(RuntimeException.class);
        assertThat(articleRepository.findById(ARTICLE_ID)).isPresent();
        assertThat(articleLikeCountRepository.findById(ARTICLE_ID)).isPresent();
        verify(outboxEventPublisher).publish(
                eq(EventType.ARTICLE_DELETED),
                any(ArticleDeletedEventPayload.class),
                eq(ARTICLE_ID)
        );
    }

    private void createArticleAndArticleLikeCount() {
        articleRepository.save(Article.create(ARTICLE_ID, "title", "content", 1L, 1L));
        articleLikeCountRepository.save(ArticleLikeCount.init(ARTICLE_ID, 0L));
    }
}

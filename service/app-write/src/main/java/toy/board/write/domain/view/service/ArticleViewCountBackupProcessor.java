package toy.board.write.domain.view.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import toy.board.common.event.EventType;
import toy.board.common.outboxmessagerelay.OutboxEventPublisher;
import toy.board.common.event.payload.ArticleViewedEventPayload;
import toy.board.write.domain.view.entity.ArticleViewCount;
import toy.board.write.domain.view.repository.ArticleViewCountBackupRepository;

@Component
@RequiredArgsConstructor
public class ArticleViewCountBackupProcessor {
    private final ArticleViewCountBackupRepository articleViewCountBackupRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void backup(Long articleId, Long viewCount) {
        int result = articleViewCountBackupRepository.updateViewCount(articleId, viewCount);
        if (result == 0) {
            articleViewCountBackupRepository.findById(articleId)
                    .ifPresentOrElse(ignored -> { },
                            () -> articleViewCountBackupRepository.save(ArticleViewCount.init(articleId, viewCount))
                    );
        }

        outboxEventPublisher.publish(
                EventType.ARTICLE_VIEWED,
                ArticleViewedEventPayload.builder()
                        .articleId(articleId)
                        .articleViewCount(viewCount)
                        .build(),
                articleId
        );
    }
}

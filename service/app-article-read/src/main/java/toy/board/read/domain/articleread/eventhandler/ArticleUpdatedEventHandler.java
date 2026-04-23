package toy.board.read.domain.articleread.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleUpdatedEventPayload;
import toy.board.read.repository.ArticleQueryModelRepository;

@Component("articleReadArticleUpdatedEventHandler")
@RequiredArgsConstructor
public class ArticleUpdatedEventHandler implements EventHandler<ArticleUpdatedEventPayload> {
    private final ArticleQueryModelRepository articleQueryModelRepository;

    @Override
    public void handle(Event<ArticleUpdatedEventPayload> event) {
        ArticleUpdatedEventPayload payload = event.getPayload();
        articleQueryModelRepository.read(payload.getArticleId())
                .ifPresent(articleQueryModel -> {
                    articleQueryModel.updateBy(payload);
                    articleQueryModelRepository.update(articleQueryModel);
                });
    }

    @Override
    public boolean supports(Event<ArticleUpdatedEventPayload> event) {
        return event.getType() == EventType.ARTICLE_UPDATED;
    }
}

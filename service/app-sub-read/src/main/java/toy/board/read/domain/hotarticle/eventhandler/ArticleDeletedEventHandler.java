package toy.board.read.domain.hotarticle.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleDeletedEventPayload;
import toy.board.read.domain.hotarticle.repository.ArticleCreatedTimeRepository;
import toy.board.read.domain.hotarticle.repository.HotArticleListRepository;

@Component("hotArticleArticleDeletedEventHandler")
@RequiredArgsConstructor
public class ArticleDeletedEventHandler implements EventHandler<ArticleDeletedEventPayload> {
    private final HotArticleListRepository hotArticleListRepository;
    private final ArticleCreatedTimeRepository articleCreatedTimeRepository;

    @Override
    public void handle(Event<ArticleDeletedEventPayload> event) {
        ArticleDeletedEventPayload payload = event.getPayload();
        articleCreatedTimeRepository.delete(payload.getArticleId());
        hotArticleListRepository.remove(payload.getArticleId(), payload.getCreatedAt());
    }

    @Override
    public boolean supports(Event<ArticleDeletedEventPayload> event) {
        return event.getType() == EventType.ARTICLE_DELETED;
    }

    @Override
    public Long findArticleId(Event<ArticleDeletedEventPayload> event) {
        return event.getPayload().getArticleId();
    }
}

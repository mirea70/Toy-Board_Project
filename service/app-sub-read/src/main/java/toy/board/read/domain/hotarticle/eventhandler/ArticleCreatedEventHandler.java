package toy.board.read.domain.hotarticle.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleCreatedEventPayload;
import toy.board.read.domain.hotarticle.repository.ArticleCreatedTimeRepository;
import toy.board.read.domain.hotarticle.util.TimeCalculatorUtils;

@Component("hotArticleArticleCreatedEventHandler")
@RequiredArgsConstructor
public class ArticleCreatedEventHandler implements EventHandler<ArticleCreatedEventPayload> {
    private final ArticleCreatedTimeRepository articleCreatedTimeRepository;

    @Override
    public void handle(Event<ArticleCreatedEventPayload> event) {
        ArticleCreatedEventPayload payload = event.getPayload();
        articleCreatedTimeRepository.createOrUpdate(
                payload.getArticleId(),
                payload.getCreatedAt(),
                TimeCalculatorUtils.calculateDurationToMidNight()
        );
    }

    @Override
    public boolean supports(Event<ArticleCreatedEventPayload> event) {
        return event.getType() == EventType.ARTICLE_CREATED;
    }

    @Override
    public Long findArticleId(Event<ArticleCreatedEventPayload> event) {
        return event.getPayload().getArticleId();
    }
}

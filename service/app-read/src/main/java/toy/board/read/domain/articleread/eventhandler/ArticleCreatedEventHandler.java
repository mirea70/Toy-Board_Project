package toy.board.read.domain.articleread.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.common.event.Event;
import toy.board.common.event.EventType;
import toy.board.common.event.payload.ArticleCreatedEventPayload;
import toy.board.read.domain.articleread.repository.ArticleIdListRepository;
import toy.board.read.domain.articleread.repository.ArticleQueryModel;
import toy.board.read.domain.articleread.repository.ArticleQueryModelRepository;

import java.time.Duration;

@Component("articleReadArticleCreatedEventHandler")
@RequiredArgsConstructor
public class ArticleCreatedEventHandler implements EventHandler<ArticleCreatedEventPayload> {
    private final ArticleQueryModelRepository articleQueryModelRepository;
    private final ArticleIdListRepository articleIdListRepository;

    @Override
    public void handle(Event<ArticleCreatedEventPayload> event) {
        ArticleCreatedEventPayload payload = event.getPayload();
        articleQueryModelRepository.create(
                ArticleQueryModel.create(payload),
                Duration.ofDays(1L)
        );
        articleIdListRepository.add(payload.getBoardId(), payload.getArticleId(), 1000L);
    }

    @Override
    public boolean supports(Event<ArticleCreatedEventPayload> event) {
        return event.getType() == EventType.ARTICLE_CREATED;
    }
}

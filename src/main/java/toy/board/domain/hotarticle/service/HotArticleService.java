package toy.board.domain.hotarticle.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.common.event.EventType;
import toy.board.domain.article.entity.Article;
import toy.board.domain.article.repository.ArticleRepository;
import toy.board.domain.hotarticle.eventhandler.EventHandler;
import toy.board.domain.hotarticle.repository.HotArticleListRepository;
import toy.board.domain.hotarticle.response.HotArticleResponse;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleService {
    private final ArticleRepository articleRepository;
    private final List<EventHandler> eventHandlers;
    private final HotArticleScoreUpdater hotArticleScoreUpdater;
    private final HotArticleListRepository hotArticleListRepository;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void handleEvent(Event<EventPayload> event) {
        EventHandler<EventPayload> eventHandler = findEventHandler(event);
        if (eventHandler == null)
            return;

        if (isArticleCreatedOrDeleted(event)) {
            eventHandler.handle(event);
        } else {
            hotArticleScoreUpdater.update(event, eventHandler);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private EventHandler<EventPayload> findEventHandler(Event<EventPayload> event) {
        return eventHandlers.stream()
                .filter(eventHandler -> eventHandler.supports(event))
                .findAny()
                .orElse(null);
    }

    private boolean isArticleCreatedOrDeleted(Event<EventPayload> event) {
        return event.getType() == EventType.ARTICLE_CREATED || event.getType() == EventType.ARTICLE_DELETED;
    }

    public List<HotArticleResponse> readAll(String dateStr) {
        return hotArticleListRepository.readAll(dateStr).stream()
                .map(id -> articleRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(HotArticleResponse::from)
                .toList();
    }
}

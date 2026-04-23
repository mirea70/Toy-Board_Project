package toy.board.read.domain.hotarticle.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.common.event.EventType;
import toy.board.read.client.ArticleClient;
import toy.board.read.repository.ArticleQueryModel;
import toy.board.read.repository.ArticleQueryModelRepository;
import toy.board.read.domain.hotarticle.eventhandler.EventHandler;
import toy.board.read.domain.hotarticle.repository.HotArticleListRepository;
import toy.board.read.domain.hotarticle.response.HotArticleResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleService {
    private final ArticleClient articleClient;
    private final List<EventHandler> eventHandlers;
    private final HotArticleScoreUpdater hotArticleScoreUpdater;
    private final HotArticleListRepository hotArticleListRepository;
    private final ArticleQueryModelRepository articleQueryModelRepository;

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
        List<Long> ids = hotArticleListRepository.readAll(dateStr);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleQueryModel> cached = articleQueryModelRepository.readAll(ids);
        return ids.stream()
                .map(id -> {
                    ArticleQueryModel model = cached.get(id);
                    if (model != null) {
                        return HotArticleResponse.from(model);
                    }
                    return articleClient.read(id)
                            .map(HotArticleResponse::from)
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();
    }
}

package toy.board.read.domain.hotarticle.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.common.event.EventType;
import toy.board.read.domain.hotarticle.eventhandler.EventHandler;
import toy.board.read.domain.hotarticle.repository.HotArticleListRepository;
import toy.board.read.domain.hotarticle.response.HotArticleResponse;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleService {
    // TODO Task 18: inject ArticleClient (RestClient) instead of local ArticleRepository/Article entity.
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
        // TODO Task 18: 결과 ID 목록을 ArticleClient.read로 매핑하여 HotArticleResponse 리스트로 변환
        hotArticleListRepository.readAll(dateStr);
        return Collections.emptyList();
    }
}

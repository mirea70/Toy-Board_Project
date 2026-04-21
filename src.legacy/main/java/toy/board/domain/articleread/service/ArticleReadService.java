package toy.board.domain.articleread.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import toy.board.common.event.Event;
import toy.board.common.event.EventPayload;
import toy.board.domain.article.response.ArticleResponse;
import toy.board.domain.article.service.ArticleService;
import toy.board.domain.articleread.eventhandler.EventHandler;
import toy.board.domain.articleread.repository.ArticleIdListRepository;
import toy.board.domain.articleread.repository.ArticleQueryModel;
import toy.board.domain.articleread.repository.ArticleQueryModelRepository;
import toy.board.domain.articleread.repository.BoardArticleCountRedisRepository;
import toy.board.domain.articleread.response.ArticleReadPageResponse;
import toy.board.domain.articleread.response.ArticleReadResponse;
import toy.board.domain.comment.service.CommentServiceV2;
import toy.board.domain.like.service.ArticleLikeService;
import toy.board.domain.view.service.ArticleViewService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleReadService {
    private final ArticleService articleService;
    private final CommentServiceV2 commentService;
    private final ArticleLikeService articleLikeService;
    private final ViewCountQueryService viewCountQueryService;
    private final ArticleViewService articleViewService;
    private final ArticleQueryModelRepository articleQueryModelRepository;
    private final List<EventHandler> eventHandlers;
    private final ArticleIdListRepository articleIdListRepository;
    private final BoardArticleCountRedisRepository boardArticleCountRepository;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void handleEvent(Event<EventPayload> event) {
        for (EventHandler eventHandler : eventHandlers) {
            if (eventHandler.supports(event)) {
                eventHandler.handle(event);
            }
        }
    }

    public ArticleReadResponse read(Long articleId, Long userId) {
        Long viewCount = articleViewService.increase(articleId, userId);
        ArticleQueryModel articleQueryModel = articleQueryModelRepository.read(articleId)
                .or(() -> fetch(articleId))
                .orElseThrow();
        return ArticleReadResponse.from(articleQueryModel, viewCount);
    }

    private Optional<ArticleQueryModel> fetch(Long articleId) {
        Optional<ArticleResponse> articleOpt;
        try {
            articleOpt = Optional.ofNullable(articleService.read(articleId));
        } catch (Exception e) {
            log.info("[ArticleReadService.fetch] articleId={} not found", articleId);
            return Optional.empty();
        }
        Optional<ArticleQueryModel> articleQueryModelOptional = articleOpt.map(article ->
                ArticleQueryModel.create(
                        article,
                        commentService.count(articleId),
                        articleLikeService.count(articleId)
                ));
        articleQueryModelOptional.ifPresent(
                articleQueryModel -> articleQueryModelRepository.create(articleQueryModel, Duration.ofDays(1))
        );
        log.info("[ArticleReadService.fetch] fetch data. articleId={}, isPresent={}", articleId, articleQueryModelOptional.isPresent());
        return articleQueryModelOptional;
    }

    public ArticleReadPageResponse readAll(Long boardId, Long page, Long pageSize) {
        return ArticleReadPageResponse.of(
                readAll(readAllArticleIds(boardId, page, pageSize)),
                count(boardId)
        );
    }

    public List<ArticleReadResponse> readAll(List<Long> articleIds) {
        Map<Long, ArticleQueryModel> articleQueryModelMap = articleQueryModelRepository.readAll(articleIds);
        return articleIds.stream()
                .map(articleId -> articleQueryModelMap.containsKey(articleId) ?
                        articleQueryModelMap.get(articleId) :
                        fetch(articleId).orElse(null))
                .filter(Objects::nonNull)
                .map(articleQueryModel ->
                        ArticleReadResponse.from(
                                articleQueryModel,
                                viewCountQueryService.count(articleQueryModel.getArticleId())
                        ))
                .toList();
    }

    private List<Long> readAllArticleIds(Long boardId, Long page, Long pageSize) {
        List<Long> articleIds = articleIdListRepository.readAll(boardId, (page - 1) * pageSize, pageSize);
        if (pageSize == articleIds.size()) {
            log.info("[ArticleReadService.readAllArticleIds] return redis data");
            return articleIds;
        }
        log.info("[ArticleReadService.readAllArticleIds] return origin data");
        return articleService.readAll(boardId, page, pageSize).getArticles().stream()
                .map(ArticleResponse::getArticleId)
                .toList();
    }

    public long count(Long boardId) {
        Long result = boardArticleCountRepository.read(boardId);
        if (result != null && result != 0L) {
            return result;
        }
        long count = articleService.count(boardId);
        boardArticleCountRepository.createOrUpdate(boardId, count);
        return count;
    }

    public List<ArticleReadResponse> readAllInfiniteScroll(Long boardId, Long lastArticleId, Long pageSize) {
        return readAll(readAllInfiniteScrollArticleIds(boardId, lastArticleId, pageSize));
    }

    private List<Long> readAllInfiniteScrollArticleIds(Long boardId, Long lastArticleId, Long pageSize) {
        List<Long> articleIds = articleIdListRepository.readAllInfiniteScroll(boardId, lastArticleId, pageSize);
        if (pageSize == articleIds.size()) {
            log.info("[ArticleReadService.readAllInfiniteScrollArticleIds] return redis data");
            return articleIds;
        }
        log.info("[ArticleReadService.readAllInfiniteScrollArticleIds] return origin data");
        return articleService.readAllInfiniteScroll(boardId, pageSize, lastArticleId).stream()
                .map(ArticleResponse::getArticleId)
                .toList();
    }
}

package toy.board.domain.articleread.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import toy.board.domain.articleread.cache.OptimizedCacheable;
import toy.board.domain.view.service.ArticleViewService;

/**
 * ViewClient 대체: in-process 뷰 카운트 조회 + OptimizedCache 적용.
 * 원본 ViewClient.count()에 걸려있던 @OptimizedCacheable(type="articleViewCount", ttlSeconds=1) 동작을 유지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountQueryService {
    private final ArticleViewService articleViewService;

    @OptimizedCacheable(type = "articleViewCount", ttlSeconds = 1)
    public long count(Long articleId) {
        log.info("[ViewCountQueryService.count] articleId={}", articleId);
        Long c = articleViewService.count(articleId);
        return c == null ? 0L : c;
    }
}

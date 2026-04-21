package toy.board.read.domain.articleread.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import toy.board.read.domain.articleread.cache.OptimizedCacheable;

/**
 * ViewClient 대체: in-process 뷰 카운트 조회 + OptimizedCache 적용.
 * 원본 ViewClient.count()에 걸려있던 @OptimizedCacheable(type="articleViewCount", ttlSeconds=1) 동작을 유지.
 */
// TODO Task 18: replace the stubbed count(...) with ArticleViewClient.count(articleId) (RestClient call to app-write).
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountQueryService {

    @OptimizedCacheable(type = "articleViewCount", ttlSeconds = 1)
    public long count(Long articleId) {
        log.info("[ViewCountQueryService.count] stubbed articleId={}", articleId);
        return 0L;
    }
}

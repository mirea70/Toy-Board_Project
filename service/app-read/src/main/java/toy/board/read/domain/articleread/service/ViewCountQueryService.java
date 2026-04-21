package toy.board.read.domain.articleread.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import toy.board.read.client.ViewClient;
import toy.board.read.domain.articleread.cache.OptimizedCacheable;

/**
 * ViewClient 조회 wrapper: OptimizedCache 적용.
 * 원본 ViewClient.count()에 걸려있던 @OptimizedCacheable(type="articleViewCount", ttlSeconds=1) 동작을 유지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountQueryService {

    private final ViewClient viewClient;

    @OptimizedCacheable(type = "articleViewCount", ttlSeconds = 1)
    public long count(Long articleId) {
        return viewClient.count(articleId);
    }
}

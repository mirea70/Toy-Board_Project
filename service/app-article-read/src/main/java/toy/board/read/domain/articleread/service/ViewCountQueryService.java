package toy.board.read.domain.articleread.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import toy.board.read.client.ViewClient;
import toy.board.read.cache.OptimizedCacheable;

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

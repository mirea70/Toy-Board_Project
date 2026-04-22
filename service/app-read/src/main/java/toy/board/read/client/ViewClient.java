package toy.board.read.client;

import jakarta.annotation.PostConstruct;
import toy.board.read.domain.articleread.cache.OptimizedCacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewClient {
    private RestClient restClient;
    @Value("${endpoints.toy-board-write-app.url}")
    private String viewServiceUrl;

    @PostConstruct
    public void initRestClient() {
        restClient = RestClient.create(viewServiceUrl);
    }

    @OptimizedCacheable(type = "articleViewCount", ttlSeconds = 1)
    public long count(Long articleId) {
        log.info("[ViewClient.count] articleId={}", articleId);
        try {
            return restClient.get()
                    .uri("/v1/article-views/articles/{articleId}/count", articleId)
                    .retrieve()
                    .body(Long.class);
        } catch (Exception e) {
            log.error("[ViewClient.count] articleId={}", articleId, e);
            return 0;
        }
    }

    public Map<Long, Long> countAll(List<Long> articleIds) {
        if (articleIds.isEmpty()) return Map.of();
        try {
            String ids = articleIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            Map<Long, Long> result = restClient.get()
                    .uri("/v1/article-views/articles/count?articleIds=" + ids)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<Long, Long>>() {});
            return result != null ? result : Map.of();
        } catch (Exception e) {
            log.error("[ViewClient.countAll] articleIds={}", articleIds, e);
            return fallback(articleIds);
        }
    }

    private Map<Long, Long> fallback(List<Long> articleIds) {
        Map<Long, Long> result = new HashMap<>();
        for (Long id : articleIds) result.put(id, 0L);
        return result;
    }
}

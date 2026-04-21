package toy.board.read.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import toy.board.read.client.ArticleClient;
import toy.board.read.domain.articleread.repository.ArticleIdListRepository;
import toy.board.read.domain.articleread.service.ArticleReadService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleQueryModelPrepopulator implements ApplicationRunner {
    private final ArticleClient articleClient;
    private final ArticleReadService articleReadService;
    private final ArticleIdListRepository articleIdListRepository;

    private static final Long BOARD_ID = 1L;
    private static final Long PAGE_SIZE = 100L;
    private static final long MAX_PAGES = 1000L;  // safety cap (article 100K 까지)
    private static final Long ARTICLE_ID_LIST_LIMIT = 1000L;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[ArticleQueryModelPrepopulator] start - boardId={}", BOARD_ID);
        Instant start = Instant.now();
        long total = 0;
        try {
            for (long page = 1; page <= MAX_PAGES; page++) {
                ArticleClient.ArticlePageResponse response = articleClient.readAll(BOARD_ID, page, PAGE_SIZE);
                List<ArticleClient.ArticleResponse> articles = response == null ? List.of() : response.getArticles();
                if (articles == null || articles.isEmpty()) {
                    log.info("[ArticleQueryModelPrepopulator] reached end at page={}", page);
                    break;
                }
                for (ArticleClient.ArticleResponse article : articles) {
                    try {
                        articleReadService.prepopulate(article.getArticleId());
                        articleIdListRepository.add(BOARD_ID, article.getArticleId(), ARTICLE_ID_LIST_LIMIT);
                        total++;
                    } catch (Exception e) {
                        log.warn("[ArticleQueryModelPrepopulator] skip articleId={} due to {}", article.getArticleId(), e.toString());
                    }
                }
                if (page % 10 == 0) {
                    log.info("[ArticleQueryModelPrepopulator] progress: page={}, total={}, elapsed={}s",
                            page, total, Duration.between(start, Instant.now()).getSeconds());
                }
            }
        } catch (Exception e) {
            log.error("[ArticleQueryModelPrepopulator] failed at page-loop, total prepopulated={}", total, e);
        }
        log.info("[ArticleQueryModelPrepopulator] done - total={}, elapsed={}s",
                total, Duration.between(start, Instant.now()).getSeconds());
    }
}

package toy.board.write.domain.article.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import toy.board.write.domain.article.response.ArticlePageResponse;
import toy.board.write.domain.article.response.ArticleResponse;
import toy.board.write.domain.article.service.ArticleService;

import java.util.List;

/**
 * Raw GET endpoints exposed by app-write for cache-miss fallback / CQRS read-side.
 * Called by read-app's ArticleClient from inside the docker network.
 */
@RestController
@RequiredArgsConstructor
public class ArticleRawReadController {
    private final ArticleService articleService;

    @GetMapping("/v1/articles/{articleId}")
    public ArticleResponse read(@PathVariable("articleId") Long articleId) {
        return articleService.read(articleId);
    }

    @GetMapping("/v1/articles")
    public ArticlePageResponse readAll(
            @RequestParam("boardId") Long boardId,
            @RequestParam("page") Long page,
            @RequestParam("pageSize") Long pageSize
    ) {
        return articleService.readAll(boardId, page, pageSize);
    }

    @GetMapping("/v1/articles/infinite-scroll")
    public List<ArticleResponse> readAllInfiniteScroll(
            @RequestParam("boardId") Long boardId,
            @RequestParam(value = "lastArticleId", required = false) Long lastArticleId,
            @RequestParam("pageSize") Long pageSize
    ) {
        return articleService.readAllInfiniteScroll(boardId, pageSize, lastArticleId);
    }
}

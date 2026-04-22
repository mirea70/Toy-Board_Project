package toy.board.write.domain.view.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import toy.board.write.domain.view.service.ArticleViewService;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ArticleViewController {
    private final ArticleViewService articleViewService;

    @GetMapping("/v1/article-views/articles/{articleId}/count")
    public Long count(@PathVariable("articleId") Long articleId) {
        return articleViewService.count(articleId);
    }

    @GetMapping("/v1/article-views/articles/count")
    public Map<Long, Long> countAll(@RequestParam("articleIds") List<Long> articleIds) {
        return articleViewService.countAll(articleIds);
    }
}

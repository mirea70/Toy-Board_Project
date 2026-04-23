package toy.board.read.domain.hotarticle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import toy.board.read.domain.hotarticle.repository.ArticleCommentCountRepository;
import toy.board.read.domain.hotarticle.repository.ArticleLikeCountRepository;
import toy.board.read.domain.hotarticle.repository.ArticleViewCountRepository;

@Component
@RequiredArgsConstructor
public class HotArticleScoreCalculator {
    private final ArticleLikeCountRepository articleLikeCountRepository;
    private final ArticleCommentCountRepository articleCommentCountRepository;
    private final ArticleViewCountRepository articleViewCountRepository;

    private static final long ARTICLE_LIKE_COUNT_WEIGHT = 3;
    private static final long ARTICLE_COMMENT_COUNT_WEIGHT = 2;
    private static final long ARTICLE_VIEW_COUNT_WEIGHT = 1;

    public long calculate(Long articleId) {
        long articleLikeCount = articleLikeCountRepository.read(articleId);
        long articleCommentCount = articleCommentCountRepository.read(articleId);
        long articleViewCount = articleViewCountRepository.read(articleId);

        return articleLikeCount * ARTICLE_LIKE_COUNT_WEIGHT
                + articleCommentCount * ARTICLE_COMMENT_COUNT_WEIGHT
                + articleViewCount * ARTICLE_VIEW_COUNT_WEIGHT;
    }
}

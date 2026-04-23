package toy.board.read.repository;

import lombok.Getter;
import toy.board.common.event.payload.ArticleCreatedEventPayload;
import toy.board.common.event.payload.ArticleLikedEventPayload;
import toy.board.common.event.payload.ArticleUnlikedEventPayload;
import toy.board.common.event.payload.ArticleUpdatedEventPayload;
import toy.board.common.event.payload.CommentCreatedEventPayload;
import toy.board.common.event.payload.CommentDeletedEventPayload;
import toy.board.read.client.ArticleClient;

import java.time.LocalDateTime;

@Getter
public class ArticleQueryModel {
    private Long articleId;
    private String title;
    private String content;
    private Long boardId;
    private Long writerId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private Long articleCommentCount;
    private Long articleLikeCount;

    public static ArticleQueryModel create(ArticleCreatedEventPayload payload) {
        ArticleQueryModel articleQueryModel = new ArticleQueryModel();
        articleQueryModel.articleId = payload.getArticleId();
        articleQueryModel.title = payload.getTitle();
        articleQueryModel.content = payload.getContent();
        articleQueryModel.boardId = payload.getBoardId();
        articleQueryModel.writerId = payload.getWriterId();
        articleQueryModel.createdAt = payload.getCreatedAt();
        articleQueryModel.modifiedAt = payload.getModifiedAt();
        articleQueryModel.articleCommentCount = 0L;
        articleQueryModel.articleLikeCount = 0L;
        return articleQueryModel;
    }

    public static ArticleQueryModel create(
            ArticleClient.ArticleResponse article,
            Long articleCommentCount,
            Long articleLikeCount) {
        ArticleQueryModel model = new ArticleQueryModel();
        model.articleId = article.getArticleId();
        model.title = article.getTitle();
        model.content = article.getContent();
        model.boardId = article.getBoardId();
        model.writerId = article.getWriterId();
        model.createdAt = article.getCreatedAt();
        model.modifiedAt = article.getModifiedAt();
        model.articleCommentCount = articleCommentCount;
        model.articleLikeCount = articleLikeCount;
        return model;
    }

    public void updateBy(CommentCreatedEventPayload payload) {
        this.articleCommentCount = payload.getArticleCommentCount();
    }

    public void updateBy(CommentDeletedEventPayload payload) {
        this.articleCommentCount = payload.getArticleCommentCount();
    }

    public void updateBy(ArticleLikedEventPayload payload) {
        this.articleLikeCount = payload.getArticleLikeCount();
    }

    public void updateBy(ArticleUnlikedEventPayload payload) {
        this.articleLikeCount = payload.getArticleLikeCount();
    }

    public void updateBy(ArticleUpdatedEventPayload payload) {
        this.title = payload.getTitle();
        this.content = payload.getContent();
        this.boardId = payload.getBoardId();
        this.writerId = payload.getWriterId();
        this.createdAt = payload.getCreatedAt();
        this.modifiedAt = payload.getModifiedAt();
    }
}

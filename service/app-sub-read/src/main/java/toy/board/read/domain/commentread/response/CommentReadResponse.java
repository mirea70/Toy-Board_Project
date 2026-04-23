package toy.board.read.domain.commentread.response;

import lombok.Getter;
import lombok.ToString;
import toy.board.read.domain.commentread.repository.CommentQueryModel;

import java.time.LocalDateTime;

@Getter
@ToString
public class CommentReadResponse {
    private Long commentId;
    private String content;
    private Long articleId;
    private Long writerId;
    private String path;
    private Boolean deleted;
    private LocalDateTime createdAt;

    public static CommentReadResponse from(CommentQueryModel commentQueryModel) {
        CommentReadResponse response = new CommentReadResponse();
        response.commentId = commentQueryModel.getCommentId();
        response.content = commentQueryModel.getContent();
        response.articleId = commentQueryModel.getArticleId();
        response.writerId = commentQueryModel.getWriterId();
        response.path = commentQueryModel.getPath();
        response.deleted = commentQueryModel.getDeleted();
        response.createdAt = commentQueryModel.getCreatedAt();
        return response;
    }
}

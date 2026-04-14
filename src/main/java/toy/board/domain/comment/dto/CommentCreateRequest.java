package toy.board.domain.comment.dto;

import lombok.Getter;

@Getter
public class CommentCreateRequest {
    private Long articleId;
    private String content;
    private Long parentCommentId;
    private Long writerId;
}

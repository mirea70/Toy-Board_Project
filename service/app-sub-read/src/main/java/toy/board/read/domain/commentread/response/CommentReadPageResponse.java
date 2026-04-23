package toy.board.read.domain.commentread.response;

import lombok.Getter;

import java.util.List;

@Getter
public class CommentReadPageResponse {
    private List<CommentReadResponse> comments;
    private Long commentCount;

    public static CommentReadPageResponse of(List<CommentReadResponse> comments, Long commentCount) {
        CommentReadPageResponse response = new CommentReadPageResponse();
        response.comments = comments;
        response.commentCount = commentCount;
        return response;
    }
}

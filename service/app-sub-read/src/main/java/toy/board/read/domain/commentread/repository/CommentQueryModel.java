package toy.board.read.domain.commentread.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import toy.board.common.event.payload.CommentCreatedEventPayload;

import java.time.LocalDateTime;

@Getter
public class CommentQueryModel {
    private final Long commentId;
    private final String content;
    private final Long articleId;
    private final Long writerId;
    private final String path;
    private final Boolean deleted;
    private final LocalDateTime createdAt;

    @JsonCreator
    public CommentQueryModel(
            @JsonProperty("commentId") Long commentId,
            @JsonProperty("content") String content,
            @JsonProperty("articleId") Long articleId,
            @JsonProperty("writerId") Long writerId,
            @JsonProperty("path") String path,
            @JsonProperty("deleted") Boolean deleted,
            @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.commentId = commentId;
        this.content = content;
        this.articleId = articleId;
        this.writerId = writerId;
        this.path = path;
        this.deleted = deleted;
        this.createdAt = createdAt;
    }

    public static CommentQueryModel from(CommentCreatedEventPayload payload) {
        return new CommentQueryModel(
                payload.getCommentId(),
                payload.getContent(),
                payload.getArticleId(),
                payload.getWriterId(),
                payload.getPath(),
                payload.getDeleted(),
                payload.getCreatedAt()
        );
    }
}

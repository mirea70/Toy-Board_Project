package toy.board.write.domain.comment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import toy.board.write.domain.comment.dto.CommentCreateRequestV2;
import toy.board.write.domain.comment.response.CommentResponse;
import toy.board.write.domain.comment.service.CommentServiceV2;

@RestController
@RequiredArgsConstructor
public class CommentWriteController {
    private final CommentServiceV2 commentService;

    @PostMapping("/v2/comments")
    public CommentResponse create(@RequestBody CommentCreateRequestV2 request) {
        return commentService.create(request);
    }

    @DeleteMapping("/v2/comments/{commentId}")
    public void delete(@PathVariable("commentId") Long commentId) {
        commentService.delete(commentId);
    }
}

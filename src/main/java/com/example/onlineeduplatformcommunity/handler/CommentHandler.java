package com.example.onlineeduplatformcommunity.handler;

import com.example.onlineeduplatformcommunity.model.Comment;
import com.example.onlineeduplatformcommunity.model.KafkaProducer;
import com.example.onlineeduplatformcommunity.repository.CommentRepository;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@NoArgsConstructor
public class CommentHandler {

    private static final Logger logger = LoggerFactory.getLogger(CommentHandler.class);

    private CommentRepository commentRepository;
    private KafkaProducer kafkaProducer;

    @Autowired
    public CommentHandler(CommentRepository commentRepository, KafkaProducer kafkaProducer) {
        this.commentRepository = commentRepository;
        this.kafkaProducer = kafkaProducer;
    }

    public CommentHandler(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    public Mono<ServerResponse> createComment(ServerRequest serverRequest) {
        Long articleId = Long.parseLong(serverRequest.pathVariable("articleId"));
        Mono<Comment> commentMono = serverRequest.bodyToMono(Comment.class)
                .onErrorResume(throwable -> {
                    System.out.println(throwable.getMessage());
                    return Mono.error(new RuntimeException(throwable));
                })
                .flatMap(x -> Mono.just(new Comment(articleId, x.getUserId(), x.getContent())));

        this.kafkaProducer.sendMessage("comment Good");

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(
//                commentMono.flatMap(this.commentRepository::save),Comment.class);
                commentMono.flatMap(comment -> {
                    return this.commentRepository.save(comment);
                }), Comment.class);
    }

    public Mono<ServerResponse> getCommentList(ServerRequest serverRequest) {

        Long articleId = Long.parseLong(serverRequest.pathVariable("articleId"));
        Mono<ServerResponse> notFound = ServerResponse.notFound().build();
        Flux<Comment> commentMono = this.commentRepository.findByArticleId(articleId)
                .filter(comment -> comment.isBlockYn() == false);

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .body(commentMono, Comment.class);

    }

    public Mono<ServerResponse> blockComment(ServerRequest serverRequest) {
        Long commentId = Long.parseLong(serverRequest.pathVariable("commentId"));
        Mono<Comment> commentMono = this.commentRepository.findById(commentId)
                .flatMap(x -> {
                    return Mono.just(new Comment(x.getCommentId(), x.getArticleId(), x.getUserId(), x.getContent(), true));
                });

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(
                commentMono.flatMap(this.commentRepository::save), Comment.class);
    }
}


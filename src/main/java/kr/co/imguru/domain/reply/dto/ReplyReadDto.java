package kr.co.imguru.domain.reply.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReplyReadDto {

    private Long replyId;

    private String memberNickname;

    private Long postId;

    private String postTitle;

    private String content;

    private Long likeCnt;

    private LocalDateTime regDate;

    private String memberSkill;
}

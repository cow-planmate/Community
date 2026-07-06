package com.planmate.community.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_002", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_003", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_004", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_005", "서버 오류가 발생했습니다."),

    // 게시글
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "게시글을 찾을 수 없습니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_002", "게시글에 대한 권한이 없습니다."),

    // 댓글
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_001", "댓글을 찾을 수 없습니다."),
    COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "COMMENT_002", "댓글에 대한 권한이 없습니다."),
    COMMENT_REPLY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "COMMENT_003", "대댓글에는 답글을 달 수 없습니다."),

    // 메이트 모집
    MATE_ALREADY_JOINED(HttpStatus.CONFLICT, "MATE_001", "이미 참여한 메이트 모집입니다."),
    MATE_FULL(HttpStatus.CONFLICT, "MATE_002", "모집 인원이 가득 찼습니다."),
    MATE_NOT_JOINED(HttpStatus.BAD_REQUEST, "MATE_003", "참여하지 않은 메이트 모집입니다."),
    MATE_CLOSED(HttpStatus.CONFLICT, "MATE_004", "마감된 메이트 모집입니다."),

    // 피드
    FEED_ALREADY_FORKED(HttpStatus.CONFLICT, "FEED_001", "이미 가져간 일정입니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

package com.planmate.community.common.exception;

import lombok.Getter;

@Getter
public class CommunityException extends RuntimeException {

    private final ErrorCode errorCode;

    public CommunityException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CommunityException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

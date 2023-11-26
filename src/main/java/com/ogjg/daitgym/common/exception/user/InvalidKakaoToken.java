package com.ogjg.daitgym.common.exception.user;

import com.ogjg.daitgym.common.exception.CustomException;
import com.ogjg.daitgym.common.exception.ErrorCode;
import com.ogjg.daitgym.common.exception.ErrorData;

public class InvalidKakaoToken extends CustomException {

    public InvalidKakaoToken() {
        super(ErrorCode.INVALID_KAKAO_ACCESS_TOKEN);
    }

    public InvalidKakaoToken(String message) {
        super(ErrorCode.INVALID_KAKAO_ACCESS_TOKEN, message);
    }

    public InvalidKakaoToken(ErrorData errorData) {
        super(ErrorCode.INVALID_KAKAO_ACCESS_TOKEN, errorData);
    }
}

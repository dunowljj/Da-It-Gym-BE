package com.ogjg.daitgym.user.dto.response;

import com.ogjg.daitgym.domain.UserAuthentication;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
public class WithdrawKakaoResponse {
    private Long targetId;

    @Builder
    public WithdrawKakaoResponse(Long targetId) {
        this.targetId = targetId;
    }

    public static WithdrawKakaoResponse from(UserAuthentication findAuthentication) {
        return WithdrawKakaoResponse.builder()
                .targetId(findAuthentication.getProviderId())
                .build();
    }
}

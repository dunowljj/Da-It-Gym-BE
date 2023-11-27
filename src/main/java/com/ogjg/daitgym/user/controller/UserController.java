package com.ogjg.daitgym.user.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ogjg.daitgym.alarm.dto.FcmTokenRequestDto;
import com.ogjg.daitgym.alarm.service.FcmAlarmService;
import com.ogjg.daitgym.common.exception.ErrorCode;
import com.ogjg.daitgym.common.response.ApiResponse;
import com.ogjg.daitgym.config.security.details.OAuth2JwtUserDetails;
import com.ogjg.daitgym.user.dto.request.ApplyForApprovalRequest;
import com.ogjg.daitgym.user.dto.request.EditNicknameRequest;
import com.ogjg.daitgym.user.dto.request.EditUserProfileRequest;
import com.ogjg.daitgym.user.dto.request.RegisterInbodyRequest;
import com.ogjg.daitgym.user.dto.response.*;
import com.ogjg.daitgym.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FcmAlarmService fcmAlarmService;

    private final ObjectMapper objectMapper;

    /**
     * 로그아웃 - 메시지 지정 필요
     */
    @PostMapping("/logout")
    public ApiResponse<?> logout(HttpServletResponse response,
                                 @AuthenticationPrincipal OAuth2JwtUserDetails oAuth2JwtUserDetails,
                                 @RequestBody FcmTokenRequestDto fcmTokenRequestDto) {
        response.setHeader("Set-Cookie", userService.getExpiredResponseCookie().toString());
        fcmAlarmService.deleteFcmToken(oAuth2JwtUserDetails, fcmTokenRequestDto);
        return new ApiResponse<>(ErrorCode.SUCCESS.changeMessage("로그아웃 성공"));
    }

    /**
     * 닉네임 중복 검사
     */
    @GetMapping("/check-duplication")
    public ApiResponse<?> checkNicknameDuplication(
            @RequestParam("nickname") String nickname,
            @AuthenticationPrincipal OAuth2JwtUserDetails userDetails
    ) {
        String message = userService.checkNicknameDuplication(nickname, userDetails.getNickname());
        return new ApiResponse<>(ErrorCode.SUCCESS.changeMessage(message));
    }

    /**
     * 닉네임 초기 변경
     */
    @PatchMapping("/nickname")
    public ApiResponse<EditInitialNicknameResponse> editInitialNickname(
            @RequestBody EditNicknameRequest request,
            @AuthenticationPrincipal OAuth2JwtUserDetails userDetails
    ) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.changeMessage("닉네임 변경 완료"),
                userService.editInitialNickname(userDetails.getEmail(), request)
        );
    }

    /**
     * 회원 탈퇴
     */
    @PatchMapping("/withdraw")
    public ApiResponse<?> withdraw(
            @AuthenticationPrincipal OAuth2JwtUserDetails userDetails
    ) {
        userService.updateUserDeleted(userDetails.getEmail());
        return new ApiResponse<>(ErrorCode.SUCCESS.changeMessage("회원탈퇴 성공"));
    }

    /**
     * 프로필 불러오기
     */
    @GetMapping("/{nickname}")
    public ApiResponse<GetUserProfileGetResponse> getUserProfile(
            @PathVariable("nickname") String nickname,
            @AuthenticationPrincipal OAuth2JwtUserDetails userDetails
    ) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS,
                userService.getUserProfile(userDetails.getEmail(), nickname)
        );
    }

    /**
     * 프로필 편집
     */
    @PutMapping("/{nickname}")
    public ApiResponse<EditUserProfileResponse> editUserProfile(
            @PathVariable("nickname") String nickname,
            @RequestPart String request,
            @RequestPart(required = false) MultipartFile userProfileImg,
            @AuthenticationPrincipal OAuth2JwtUserDetails userDetails
    ) throws JsonProcessingException {

        return new ApiResponse<>(
                ErrorCode.SUCCESS,
                userService.editUserProfile(
                        userDetails.getEmail(),
                        nickname,
                        objectMapper.readValue(request, EditUserProfileRequest.class),
                        userProfileImg
                ));
    }

    /**
     * 트레이너 심사 신청
     */
    @PostMapping("/career/submit")
    public ApiResponse<Void> applyForApproval(
            @RequestPart String request,
            @RequestPart(required = false) List<MultipartFile> certificationImgs,
            @RequestPart(required = false) List<MultipartFile> awardImgs,
            @AuthenticationPrincipal OAuth2JwtUserDetails userDetails
    ) throws JsonProcessingException {

        userService.applyForApproval(
                userDetails.getEmail(),
                objectMapper.readValue(request, ApplyForApprovalRequest.class),
                awardImgs,
                certificationImgs
        );
        return new ApiResponse<>(ErrorCode.SUCCESS);
    }

    /**
     * 인바디 등록
     */
    @PostMapping("/inbodies")
    public ApiResponse<Void> registerInbody(
            @RequestBody RegisterInbodyRequest request,
            @AuthenticationPrincipal OAuth2JwtUserDetails userDetails
    ) {
        userService.registerInbody(userDetails.getEmail(), request);
        return new ApiResponse<>(ErrorCode.SUCCESS);
    }

    /**
     * 인바디 조회
     */
    @GetMapping("/{nickname}/inbodies")
    public ApiResponse<GetInbodiesResponse> getInbodies(
            @PathVariable("nickname") String nickname,
            @AuthenticationPrincipal OAuth2JwtUserDetails userDetails
    ) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS,
                userService.getInbodies(nickname)
        );
    }

    /**
     * 유저 검색
     */
    @GetMapping("/search")
    public ApiResponse<GetSearchUsersResponse> getUsers(
            @RequestParam("nickname") String nickname,
            @PageableDefault(page = 0, size = 10) Pageable pageable
    ) {

        return new ApiResponse<>(
                ErrorCode.SUCCESS,
                userService.getSearchedUsers(nickname, pageable)
        );
    }

    /**
     * 회원탈퇴를 위한 카카오 아이디 조회
     */
    @GetMapping("/kakao-id")
    public ApiResponse<WithdrawKakaoResponse> getKakaoId(
            @AuthenticationPrincipal OAuth2JwtUserDetails oAuth2JwtUserDetails
    ) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS,
                userService.getKakaoId(oAuth2JwtUserDetails.getEmail())
        );
    }
}

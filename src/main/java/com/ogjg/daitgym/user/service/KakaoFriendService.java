package com.ogjg.daitgym.user.service;

import com.ogjg.daitgym.common.exception.user.ForbiddenKaKaoSocial;
import com.ogjg.daitgym.common.exception.user.InvalidKakaoToken;
import com.ogjg.daitgym.common.exception.user.NotFoundUser;
import com.ogjg.daitgym.domain.Inbody;
import com.ogjg.daitgym.domain.UserAuthentication;
import com.ogjg.daitgym.user.dto.request.KaKaoFriendsRequest;
import com.ogjg.daitgym.user.dto.response.KaKaoFriendResponseDto;
import com.ogjg.daitgym.user.dto.response.KaKaoFriendsResponse;
import com.ogjg.daitgym.user.repository.InbodyRepository;
import com.ogjg.daitgym.user.repository.UserAuthenticationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KakaoFriendService {

    private final RestTemplate restTemplate;
    private final UserAuthenticationRepository userAuthenticationRepository;
    private final InbodyRepository inbodyRepository;

    public KakaoFriendService(UserAuthenticationRepository userAuthenticationRepository, InbodyRepository inbodyRepository) {
        this.restTemplate = new RestTemplate();
        this.userAuthenticationRepository = userAuthenticationRepository;
        this.inbodyRepository = inbodyRepository;
    }


    /**
     * 카카오 서버로 accessToken을 담아 요청을 보내 친구목록을 받아오기
     *
     * @param email 로그인중인 사용자 이메일
     */
    @Transactional
    public KaKaoFriendsResponse requestKaKaoFriendsList(String email) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + getAccessToken(email));
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<KaKaoFriendsRequest> response = restTemplate.exchange(
                    "https://kapi.kakao.com/v1/api/talk/friends",
                    HttpMethod.GET,
                    entity,
                    KaKaoFriendsRequest.class
            );

            KaKaoFriendsRequest kaKaoFriendsRequest = response.getBody();
            List<KaKaoFriendResponseDto> responseDtoList = new ArrayList<>();

            // todo : 조회시에 탈퇴한 회원에 대한 구분과 처리 필요
            kaKaoFriendsRequest.getElements().forEach(
                    kaKaoFriendsRequestDto -> {
                        UserAuthentication userAuthentication = userAuthenticationRepository.findByProviderId(kaKaoFriendsRequestDto.getId())
                                .orElse(null);

                        if (userAuthentication == null) {
                            log.info("해당 providerId가 존재하지 않습니다. id = {}", kaKaoFriendsRequestDto.getId());
                            return;
                        }
//                                .orElseThrow(NotFoundUserAuthentication::new);

                        responseDtoList.add(
                                new KaKaoFriendResponseDto(
                                        userAuthentication.getUser().getNickname(),
                                        userAuthentication.getUser().getImageUrl(),
                                        userAuthentication.getUser().getIntroduction(),
                                        inbodyRepository.findFirstByUserEmailOrderByCreatedAtDesc(
                                                        userAuthentication.getUser().getEmail())
                                                .map(Inbody::getScore)
                                                .orElse(0)
                                )
                        );
                    });

            return new KaKaoFriendsResponse(responseDtoList);
        } catch (HttpClientErrorException.Forbidden e) {
            log.error(e.getMessage());
            throw new ForbiddenKaKaoSocial();
        }
        catch (HttpClientErrorException.Unauthorized e) {
            log.error(e.getMessage());
            throw new InvalidKakaoToken("카카오 Token 오류");
        }
    }

    private String getAccessToken(String email) {
        return userAuthenticationRepository.findByUserEmail(email)
                .orElseThrow(NotFoundUser::new)
                .getAccessToken();
    }
}
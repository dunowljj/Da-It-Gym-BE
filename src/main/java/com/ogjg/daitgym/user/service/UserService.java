package com.ogjg.daitgym.user.service;

import com.ogjg.daitgym.approval.repository.ApprovalRepository;
import com.ogjg.daitgym.approval.repository.AwardRepository;
import com.ogjg.daitgym.approval.repository.CertificationRepository;
import com.ogjg.daitgym.comment.feedExerciseJournal.exception.WrongApproach;
import com.ogjg.daitgym.common.exception.user.AlreadyExistNickname;
import com.ogjg.daitgym.common.exception.user.AlreadyProceedingApproval;
import com.ogjg.daitgym.common.exception.user.EmptyTrainerApplyException;
import com.ogjg.daitgym.common.exception.user.NotFoundUser;
import com.ogjg.daitgym.domain.Approval;
import com.ogjg.daitgym.domain.HealthClub;
import com.ogjg.daitgym.domain.Inbody;
import com.ogjg.daitgym.domain.User;
import com.ogjg.daitgym.domain.follow.Follow;
import com.ogjg.daitgym.domain.routine.Routine;
import com.ogjg.daitgym.follow.repository.FollowRepository;
import com.ogjg.daitgym.journal.repository.journal.ExerciseJournalRepository;
import com.ogjg.daitgym.routine.repository.RoutineRepository;
import com.ogjg.daitgym.s3.service.S3UserService;
import com.ogjg.daitgym.user.dto.request.ApplyForApprovalRequest;
import com.ogjg.daitgym.user.dto.request.EditNicknameRequest;
import com.ogjg.daitgym.user.dto.request.EditUserProfileRequest;
import com.ogjg.daitgym.user.dto.request.RegisterInbodyRequest;
import com.ogjg.daitgym.user.dto.response.*;
import com.ogjg.daitgym.user.repository.HealthClubRepository;
import com.ogjg.daitgym.user.repository.InbodyRepository;
import com.ogjg.daitgym.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.ogjg.daitgym.domain.ApproveStatus.WAITING;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final UserHelper userHelper;

    private final HealthClubRepository healthClubRepository;

    private final FollowRepository followRepository;

    private final ApprovalRepository approvalRepository;

    private final AwardRepository awardRepository;

    private final CertificationRepository certificationRepository;

    private final InbodyRepository inbodyRepository;

    private final RoutineRepository routineRepository;

    private final ExerciseJournalRepository exerciseJournalRepository;

    private final S3UserService s3UserService;

    @Transactional(readOnly = true)
    public GetUserProfileGetResponse getUserProfile(String loginEmail, String nickname) {
        User targetUser = userHelper.findUserByNickname(nickname);

        return GetUserProfileGetResponse.builder()
                .nickname(targetUser.getNickname())
                .preferredSplit(targetUser.getPreferredSplit().getTitle())
                .userProfileImgUrl(targetUser.getImageUrl())
                .introduction(targetUser.getIntroduction())
                .healthClubName(targetUser.getHealthClub().getName())
                .isFollower(isTargetUserFollowedByLoginUser(targetUser.getEmail(), loginEmail))
                .role(targetUser.getRole().getTitle())
                .journalCount(exerciseJournalRepository.countByUserEmail(targetUser.getEmail()))
                .followerCount(followRepository.countByFollowPKTargetEmail(targetUser.getEmail()))
                .followingCount(followRepository.countByFollowPKFollowerEmail(targetUser.getEmail()))
                .isMyProfile(targetUser.getEmail().equals(loginEmail))
                .submitTrainerQualification(hasProceedingApproval(targetUser))
                .build();
    }

    private boolean isTargetUserFollowedByLoginUser(String targetEmail, String loginEmail) {
        return followRepository.findByFollowPK(Follow.createFollowPK(targetEmail, loginEmail))
                .isPresent();
    }

    private boolean hasProceedingApproval(User user) {
        List<Approval> awardApprovals = awardRepository.findByUserEmail(user.getEmail()).stream()
                .map((award -> award.getApproval()))
                .collect(toList());

        List<Approval> certificationApprovals = certificationRepository.findByUserEmail(user.getEmail()).stream()
                .map((certification -> certification.getApproval()))
                .collect(toList());

        awardApprovals.addAll(certificationApprovals);
        Approval approval = awardApprovals.stream()
                .sorted(comparing(Approval::getCreatedAt).reversed())
                .findFirst()
                .orElse(null);

        return approval != null && approval.isProceeding();
    }

    @Transactional
    public EditUserProfileResponse editUserProfile(String loginEmail, String nickname, EditUserProfileRequest request, MultipartFile multipartFile) {
        User user = userHelper.findUserByNickname(nickname);

        if (!loginEmail.equals(user.getEmail())) {
            throw new WrongApproach("본인의 프로필만 수정할 수 있습니다.");
        }

        if (userHelper.isNicknameAlreadyExist(user.getNickname(), request.getNickname())) {
            throw new AlreadyExistNickname();
        }

        String originImgUrl = user.getImageUrl();
        String newImgUrl = s3UserService.saveProfileImage(multipartFile, originImgUrl);

        //todo 헬스장 조회 수정, 수정 시 헬스장 식별자를 추가로 받아와야한다.
        HealthClub healthClub = findOrUpdateHealthClub(request.getGymName());

        user.editProfile(
                newImgUrl,
                request.getNickname(),
                request.getIntroduction(),
                healthClub,
                request.getPreferredSplit()
        );

        userRepository.save(user);

        return EditUserProfileResponse.of(user);
    }


    private HealthClub findOrUpdateHealthClub(String name) {
        HealthClub healthClub = healthClubRepository.findByName(name)
                .stream().findAny()
                .orElse(
                        HealthClub.builder()
                                .name(name)
                                .build()
                );

        return healthClubRepository.save(healthClub);
    }

    /**
     * 트레이너 심사 요청 기능
     */
    @Transactional
    public void applyForApproval(String loginEmail, ApplyForApprovalRequest request, List<MultipartFile> awardImageFiles, List<MultipartFile> certificationImageFiles) {
        User user = userHelper.findUserByEmail(loginEmail);

        if (hasProceedingApproval(user)) {
            throw new AlreadyProceedingApproval();
        }
        validateOmission(request, awardImageFiles, certificationImageFiles);

        // s3에 이미지들 저장
        List<String> awardImageUrls = s3UserService.saveCareerImages(request.getAwards(), awardImageFiles);
        List<String> certificationImageUrls = s3UserService.saveCareerImages(request.getCertifications(), certificationImageFiles);

        // db에 저장
        Approval approval = Approval.builder()
                .approveStatus(WAITING)
                .awards(new ArrayList<>())
                .certifications(new ArrayList<>())
                .build();

        approval.addAwards(request.toAwards(user), awardImageUrls);
        approval.addCertifications(request.toCertifications(user), certificationImageUrls);

        approvalRepository.save(approval);
    }

    private void validateOmission(ApplyForApprovalRequest request, List<MultipartFile> awardImages, List<MultipartFile> certificationImages) {
        validateAllOmitted(request);
        validateOnlyHasImages(request, awardImages, certificationImages);
    }

    private void validateAllOmitted(ApplyForApprovalRequest request) {
        if (isEmptyCollection(request.getAwards()) && isEmptyCollection(request.getCertifications())) {
            throw new EmptyTrainerApplyException();
        }
    }

    private void validateOnlyHasImages(ApplyForApprovalRequest request, List<MultipartFile> awardImages, List<MultipartFile> certificationImages) {
        if (isEmptyCollection(request.getAwards()) && !isFilesListNull(awardImages)) {
            throw new EmptyTrainerApplyException();
        }

        if (isEmptyCollection(request.getCertifications()) && !isFilesListNull(certificationImages)) {
            throw new EmptyTrainerApplyException();
        }
    }
    
    private boolean isEmptyCollection(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    // List<MultipartFile>은 빈 파일보내면 길이 1의 리스트를 반환
    private boolean isFilesListNull(List<MultipartFile> imgFiles) {
        return imgFiles == null || imgFiles.get(0).isEmpty();
    }

    @Transactional
    public void registerInbody(String loginEmail, RegisterInbodyRequest request) {
        User user = userHelper.findUserByEmail(loginEmail);
        Routine routine = routineRepository.findById(request.getRoutineId())
                .orElse(null);

        Inbody inbody = Inbody.builder()
                .user(user)
                .measureAt(request.getMeasureAt())
                .score(request.getInbodyScore())
                .skeletalMuscleMass(request.getSkeletalMuscleMass())
                .bodyFatRatio(request.getBodyFatRatio())
                .weight(request.getWeight())
                .basalMetabolicRate(request.getBasalMetabolicRate())
                .build();

        if (routine != null) inbody.addRoutineId(routine);
        inbodyRepository.save(inbody);
    }

    @Transactional(readOnly = true)
    public GetInbodiesResponse getInbodies(String nickname) {
        User user = userHelper.findUserByNickname(nickname);

        return new GetInbodiesResponse(
                inbodyRepository.findByUserEmail(user.getEmail())
        );
    }

    public ResponseCookie getExpiredResponseCookie() {
        return ResponseCookie.from("refreshToken", null)
                .maxAge(0)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .build();
    }

    @Transactional
    public EditInitialNicknameResponse editInitialNickname(String loginEmail, EditNicknameRequest request) {
        User findUser = userHelper.findUserByEmail(loginEmail);
        String newNickname = request.getNickname();

        if (userHelper.isNicknameAlreadyExist(findUser.getNickname(), newNickname)) {
            throw new AlreadyExistNickname();
        }

        // 해당 메시지를 사용해야하는데 기존 에러코드를 수정할 수는 없어서 임시 사용
        if (userHelper.isUserNotFoundByEmail(loginEmail)) {
            throw new NotFoundUser("존재하지 않는 회원입니다.");
        }

        String changedNickname = findUser.changeNickname(newNickname);
        return EditInitialNicknameResponse.of(changedNickname);
    }

    @Transactional(readOnly = true)
    public String checkNicknameDuplication(String loginNickname, String newNickname) {
        if (userHelper.isNicknameAlreadyExist(loginNickname, newNickname)) {
            return "중복";
        }
        return "사용가능";
    }

    @Transactional(readOnly = true)
    public void updateUserDeleted(String loginEmail) {
        if (userHelper.isUserNotFoundByEmail(loginEmail)) {
            throw new NotFoundUser("존재하지 않는 회원입니다.");
        }

        User findUser = userHelper.findUserByEmail(loginEmail);
        findUser.withdraw();
    }



    @Transactional(readOnly = true)
    public GetSearchUsersResponse getSearchedUsers(String nickname, Pageable pageable) {
        Page<User> searchedUsers = userRepository.findByNicknameStartingWith(nickname, pageable);

        return GetSearchUsersResponse.from(
                searchedUsers.stream()
                        .map(this::toResponse)
                        .toList(),
                searchedUsers.hasNext()
        );
    }

    private GetSearchUsersResponse.GetSearchUserResponse toResponse(User user) {
        return GetSearchUsersResponse.GetSearchUserResponse.builder()
                .userProfileImageUrl(user.getImageUrl())
                .nickname(user.getNickname())
                .introduction(user.getIntroduction())
                .inbodyScore(getAverageOfInbodyScores(user.getEmail()))
                .build();
    }

    // todo : fetch로 변경 고려
    private int getAverageOfInbodyScores(String email) {
        List<Inbody> inbodies = inbodyRepository.findByUserEmail(email);
        if (inbodies.isEmpty()) return 0;

        double sum = inbodies.stream()
                .mapToDouble(inbody -> inbody.getScore())
                .reduce(Double::sum)
                .orElse(0.0);

        return (int) (Math.round(sum / inbodies.size()));
    }
}

package com.planmate.community.domain.participant.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.participant.dto.MateParticipationResponse;
import com.planmate.community.domain.participant.entity.MateParticipant;
import com.planmate.community.domain.participant.repository.MateParticipantRepository;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.enums.MateStatus;
import com.planmate.community.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MateService {

    private final MateParticipantRepository mateParticipantRepository;
    private final PostRepository postRepository;

    /**
     * 메이트 모집 참여. 정원이 차면 자동으로 모집 마감된다.
     */
    @Transactional
    public MateParticipationResponse join(UUID userId, Long postId) {
        // 게시글 행을 잠그고 검사 → 저장을 직렬화해 동시 참여로 정원이 초과되는 경합을 방지
        Post post = findMatePostForUpdate(postId);

        if (post.getStatus() == MateStatus.CLOSED) {
            throw new CommunityException(ErrorCode.MATE_CLOSED);
        }
        if (mateParticipantRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            throw new CommunityException(ErrorCode.MATE_ALREADY_JOINED);
        }

        long current = mateParticipantRepository.countByPostId(postId);
        if (post.getMaxParticipants() != null && current >= post.getMaxParticipants()) {
            throw new CommunityException(ErrorCode.MATE_FULL);
        }

        mateParticipantRepository.save(MateParticipant.builder()
                .postId(postId)
                .userId(userId)
                .build());

        long updated = current + 1;
        if (post.getMaxParticipants() != null && updated >= post.getMaxParticipants()) {
            post.changeStatus(MateStatus.CLOSED);
        }

        return buildResponse(post, updated);
    }

    @Transactional
    public MateParticipationResponse leave(UUID userId, Long postId) {
        Post post = findMatePost(postId);

        MateParticipant participant = mateParticipantRepository.findByPostIdAndUserId(postId, userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.MATE_NOT_JOINED));

        // 삭제 전 인원을 세고 -1 (delete 후 count 는 auto-flush 여부에 따라 값이 흔들릴 수 있음)
        long remaining = mateParticipantRepository.countByPostId(postId) - 1;
        mateParticipantRepository.delete(participant);

        return buildResponse(post, remaining);
    }

    /**
     * 모집 상태 변경 (작성자 전용) — 마감/재모집.
     */
    @Transactional
    public MateParticipationResponse changeStatus(UUID userId, Long postId, String statusValue) {
        Post post = findMatePost(postId);
        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.POST_ACCESS_DENIED);
        }

        MateStatus status;
        try {
            status = MateStatus.valueOf(statusValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "존재하지 않는 모집 상태입니다: " + statusValue);
        }
        post.changeStatus(status);

        return buildResponse(post, mateParticipantRepository.countByPostId(postId));
    }

    private Post findMatePost(Long postId) {
        return requireMate(postRepository.findById(postId));
    }

    // 참여 처리용 — 게시글 행에 쓰기 락을 걸어 로드
    private Post findMatePostForUpdate(Long postId) {
        return requireMate(postRepository.findByIdForUpdate(postId));
    }

    private Post requireMate(Optional<Post> found) {
        Post post = found.orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
        if (post.getCategory() != Category.MATE) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "메이트 게시판 게시글이 아닙니다.");
        }
        return post;
    }

    private MateParticipationResponse buildResponse(Post post, long participants) {
        return new MateParticipationResponse(
                (int) Math.max(participants, 0),
                post.getMaxParticipants(),
                post.getStatus() != null ? post.getStatus().toLowerValue() : null
        );
    }
}

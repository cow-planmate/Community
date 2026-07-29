package com.planmate.community.common.access;

import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 프로필에 묶인 목록(작성글·댓글 등)의 열람 권한 판정.
 *
 * 프로필과 그 사람의 활동 목록은 사용자 입장에서 한 화면이므로 같은 공개 범위를 따른다.
 * 판정을 이 한 곳에 모아 두어야 목록이 늘어날 때 규칙이 갈라지지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ProfileAccessValidator {

    private final UserClient userClient;

    /**
     * @param ownerId  목록의 주인
     * @param viewerId 보는 사람 (비로그인 null)
     */
    public void validateVisible(UUID ownerId, UUID viewerId) {
        if (ownerId.equals(viewerId)) {
            return;
        }
        if (!userClient.isProfilePublic(ownerId)) {
            throw new CommunityException(ErrorCode.PROFILE_PRIVATE);
        }
    }
}

package com.planmate.community.common.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 게시글·댓글 작성자 표시에 필요한 정보 (메인 백엔드가 원본).
 *
 * profileImageUrl과 avatarHash는 없을 수 있으며(사진 미등록, 이메일 없는 소셜 계정),
 * 그때는 클라이언트가 닉네임 이니셜로 아이콘을 그린다.
 *
 * deleted는 탈퇴한 계정을 뜻한다. "조회 실패"(메인 백엔드 장애)와는 다르다 —
 * 조회 실패는 애초에 이 객체가 만들어지지 않고 ofSnapshot으로 떨어진다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorProfile(String nickname, String profileImageUrl, String avatarHash, boolean deleted) {

    /** 탈퇴한 사용자에게 보여줄 이름. 게시글에 남은 옛 닉네임 대신 이걸 쓴다. */
    public static final String DELETED_NICKNAME = "탈퇴한 사용자";

    /**
     * 최신 정보를 못 받아왔을 때(메인 백엔드 장애 등) 게시글·댓글에 저장된 닉네임 스냅샷으로 만든다.
     * 아이콘은 포기하고 이니셜로 떨어뜨린다 — 옛 아이콘을 스냅샷해두는 것보다 낫다.
     */
    public static AuthorProfile ofSnapshot(String storedNickname) {
        return new AuthorProfile(storedNickname, null, null, false);
    }

    /** 탈퇴 계정 — 닉네임·아이콘을 모두 버리고 "탈퇴한 사용자"로 고정한다. */
    public static AuthorProfile ofDeleted() {
        return new AuthorProfile(DELETED_NICKNAME, null, null, true);
    }

    public static AuthorProfile resolve(AuthorProfile fresh, String storedNickname) {
        return fresh != null ? fresh : ofSnapshot(storedNickname);
    }
}

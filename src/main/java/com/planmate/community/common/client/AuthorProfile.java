package com.planmate.community.common.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 게시글·댓글 작성자 표시에 필요한 정보 (메인 백엔드가 원본).
 *
 * profileImageUrl과 avatarHash는 없을 수 있으며(사진 미등록, 이메일 없는 소셜 계정),
 * 그때는 클라이언트가 닉네임 이니셜로 아이콘을 그린다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorProfile(String nickname, String profileImageUrl, String avatarHash) {

    /**
     * 최신 정보를 못 받아왔을 때(메인 백엔드 장애 등) 게시글·댓글에 저장된 닉네임 스냅샷으로 만든다.
     * 아이콘은 포기하고 이니셜로 떨어뜨린다 — 옛 아이콘을 스냅샷해두는 것보다 낫다.
     */
    public static AuthorProfile ofSnapshot(String storedNickname) {
        return new AuthorProfile(storedNickname, null, null);
    }

    public static AuthorProfile resolve(AuthorProfile fresh, String storedNickname) {
        return fresh != null ? fresh : ofSnapshot(storedNickname);
    }
}

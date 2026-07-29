package com.planmate.community.common.access;

import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAccessValidatorTest {

    @Mock
    private UserClient userClient;

    private final UUID owner = UUID.randomUUID();

    @Test
    @DisplayName("공개 프로필이면 통과한다")
    void allowsWhenProfilePublic() {
        when(userClient.isProfilePublic(owner)).thenReturn(true);

        assertThatCode(() -> validator().validateVisible(owner, UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비공개 프로필이면 PROFILE_PRIVATE 예외가 발생한다")
    void rejectsWhenProfilePrivate() {
        when(userClient.isProfilePublic(owner)).thenReturn(false);

        assertThatThrownBy(() -> validator().validateVisible(owner, UUID.randomUUID()))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.PROFILE_PRIVATE));
    }

    @Test
    @DisplayName("본인은 비공개여도 통과하며 공개 여부를 조회하지 않는다")
    void allowsOwnerWithoutLookup() {
        assertThatCode(() -> validator().validateVisible(owner, owner))
                .doesNotThrowAnyException();

        verify(userClient, never()).isProfilePublic(any());
    }

    @Test
    @DisplayName("비로그인(viewer null)은 공개 프로필만 볼 수 있다")
    void anonymousViewerFollowsPublicFlag() {
        when(userClient.isProfilePublic(owner)).thenReturn(false);

        assertThatThrownBy(() -> validator().validateVisible(owner, null))
                .isInstanceOf(CommunityException.class);
    }

    private ProfileAccessValidator validator() {
        return new ProfileAccessValidator(userClient);
    }
}

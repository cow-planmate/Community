package com.planmate.community.domain.post.repository;

import com.planmate.community.common.user.ReplicatedUserRepository;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.support.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작성자 닉네임 검색의 SQL 계약 검증.
 *
 * 여기서 가장 중요한 건 <b>totalElements</b> 다. 사용자 정보를 원격에서 가져오던 시절에는
 * 작성자로 거르려면 페이지를 가져온 뒤 애플리케이션에서 버리는 수밖에 없었고, 그러면 총계와
 * 페이지 수가 실제와 어긋난다. 이 마이그레이션의 목적 자체가 그 필터를 SQL 안으로 옮기는 것이라,
 * Spring Data 가 EXISTS 서브쿼리에서 파생한 count 쿼리가 정확한지 확인하지 않으면 의미가 없다.
 * 그리고 count 가 틀려도 목록 자체는 그럴듯하게 나오므로 눈으로는 발견되지 않는다.
 */
class PostAuthorSearchTest extends PostgresTestBase {

    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "createdAt");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ReplicatedUserRepository userRepository;

    private UUID givenUser(String nickname) {
        UUID userId = UUID.randomUUID();
        userRepository.upsert(userId, nickname, null, null, true, false, 1L);
        return userId;
    }

    private UUID givenDeletedUser() {
        UUID userId = UUID.randomUUID();
        userRepository.upsert(userId, null, null, null, false, true, 1L);
        return userId;
    }

    private void givenPost(UUID userId, String authorNickname, String title) {
        postRepository.save(Post.builder()
                .category(Category.FREE)
                .userId(userId)
                .authorNickname(authorNickname)
                .title(title)
                .content("{}")
                .contentText("본문")
                .build());
    }

    @Test
    @DisplayName("작성자 닉네임으로 게시글이 검색된다")
    void findsPostByAuthorNickname() {
        UUID userId = givenUser("김철수");
        givenPost(userId, "김철수", "제목과 본문에는 검색어가 없다");

        Page<Post> page = postRepository.searchByCategoryIncludingAuthor(
                Category.FREE, "철수", PageRequest.of(0, 10, NEWEST));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("제목과 본문에는 검색어가 없다");
    }

    @Test
    @DisplayName("제목/본문 검색은 그대로 동작한다 — 작성자 조건이 기존 검색을 좁히면 안 된다")
    void stillFindsByTitle() {
        UUID userId = givenUser("무관한닉네임");
        givenPost(userId, "무관한닉네임", "여행 후기입니다");

        Page<Post> page = postRepository.searchByCategoryIncludingAuthor(
                Category.FREE, "여행", PageRequest.of(0, 10, NEWEST));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("totalElements와 totalPages가 실제 결과 수와 일치한다")
    void countQueryMatchesActualResults() {
        UUID target = givenUser("박영희");
        UUID other = givenUser("전혀다른사람");
        for (int i = 0; i < 5; i++) {
            givenPost(target, "박영희", "글 " + i);
        }
        givenPost(other, "전혀다른사람", "안 걸려야 하는 글");

        // 페이지 크기를 결과보다 작게 잡아야 count 쿼리가 실제로 따로 실행된다
        Page<Post> page = postRepository.searchByCategoryIncludingAuthor(
                Category.FREE, "영희", PageRequest.of(0, 2, NEWEST));

        assertThat(page.getContent()).hasSize(2);
        // 파생 count 쿼리가 EXISTS 조건을 빠뜨리면 여기서 6이 나온다
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("작성자가 여러 글을 써도 행이 중복되지 않는다")
    void doesNotDuplicateRows() {
        UUID userId = givenUser("다작작가");
        givenPost(userId, "다작작가", "첫 글");
        givenPost(userId, "다작작가", "두 번째 글");

        Page<Post> page = postRepository.searchByCategoryIncludingAuthor(
                Category.FREE, "다작", PageRequest.of(0, 10, NEWEST));

        // 조인이었다면 작성자당 행이 부풀 수 있다. EXISTS 를 쓰는 이유 중 하나다.
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("탈퇴한 작성자는 이름으로 찾을 수 없다")
    void deletedAuthorIsNotSearchable() {
        UUID userId = givenDeletedUser();
        // 게시글에는 작성 시점 닉네임 스냅샷이 남아 있지만, 그걸로 찾히면 안 된다
        givenPost(userId, "탈퇴하기전닉", "탈퇴자가 쓴 글");

        Page<Post> page = postRepository.searchByCategoryIncludingAuthor(
                Category.FREE, "탈퇴하기전닉", PageRequest.of(0, 10, NEWEST));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("복제본에 없는 작성자의 글도 제목으로는 검색된다 — 아직 복제 안 된 사용자가 사라지면 안 된다")
    void postFromUnreplicatedAuthorStillFoundByTitle() {
        // 복제본에 행이 없는 사용자(가입 직후 등)
        givenPost(UUID.randomUUID(), "복제안된사람", "제주도 여행기");

        Page<Post> page = postRepository.searchByCategoryIncludingAuthor(
                Category.FREE, "제주도", PageRequest.of(0, 10, NEWEST));

        // EXISTS 를 OR 가 아니라 AND 로 잘못 붙이면 이 글이 통째로 사라진다
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}

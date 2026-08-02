package com.planmate.community.support;

import com.planmate.community.config.JpaConfig;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 실제 PostgreSQL 에 Flyway 마이그레이션을 그대로 적용한 뒤 리포지토리를 검증하는 베이스.
 *
 * <p>이 모듈에서 SQL 을 실행하는 유일한 테스트 경로다. 사용자 읽기 모델은 정합성을 네이티브
 * SQL 에 걸고 있어서 — 업서트의 source_seq 가드, EXISTS 서브쿼리에서 파생되는 count 쿼리,
 * PG 의 null 파라미터 타입 추론 — 목으로는 어느 것도 검증되지 않는다. 가드가 뒤집혀도
 * 목 테스트는 전부 통과한다.
 *
 * <p>컨테이너는 JVM 하나당 한 번만 띄우고 명시적으로 끄지 않는다(테스트 종료 시 Ryuk 이 정리).
 * @Testcontainers/@Container 로 수명을 맡기면 <b>테스트 클래스마다</b> start/stop 이 걸리는데,
 * static 필드는 공유되므로 첫 클래스가 끝나면서 컨테이너가 내려가고 다음 클래스는 죽은 컨테이너를
 * 붙잡는다 — 증상은 "Connection is not available (total=0)" 이라 원인을 짐작하기 어렵다.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} 가 필요한 이유: @DataJpaTest 는
 * 기본으로 데이터소스를 임베디드 DB 로 바꿔치기하는데, 그러면 컨테이너를 띄워놓고 H2 를
 * 검증하게 된다(그리고 ON CONFLICT 에서 깨진다).
 */
@DataJpaTest
// @DataJpaTest 슬라이스는 @EnableJpaAuditing 을 끌어오지 않는다. 없으면 created_at/updated_at 이
// null 로 들어가 NOT NULL 제약에 걸린다 — 운영 코드 문제가 아니라 슬라이스 구성 문제다.
@Import(JpaConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class PostgresTestBase {

    // V1__init.sql 이 pg_trgm 확장을 만들므로 확장이 포함된 표준 이미지면 충분하다
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    static {
        POSTGRES.start();
    }
}

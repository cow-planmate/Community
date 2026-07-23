# PlanMate Community Service

PlanMate 커뮤니티(자유게시판·Q&A·메이트 찾기·장소 추천)를 담당하는 **독립 마이크로서비스**.
코드 규약은 [Backend-v2](https://github.com/cow-planmate/Backend-v2)를 미러한다 (ErrorCode/ErrorResponse, BaseTimeEntity/BaseSoftDeleteEntity, config/·security/ 레이아웃, jjwt 0.12.6).

## 아키텍처

```
[Frontend] ──/api/community/*──▶ nginx Ingress ──▶ community-service (:8080)
     │                              │
     └────/api/* ───────────────────┴───────────▶ planmate-backend (레거시)
                                                     ▲ GET /api/internal/users?ids=  (X-Internal-Token)
공유: Postgres 클러스터(별도 DB 'community') · Redis(키 prefix community:) · MinIO(버킷 community) · JWT access secret
```

- **인증**: 메인 백엔드가 발급한 JWT를 시크릿 공유로 **무상태 검증**. 키 파생은 `jwt.secret-encoding`으로 전환
  (`base64` = 레거시 페어링(현재), `raw` = Backend-v2 페어링). 리프레시는 메인 백엔드 소관.
- **작성자 정보**: 글/댓글에 닉네임 스냅샷 저장 + 조회 시 내부 API(Redis 캐시 10분)로 최신 닉네임 병합.
  메인 백엔드 장애 시에도 스냅샷으로 읽기 경로 동작.
- **레벨**: 커뮤니티 활동 기반 (`글×3 + 댓글` 점수 → 구간 [10,30,70,150) = Lv1~5), `community_user_stats` 소유.
- **조회수**: Redis SETNX 24h 중복 방지(로그인 userId / 비로그인 IP), Redis 장애 시 fail-open.
- **카운터**(좋아요/싫어요/댓글/조회): 원자적 UPDATE로 동시성 보장.

## API 요약 (`/api/community`)

| 기능 | 엔드포인트 |
|---|---|
| 목록/검색 | `GET /posts?category=free\|qna\|mate\|recommend&page=&size=&sort=latest\|likes\|views&q=` |
| 핫글(상위3) | `GET /posts/hot?category=` |
| 상세/작성/수정/삭제 | `GET·POST /posts`, `PATCH·DELETE /posts/{id}` |
| 반응 | `PUT /posts/{id}/reaction {type: like\|dislike}` (토글/전환), `DELETE /posts/{id}/reaction` |
| 댓글 | `GET·POST /posts/{id}/comments`, `PATCH·DELETE /comments/{id}` |
| 메이트 | `POST·DELETE /posts/{id}/participants` (정원 차면 자동 마감), `PATCH /posts/{id}/status` |
| QnA | `PATCH /posts/{id}/answered` (작성자) |
| 내 활동 | `GET /me/posts`, `/me/liked`, `/me/comments`, `/me/stats` |
| 이미지 | `POST /images` (multipart → MinIO 공개 URL, BlockNote uploadFile용) |

GET 목록/상세는 비로그인 허용, 나머지는 `Authorization: Bearer <accessToken>`. Swagger: `/swagger-ui.html`.

## 로컬 실행

```bash
# DB는 공용 postgres 인스턴스(호스트 5432)의 community DB를 사용한다.
# 이 저장소의 docker-compose.yml은 5433에 별도 인스턴스를 띄우던 이전 구성으로,
# 더 이상 쓰지 않는다.
# redis는 로컬 6379 재사용 (없어도 기동됨 — dedupe/캐시만 비활성)

JWT_SECRET=<레거시 jwt.access-secret 값> JWT_SECRET_ENCODING=base64 \
MAIN_BACKEND_URL=http://localhost:8080 \
MINIO_ACCESS_KEY=... MINIO_SECRET_KEY=... \
./gradlew bootRun             # :8081
```

프론트: `VITE_COMMUNITY_API_URL=http://localhost:8081 npm run dev`
(prod는 ingress 경로 라우팅으로 단일 도메인 — 변수 불필요)

테스트: `./gradlew test` (서비스 단위 테스트 25+건, DB/Redis 불필요)

## 배포 (k3s)

1. **DB 프로비저닝** (기존 클러스터에 1회):
   ```sql
   CREATE DATABASE community;
   CREATE USER community_user WITH PASSWORD '<비밀번호>';
   GRANT ALL PRIVILEGES ON DATABASE community TO community_user;
   -- community DB에 접속해서:
   GRANT ALL ON SCHEMA public TO community_user;
   ```
2. **시크릿**:
   ```bash
   kubectl create secret generic community-db-secret \
     --from-literal=username=community_user --from-literal=password='<비밀번호>'
   # planmate-secret에 내부 API 토큰 키 추가 (레거시 백엔드와 공유)
   kubectl patch secret planmate-secret -p '{"stringData":{"INTERNAL_API_TOKEN":"<랜덤 토큰>"}}'
   ```
   레거시 백엔드 Deployment에도 `INTERNAL_API_TOKEN` env(secretKeyRef planmate-secret)를 추가해야 함.
3. **이미지**: `./gradlew build && docker build -t cycle123/planmate-community:latest . && docker push ...`
4. **적용**: `kubectl apply -f k8s/` + `Backend/k8s/planmate-ingress.yaml` (경로 `/api/community` 추가본) 재적용.
   `k8s/deployment.yaml`의 `SPRING_DATA_REDIS_HOST`는 클러스터 redis 마스터 Service 이름으로 맞출 것.

## Backend-v2 전환 시 (커뮤니티 코드 변경 0줄)

1. `JWT_SECRET` → v2의 `jwt.secret` 값, `JWT_SECRET_ENCODING=raw`
2. `MAIN_BACKEND_URL` → v2 Service
3. 내부 사용자 API(`InternalUserController` + `InternalTokenFilter`, ~50줄)를 v2로 포팅

## 미구현/추후 과제

- CI 파이프라인 (레거시 `.github/workflows` 패턴 이식)
- `me/comments` 응답에 원글 제목 미포함 (마이페이지 댓글 탭에 postTitle 빈 값)
- 검색은 Postgres pg_trgm — 트래픽 증가 시 Elasticsearch 전환 여지
- 알림(댓글/참여 발생 시 FCM·SSE) — 메인 백엔드 연계 필요

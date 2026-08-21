package com.planmate.community.common.notification;

import build.buf.gen.planmate.notification.v1.ActionRef;
import build.buf.gen.planmate.notification.v1.ActorSnapshot;
import build.buf.gen.planmate.notification.v1.NotificationRequested;
import build.buf.gen.planmate.notification.v1.NotificationType;
import build.buf.gen.planmate.notification.v1.ResourceRef;
import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 커뮤니티 도메인 사건을 알림 요청 이벤트로 바꾼다. 문구는 만들지 않는다 — 제목/본문 렌더링은
 * 알림 센터가 소유하고, 여기서는 "누가 누구의 어떤 자원에 무슨 일을 했다"만 싣는다.
 */
@Component
public class CommunityNotificationFactory {

    public NotificationRequested create(UUID recipientId, UUID actorId, String actorName,
                                        NotificationType type, String resourceType, String resourceId,
                                        String resourceName, String route, Map<String, String> parameters) {
        return create(UUID.randomUUID(), recipientId, actorId, actorName, type, resourceType,
                resourceId, resourceName, route, parameters, Map.of());
    }

    /**
     * 뱃지는 eventId 를 (사용자, 뱃지코드)로 결정론적으로 만든다. 뱃지 판정이 여러 경로에서
     * 중복 실행돼도 소비자가 같은 eventId 로 지워, "최초 달성 한 번"이라는 정책이 유지된다.
     */
    public NotificationRequested badge(UUID userId, String badgeCode, String badgeName) {
        UUID deterministicId = UUID.nameUUIDFromBytes(
                ("community:badge:" + userId + ":" + badgeCode).getBytes(StandardCharsets.UTF_8));
        return create(deterministicId, userId, null, null,
                NotificationType.NOTIFICATION_TYPE_COMMUNITY_BADGE_EARNED,
                "BADGE", badgeCode, badgeName, "COMMUNITY_BADGES",
                Map.of("badgeCode", badgeCode),
                Map.of("badgeName", badgeName == null ? "" : badgeName));
    }

    private NotificationRequested create(UUID eventId, UUID recipientId, UUID actorId, String actorName,
                                         NotificationType type, String resourceType, String resourceId,
                                         String resourceName, String route, Map<String, String> parameters,
                                         Map<String, String> templateVariables) {
        Instant now = Instant.now();
        NotificationRequested.Builder builder = NotificationRequested.newBuilder()
                .setEventId(eventId.toString())
                .setProducer("community")
                .setOccurredAt(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .setRecipientUserId(recipientId.toString())
                .setType(type)
                .setResource(ResourceRef.newBuilder()
                        .setType(resourceType).setId(resourceId)
                        .setDisplayName(resourceName == null ? "" : resourceName))
                .setAction(ActionRef.newBuilder().setRoute(route).putAllParameters(parameters))
                .putAllTemplateVariables(templateVariables);
        if (actorId != null) {
            builder.setActor(ActorSnapshot.newBuilder()
                    .setUserId(actorId.toString())
                    .setDisplayName(actorName == null ? "누군가" : actorName));
        }
        return builder.build();
    }
}

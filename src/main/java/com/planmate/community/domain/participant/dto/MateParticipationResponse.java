package com.planmate.community.domain.participant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MateParticipationResponse(
        int participants,
        Integer maxParticipants,
        String status
) {
}

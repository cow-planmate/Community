package com.planmate.community.domain.reaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReactionResponse(
        int likes,
        int dislikes,
        String myReaction
) {
}

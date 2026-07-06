package com.planmate.community.domain.post.dto;

import com.planmate.community.domain.post.repository.PostRepository;

public record RegionCountResponse(
        String region,
        long count
) {

    public static RegionCountResponse of(PostRepository.RegionCount regionCount) {
        return new RegionCountResponse(regionCount.getRegion(), regionCount.getPostCount());
    }
}

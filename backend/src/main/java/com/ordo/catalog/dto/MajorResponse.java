package com.ordo.catalog.dto;

import com.ordo.catalog.domain.Major;

public record MajorResponse(Long id, String displayName, Long collegeId, String collegeName) {
    public static MajorResponse from(Major major) {
        return new MajorResponse(major.getId(), major.getDisplayName(),
                major.getCollege().getId(), major.getCollege().getName());
    }
}

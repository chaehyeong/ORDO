package com.ordo.catalog.dto;

import com.ordo.catalog.domain.College;

public record CollegeResponse(Long id, String name) {
    public static CollegeResponse from(College college) {
        return new CollegeResponse(college.getId(), college.getName());
    }
}

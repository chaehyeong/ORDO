package com.ordo.catalog.dto;

import com.ordo.catalog.domain.Course;
import java.util.List;
import org.springframework.data.domain.Page;

/** Spring 내부 Page 직렬화 형태에 의존하지 않는 교과목 검색 응답. */
public record CoursePageResponse(List<CourseResponse> content, int page, int size,
                                 long totalElements, int totalPages) {
    public static CoursePageResponse from(Page<Course> courses) {
        return new CoursePageResponse(courses.getContent().stream().map(CourseResponse::from).toList(),
                courses.getNumber(), courses.getSize(), courses.getTotalElements(), courses.getTotalPages());
    }
}

package com.ordo.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 마이크로디그리 (기준정보, 읽기 전용, V18 시드) */
@Entity
@Table(name = "micro_degrees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MicroDegree {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String scope;  // 국제 / 공통
    private String collegeName;
    private String hostDepartment;
    private int requiredCourseCount;
    private int requiredCredits;
    private int minDepartments;  // 2 면 참여학과 2개 이상 조건 적용
    private boolean selective;  // 선발제
    private String requirementText;
    private String guideUrl;
}

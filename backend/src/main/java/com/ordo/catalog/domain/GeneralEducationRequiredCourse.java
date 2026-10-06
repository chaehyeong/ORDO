package com.ordo.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학사 기준정보. V1 스키마에 대응하며 조회에만 사용한다. */
@Entity
@Table(name = "general_education_required_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneralEducationRequiredCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int admissionYear;
    @Column(length = 50)
    private String groupName;
    @Column(length = 100, nullable = false)
    private String courseName;
    @Column(nullable = false)
    private int credits;
    @Column(length = 10)
    private String recommendedGrade;
    @Column(length = 200)
    private String note;
}

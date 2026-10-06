package com.ordo.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String collegeName;
    @Column(length = 100, nullable = false)
    private String unitName;
    @Column(length = 20)
    private String courseCode;
    @Column(length = 100, nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private Classification classification;
    @Column(nullable = false)
    private int credits;
    @Column(nullable = false)
    private boolean variableCredits;
    @Column(length = 10)
    private String targetGrade;
    @Column(length = 10)
    private String openSemester;
    @Column(length = 200)
    private String track;
}

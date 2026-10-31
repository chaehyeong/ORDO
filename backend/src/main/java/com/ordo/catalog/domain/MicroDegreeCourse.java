package com.ordo.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 마이크로디그리 편성 과목 (기준정보, 읽기 전용, V18 시드) */
@Entity
@Table(name = "micro_degree_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MicroDegreeCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "micro_degree_id")
    private MicroDegree microDegree;

    private String courseCode;  // null 이면 과목명으로 찾는다
    private String aliasCode;  // 구 학수번호·사이트 오기
    private String courseName;
    private String departmentName;
    private Integer credits;  // null = 자료에서 확인 못함
    private boolean mandatory;
    private String groupKey;
    private Integer groupMin;
    private Integer groupMax;  // null = 제한 없음
}

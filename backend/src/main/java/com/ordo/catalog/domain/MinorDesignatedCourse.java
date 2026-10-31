package com.ordo.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 부전공 지정 과목(V22·V23). 기준정보라 조회에만 사용한다. 명세 5장 14번 */
@Entity
@Table(name = "minor_designated_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinorDesignatedCourse {

    public enum Role {
        REQUIRED, ELECTIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    @Column(length = 20, nullable = false)
    private String courseCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Role role;
}

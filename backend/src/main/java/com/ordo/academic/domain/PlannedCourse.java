package com.ordo.academic.domain;

import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.BaseTimeEntity;
import com.ordo.global.common.Term;
import com.ordo.user.domain.User;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 졸업 로드맵에서 학기에 배치한 과목 1건 (T18). 계획이라 이수현황·평점에는 들어가지 않는다 */
@Entity
@Table(name = "planned_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannedCourse extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private int academicYear;

    @Enumerated(EnumType.STRING)
    private Term term;

    private String courseName;
    private String courseCode;

    @Enumerated(EnumType.STRING)
    private Classification classification;

    private int credits;

    @Builder
    private PlannedCourse(User user, int academicYear, Term term, String courseName, String courseCode,
                          Classification classification, int credits) {
        this.user = user;
        this.academicYear = academicYear;
        this.term = term;
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.classification = classification;
        this.credits = credits;
    }
}

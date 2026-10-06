package com.ordo.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "graduation_requirements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GraduationRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;
    @Column(nullable = false)
    private int admissionYear;
    private Integer totalCredits;
    private Integer basicCredits;
    private Integer requiredCredits;
    private Integer electiveCredits;
    private Integer majorTotalCredits;
    private Integer otherMajorCredits;
    private Integer doubleBasicCredits;
    private Integer doubleRequiredCredits;
    private Integer doubleElectiveCredits;
    private Integer doubleTotalCredits;
    private Integer doubleOtherMajorCredits;
    private Integer minorRequiredCredits;
    private Integer minorElectiveCredits;
    private Integer minorTotalCredits;
    @Column(length = 50)
    private String swRequirement;
    @Column(length = 50)
    private String englishLectureRequirement;
    @Column(length = 50)
    private String thesisRequirement;
    @Column(length = 100)
    private String topikRequirement;
    @Column(length = 50)
    private String competencyCertification;
}

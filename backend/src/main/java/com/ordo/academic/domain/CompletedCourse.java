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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 내가 들은 과목 1건. 이수구분 자동 판별·검증은 서비스에서 끝낸 값을 받는다 */
@Entity
@Table(name = "completed_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompletedCourse extends BaseTimeEntity {

    /** 학년도 → 학기(1학기·여름·2학기·겨울) → 등록 순 */
    public static final Comparator<CompletedCourse> CHRONOLOGICAL = Comparator
            .comparingInt(CompletedCourse::getAcademicYear)
            .thenComparing(CompletedCourse::getTerm)
            .thenComparing(CompletedCourse::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String courseCode;
    private String courseName;
    private int credits;

    @Enumerated(EnumType.STRING)
    private Classification classification;

    private Integer distributionArea;  // GEN_DISTRIBUTION 일 때만 1~5
    private String grade;             // A+ A0 ... F / P / NP, 미정이면 null
    private int academicYear;

    @Enumerated(EnumType.STRING)
    private Term term;

    @Builder
    private CompletedCourse(User user, String courseCode, String courseName, int credits,
                            Classification classification, Integer distributionArea, String grade,
                            int academicYear, Term term) {
        this.user = user;
        change(courseCode, courseName, credits, classification, distributionArea, grade, academicYear, term);
    }

    /** 재수강 판단 키: 같은 학수번호(없으면 공백 뺀 과목명)면 같은 과목 (명세 5장 4번) */
    public String retakeKey() {
        return courseCode != null ? "code:" + courseCode : nameKey();
    }

    private String nameKey() {
        return "name:" + courseName.replaceAll("\\s+", "");
    }

    /**
     * 과목마다 가장 최근 기록 1건 (courses 는 CHRONOLOGICAL 순이어야 한다).
     * 학수번호 없는 기록은 같은 이름의 학수번호 기록과 같은 과목이다: 직접 입력한 "자료구조"와 성적표의 CSE204 를 두 번 세지 않는다
     */
    public static Collection<CompletedCourse> latestOfEachCourse(List<CompletedCourse> courses) {
        Map<String, String> codeKeyByName = new HashMap<>();
        courses.stream().filter(c -> c.courseCode != null)
                .forEach(c -> codeKeyByName.putIfAbsent(c.nameKey(), c.retakeKey()));
        Map<String, CompletedCourse> latest = new LinkedHashMap<>();
        courses.forEach(c -> latest.put(codeKeyByName.getOrDefault(c.retakeKey(), c.retakeKey()), c));
        return latest.values();
    }

    /** PATCH: 서비스가 기존 값과 합치고 이수구분까지 다시 판별한 값으로 통째로 바꾼다 */
    public void change(String courseCode, String courseName, int credits, Classification classification,
                       Integer distributionArea, String grade, int academicYear, Term term) {
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.credits = credits;
        this.classification = classification;
        this.distributionArea = distributionArea;
        this.grade = grade;
        this.academicYear = academicYear;
        this.term = term;
    }
}

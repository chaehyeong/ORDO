package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.GpaSummary;
import com.ordo.academic.dto.GpaSummary.TermGpa;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.AcademicTerm;
import com.ordo.global.common.Term;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 평점 평균 계산 (명세 5장 평점 평균). 졸업요건·프로필 없이 이수내역만 쓴다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicGpaService {

    // 경희대 4.3 만점, 학교 등급표 기준 (명세 5장, 9장 20)
    static final Map<String, BigDecimal> GRADE_POINTS = Map.ofEntries(
            Map.entry("A+", new BigDecimal("4.3")), Map.entry("A0", new BigDecimal("4.0")), Map.entry("A-", new BigDecimal("3.7")),
            Map.entry("B+", new BigDecimal("3.3")), Map.entry("B0", new BigDecimal("3.0")), Map.entry("B-", new BigDecimal("2.7")),
            Map.entry("C+", new BigDecimal("2.3")), Map.entry("C0", new BigDecimal("2.0")), Map.entry("C-", new BigDecimal("1.7")),
            Map.entry("D+", new BigDecimal("1.3")), Map.entry("D0", new BigDecimal("1.0")), Map.entry("D-", new BigDecimal("0.7")),
            Map.entry("F", BigDecimal.ZERO));
    private static final Set<String> NOT_EARNED_GRADES = Set.of("F", "NP");
    private static final Set<Term> REGULAR_TERMS = Set.of(Term.FIRST, Term.SECOND);
    // 성적표 산정기준: 전공 = 전공필수·전공선택·전공기초
    private static final Set<Classification> MAJOR = Set.of(
            Classification.MAJOR_BASIC, Classification.MAJOR_REQUIRED, Classification.MAJOR_ELECTIVE);

    private final CompletedCourseRepository completedCourseRepository;

    public GpaSummary getGpa(Long userId) {
        return calculate(completedCourseRepository.findByUserId(userId));
    }

    /** 순수 계산 (DB 조회 없음) */
    static GpaSummary calculate(List<CompletedCourse> courses) {
        List<CompletedCourse> sorted = courses.stream().sorted(CompletedCourse.CHRONOLOGICAL).toList();

        // 재수강: 같은 과목은 가장 최근 1건만. 이수현황과 달리 F 를 먼저 빼지 않는다 (명세 9장 21)
        Map<String, CompletedCourse> latest = new LinkedHashMap<>();
        sorted.forEach(c -> latest.put(c.retakeKey(), c));
        Collection<CompletedCourse> counted = latest.values();
        List<CompletedCourse> major = counted.stream().filter(c -> MAJOR.contains(c.getClassification())).toList();

        Map<AcademicTerm, List<CompletedCourse>> byTerm = sorted.stream().collect(Collectors.groupingBy(
                c -> new AcademicTerm(c.getAcademicYear(), c.getTerm()), LinkedHashMap::new, Collectors.toList()));
        // 학기 번호: 입학년도로 계산하면 휴학생이 틀리므로 기록이 있는 정규 학기 순서로 매긴다 (명세 5장)
        // ponytail: 편입생도 1-1 부터 세고 기록을 빠뜨리면 앞당겨진다. 편입 정보를 받게 되면 그때 시작 번호를 더한다
        List<TermGpa> terms = new ArrayList<>();
        int semester = 0;
        boolean lastRegularInProgress = false;
        for (Map.Entry<AcademicTerm, List<CompletedCourse>> e : byTerm.entrySet()) {
            String label = null;
            if (REGULAR_TERMS.contains(e.getKey().term())) {
                label = semesterLabel(++semester);
                lastRegularInProgress = e.getValue().stream().anyMatch(c -> c.getGrade() == null);
            }
            terms.add(new TermGpa(e.getKey().year(), e.getKey().term(), label, average(e.getValue()),
                    gradedCredits(e.getValue()), earnedCredits(e.getValue())));
        }
        String current = semesterLabel(lastRegularInProgress ? semester : semester + 1);

        return new GpaSummary(average(counted), average(major), gradedCredits(counted), earnedCredits(counted), current, terms);
    }

    /** 1 → "1-1", 2 → "1-2", 3 → "2-1" */
    private static String semesterLabel(int semester) {
        return (semester + 1) / 2 + "-" + (semester % 2 == 0 ? 2 : 1);
    }

    /** Σ(평점×학점) ÷ Σ학점, 셋째 자리 반올림. P·NP·성적 미정은 분모·분자 모두 제외 */
    private static Double average(Collection<CompletedCourse> courses) {
        BigDecimal points = BigDecimal.ZERO;
        int credits = 0;
        for (CompletedCourse c : courses) {
            if (isGraded(c)) {
                points = points.add(GRADE_POINTS.get(c.getGrade()).multiply(BigDecimal.valueOf(c.getCredits())));
                credits += c.getCredits();
            }
        }
        return credits == 0 ? null
                : points.divide(BigDecimal.valueOf(credits), 3, RoundingMode.HALF_UP).doubleValue();
    }

    private static int gradedCredits(Collection<CompletedCourse> courses) {
        return courses.stream().filter(AcademicGpaService::isGraded).mapToInt(CompletedCourse::getCredits).sum();
    }

    /** 취득 학점: F·NP·성적 미정 제외 (P 는 취득) */
    private static int earnedCredits(Collection<CompletedCourse> courses) {
        return courses.stream()
                .filter(c -> c.getGrade() != null && !NOT_EARNED_GRADES.contains(c.getGrade()))
                .mapToInt(CompletedCourse::getCredits).sum();
    }

    private static boolean isGraded(CompletedCourse c) {
        return c.getGrade() != null && GRADE_POINTS.containsKey(c.getGrade());
    }
}

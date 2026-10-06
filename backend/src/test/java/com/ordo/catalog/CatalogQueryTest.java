package com.ordo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.dto.CourseResponse;
import com.ordo.catalog.dto.MajorResponse;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.catalog.service.CatalogService;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"},
        showSql = false)
@Import(CatalogService.class)
class CatalogQueryTest {

    @Autowired CatalogService service;
    @Autowired MajorRepository majorRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepareCatalog() {
        jdbc.update("insert into colleges (id, name) values (1, '공과대학'), (2, '예술대학')");
        jdbc.update("""
                insert into majors (id, college_id, display_name, course_unit, convergence) values
                (101, 1, '컴퓨터공학과', '컴퓨터공학부', false),
                (102, 1, '컴퓨터공학부 컴퓨터공학과', '컴퓨터공학부', false),
                (103, 2, '산업디자인학과', '산업디자인학과', false),
                (104, 1, '융합전공', '컴퓨터공학부', true),
                (105, 1, '자료없는전공', '컴퓨터공학부', false),
                (106, 2, '글로벌한국학과', null, false)
                """);
        jdbc.update("""
                insert into graduation_requirements
                    (id, major_id, admission_year, total_credits, basic_credits) values
                (1, 101, 2025, 130, 12), (2, 102, 2026, 130, 12),
                (3, 103, 2026, 120, 9), (4, 104, 2026, 130, 12), (5, 106, 2026, 120, null)
                """);
        jdbc.update("""
                insert into courses (id, college_name, unit_name, course_code, name, classification, credits,
                                     variable_credits) values
                (201, '공과대학', '컴퓨터공학부', 'CSE204', '자료구조', 'MAJOR_REQUIRED', 3, false),
                (202, '공과대학', '컴퓨터공학부', 'CSE305', '운영체제', 'MAJOR_ELECTIVE', 3, false),
                (203, '예술대학', '산업디자인학과', 'CSE204', '디자인자료구조', 'MAJOR_ELECTIVE', 2, false),
                (204, '공과대학', '컴퓨터공학부', 'LAB_1', '실험%실습!', 'MAJOR_ELECTIVE', 1, true),
                (205, '공과대학', '컴퓨터공학부', 'CSE205', '자료구조', 'MAJOR_ELECTIVE', 2, false),
                (206, '공과대학', '컴퓨터공학부', null, '과목코드없는강의', 'MAJOR_REQUIRED', 3, false)
                """);
        jdbc.update("""
                insert into general_education_requirements
                    (id, admission_year, required_credits, distribution_credits, distribution_min_areas,
                     free_credits, total_credits)
                values (1, 2026, 17, 9, 3, 3, 29)
                """);
        jdbc.update("""
                insert into general_education_required_courses (id, admission_year, course_name, credits)
                values (1, 2026, '대학영어', 2)
                """);
    }

    @Test
    void majorsExcludeConvergenceAndOtherAdmissionYearsAndMatchSignupRules() {
        var majors = service.getMajors(2026, null);
        assertThat(majors).extracting(MajorResponse::id).containsExactlyInAnyOrder(102L, 103L, 106L);
        for (var major : majors) {
            assertThat(majorRepository.findSelectable(major.id(), 2026)).isPresent();
        }
        assertThat(majorRepository.findSelectable(104L, 2026)).isEmpty();
        assertThat(majorRepository.findSelectable(101L, 2026)).isEmpty();
    }

    @Test
    void majorsCanBeFilteredByCollegeAndHistoricalAdmissionYear() {
        assertThat(service.getMajors(2026, 1L)).extracting(MajorResponse::id).containsExactly(102L);
        assertThat(service.getMajors(2025, null)).extracting(MajorResponse::id).containsExactly(101L);
        assertThat(service.getMajors(2019, null)).isEmpty();
        assertThat(service.getMajors(2026, 999L)).isEmpty();
    }

    @Test
    void majorsContainCollegeInformationAndCollegesHaveStableOrder() {
        var major = service.getMajors(2026, 1L).get(0);
        assertThat(major.collegeId()).isEqualTo(1L);
        assertThat(major.collegeName()).isEqualTo("공과대학");
        assertThat(service.getColleges()).extracting("name").containsExactly("공과대학", "예술대학");
    }

    @Test
    void codeSearchUsesCourseUnitAndDoesNotLeakOtherMajorsWithTheSameCode() {
        var courses = service.searchCourses(102L, " cSe204 ", null, 0, 20);
        assertThat(courses.content()).extracting(CourseResponse::id).containsExactly(201L);
        assertThat(service.searchCourses(101L, "CSE204", null, 0, 20).content())
                .extracting(CourseResponse::id).containsExactly(201L);
        assertThat(service.searchCourses(103L, "CSE204", null, 0, 20).content())
                .extracting(CourseResponse::id).containsExactly(203L);
    }

    @Test
    void searchCombinesPartialNameAndClassification() {
        var courses = service.searchCourses(102L, "구조", Classification.MAJOR_REQUIRED, 0, 20);
        assertThat(courses.content()).extracting(CourseResponse::id).containsExactly(201L);
        assertThat(service.searchCourses(102L, "CSE20", null, 0, 20).content())
                .extracting(CourseResponse::id).containsExactly(201L, 205L);
        assertThat(service.searchCourses(102L, "과목코드없는", null, 0, 20).content())
                .extracting(CourseResponse::id).containsExactly(206L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void sqlWildcardCharactersAreSearchedLiterally(String query) {
        assertThat(service.searchCourses(102L, query, null, 0, 20).content())
                .extracting(CourseResponse::id).containsExactly(204L);
    }

    @Test
    void blankSearchListsOnlyTheMajorAndPaginationUsesNameThenId() {
        var first = service.searchCourses(102L, "  ", null, 0, 2);
        var last = service.searchCourses(102L, null, null, 2, 2);
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(last.content()).extracting(CourseResponse::id).containsExactly(205L);
        assertThat(last.page()).isEqualTo(2);
        assertThat(service.searchCourses(102L, null, null, 3, 2).content()).isEmpty();
    }

    @Test
    void missingCourseUnitReturnsAnEmptyPageInsteadOfAllCourses() {
        var courses = service.searchCourses(106L, null, null, 2, 10);
        assertThat(courses.content()).isEmpty();
        assertThat(courses.totalElements()).isZero();
        assertThat(courses.page()).isEqualTo(2);
        assertThat(courses.size()).isEqualTo(10);
    }

    @Test
    void nonexistentMajorReturnsTheSameErrorForCoursesAndRequirements() {
        assertError(() -> service.searchCourses(999L, null, null, 0, 20), ErrorCode.MAJOR_NOT_FOUND);
        assertError(() -> service.getRequirements(999L, 2026), ErrorCode.MAJOR_NOT_FOUND);
    }

    @Test
    void requirementsAreSpecificToAdmissionYearAndPreserveUnspecifiedValues() {
        var requirement = service.getRequirements(102L, 2026);
        assertThat(requirement.majorId()).isEqualTo(102L);
        assertThat(requirement.totalCredits()).isEqualTo(130);
        assertThat(requirement.basicCredits()).isEqualTo(12);
        assertThat(requirement.otherMajorCredits()).isNull();
        assertError(() -> service.getRequirements(102L, 2025), ErrorCode.REQUIREMENT_NOT_FOUND);
    }

    @Test
    void generalEducationUses2026OnlyWhenTheRequestedYearIsMissing() {
        var current = service.getGeneralEducation(2026);
        var fallback = service.getGeneralEducation(2025);
        assertThat(current.approximate()).isFalse();
        assertThat(fallback.admissionYear()).isEqualTo(2025);
        assertThat(fallback.basisYear()).isEqualTo(2026);
        assertThat(fallback.approximate()).isTrue();
        assertThat(fallback.distributionMinAreas()).isEqualTo(3);
        assertThat(fallback.requiredCourses()).extracting("courseName").containsExactly("대학영어");
    }

    @Test
    void exactGeneralEducationYearUsesItsOwnRequiredCourses() {
        jdbc.update("""
                insert into general_education_requirements
                    (id, admission_year, required_credits, distribution_credits, distribution_min_areas,
                     free_credits, total_credits) values (2, 2024, 15, 9, 3, 3, 27)
                """);
        jdbc.update("""
                insert into general_education_required_courses (id, admission_year, course_name, credits)
                values (2, 2024, '2024 필수과목', 3)
                """);
        var result = service.getGeneralEducation(2024);
        assertThat(result.approximate()).isFalse();
        assertThat(result.basisYear()).isEqualTo(2024);
        assertThat(result.totalCredits()).isEqualTo(27);
        assertThat(result.requiredCourses()).extracting("courseName").containsExactly("2024 필수과목");
    }

    @Test
    void missingGeneralEducationAndFallbackReturnRequirementNotFound() {
        jdbc.update("delete from general_education_requirements");
        assertError(() -> service.getGeneralEducation(2025), ErrorCode.REQUIREMENT_NOT_FOUND);
        assertError(() -> service.getGeneralEducation(2026), ErrorCode.REQUIREMENT_NOT_FOUND);
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}

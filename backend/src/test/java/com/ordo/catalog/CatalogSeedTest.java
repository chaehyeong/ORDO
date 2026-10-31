package com.ordo.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.catalog.dto.MajorResponse;
import com.ordo.catalog.dto.MicroDegreeResponse;
import com.ordo.catalog.service.CatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"},
        showSql = false)
@Import(CatalogService.class)
@Sql(scripts = {"/db/migration/V2__seed_catalog.sql", "/db/migration/V15__fix_course_names.sql",
        "/db/migration/V16__fix_sports_practice_course_units.sql", "/db/migration/V18__seed_micro_degrees.sql"},
        config = @SqlConfig(encoding = "UTF-8"))
class CatalogSeedTest {

    @Test
    void minorOptionsUseActualPositiveTotalsAndPreserveMissingAreaValues() {
        var options = service.getMinors(2026, null);
        assertThat(options).hasSize(53);
        assertThat(options).allSatisfy(option -> assertThat(option.totalCredits()).isPositive());
        assertThat(options).filteredOn(option -> option.id().equals(14L)).singleElement().satisfies(option -> {
            assertThat(option.requiredCredits()).isEqualTo(9);
            assertThat(option.electiveCredits()).isEqualTo(12);
        });
        assertThat(options).filteredOn(option -> option.id().equals(35L)).singleElement()
                .satisfies(option -> assertThat(option.requiredCredits()).isNull());
        assertThat(options).noneMatch(option -> option.id().equals(55L));
        assertThat(service.getMinors(2025, null)).hasSize(51);
        assertThat(service.getMinors(2026, 3L)).allSatisfy(option -> assertThat(option.collegeId()).isEqualTo(3L));
        assertThat(service.getMinors(2010, null)).isEmpty();
    }

    // V2 3036행 - 잘못 들어간 전문실기 6행 + 체육대학 5개 학과 × 6행
    private static final int COURSES_AFTER_V16 = 3060;

    @Autowired CatalogService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void existingSeedCanBeQueriedWithRealMajorIdsAndHistoricalAliases() {
        assertThat(service.getColleges()).hasSize(10);
        assertThat(jdbc.queryForObject("select count(*) from majors", Integer.class)).isEqualTo(76);
        assertThat(jdbc.queryForObject("select count(*) from graduation_requirements", Integer.class)).isEqualTo(393);
        assertThat(jdbc.queryForObject("select count(*) from courses", Integer.class)).isEqualTo(COURSES_AFTER_V16);
        assertThat(service.getMajors(2026, null, false)).extracting(MajorResponse::id).contains(35L, 54L);
        assertThat(service.searchCourses(54L, null, null, 0, 20).content())
                .isNotEmpty().allSatisfy(course ->
                        assertThat(course.unitName()).isEqualTo("컴퓨터공학부 컴퓨터공학과"));
        var oldMajor = service.getMajors(2020, null, false).stream()
                .filter(major -> major.displayName().equals("컴퓨터공학과")).findFirst().orElseThrow();
        assertThat(service.searchCourses(oldMajor.id(), null, null, 0, 20).content())
                .isEqualTo(service.searchCourses(54L, null, null, 0, 20).content());
        assertThat(service.getGeneralEducation(2025).requiredCourses()).hasSize(6);
        assertThat(service.getGeneralEducation(2025).approximate()).isTrue();
    }

    // V15: 숫자로 시작해도 정상인 이름은 3D·2D·4D 과목 20행, 소문자로 시작해도 정상인 이름은 h-STEM융합개론 뿐이다
    @Test
    void garbledCourseNamesAreFixedAndLegitimateNamesAreKept() {
        var names = jdbc.queryForList("select name from courses", String.class);

        assertThat(names).hasSize(COURSES_AFTER_V16);
        assertThat(names).filteredOn(name -> name.matches("^\\d.*")).hasSize(20)
                .allMatch(name -> name.matches("^\\dD.*"));
        assertThat(names).filteredOn(name -> name.matches("^[a-z].*")).containsExactly("h-STEM융합개론");
        assertThat(names).doesNotContain("Foreign Policy", "Organization", "지도법", "졸업스튜디오1",
                "격년개설33식물·식품미생물응용학", "E40124386전문실기6(뉴스포츠)", "ClimateChangesandESGManagement");
        assertThat(names).contains("American Foreign Policy", "Introduction to EconomicsⅠ", "시각디자인졸업스튜디오1",
                "식물·식품미생물응용학", "3D모델링", "h-STEM융합개론");
    }

    // V18: 타대학 융합 묶음은 과목·요건이 없어 뺐다. 추정 학점은 넣지 않는다
    @Test
    void microDegreeSeedMatchesTheSourceSheet() {
        var degrees = service.getMicroDegrees();

        assertThat(degrees).hasSize(56);
        assertThat(degrees).extracting(MicroDegreeResponse::name).doesNotContain("타대학 융합 마이크로디그리");
        assertThat(degrees.stream().filter(d -> d.scope().equals("공통"))).hasSize(7);
        assertThat(degrees.stream().flatMap(d -> d.courses().stream())).hasSize(249);
        assertThat(degrees.stream().flatMap(d -> d.courses().stream()).filter(c -> c.credits() == null)).hasSize(27);
        assertThat(degrees.stream().flatMap(d -> d.courses().stream()).filter(c -> c.courseCode() == null)).hasSize(4);
        assertThat(jdbc.queryForObject("select count(*) from micro_degree_courses where alias_code is not null", Integer.class))
                .isEqualTo(12);
        assertThat(degrees.stream().filter(MicroDegreeResponse::selective)).extracting(MicroDegreeResponse::name)
                .containsExactlyInAnyOrder("첨단패키징", "의료인공지능-Medical AI");
        assertThat(degrees.stream().filter(d -> d.minDepartments() == 2)).extracting(MicroDegreeResponse::name)
                .containsExactlyInAnyOrder("로컬브랜드매니지먼트", "로컬콘텐츠마케팅", "지역공간디자인매니지먼트", "AI로봇");
        assertThat(degrees).allSatisfy(d -> {
            assertThat(d.courses()).isNotEmpty();
            assertThat(d.courses().size()).isGreaterThanOrEqualTo(d.requiredCourseCount());
        });
    }

    // 참여학과 2개 조건이 있는 MD 는 2개 학과 이상의 과목이 편성돼 있어야 채울 수 있다
    @Test
    void microDegreesWithDepartmentRuleOfferAtLeastTwoDepartments() {
        assertThat(service.getMicroDegrees().stream().filter(d -> d.minDepartments() == 2)).allSatisfy(d ->
                assertThat(d.courses().stream().map(MicroDegreeResponse.CourseItem::departmentName).distinct().count())
                        .isGreaterThanOrEqualTo(2));
    }

    // V16: 전문실기는 체육대학 공통 전공선택이다
    @Test
    void sportsPracticeCoursesBelongToEverySportsDepartment() {
        for (String code : List.of("CPE201", "CPE202", "CPE301", "CPE302", "CPE401", "CPE402")) {
            assertThat(jdbc.queryForList("select unit_name from courses where course_code = ?", String.class, code))
                    .containsExactlyInAnyOrder("체육학과", "스포츠의학과", "스포츠지도학과", "골프산업학과", "태권도학과");
        }
        assertThat(jdbc.queryForList("select name from courses where course_code = 'CPE402'", String.class))
                .containsOnly("전문실기6");
        assertThat(jdbc.queryForList("select distinct college_name from courses where course_code like 'CPE%'", String.class))
                .containsExactly("체육대학");
        assertThat(jdbc.queryForObject(
                "select count(*) from courses where unit_name = '포스트모던음악학과' and course_code like 'CPE%'", Integer.class))
                .isZero();
    }
}

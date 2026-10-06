package com.ordo.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.catalog.dto.MajorResponse;
import com.ordo.catalog.service.CatalogService;
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
@Sql(scripts = "/db/migration/V2__seed_catalog.sql", config = @SqlConfig(encoding = "UTF-8"))
class CatalogSeedTest {

    @Autowired CatalogService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void existingSeedCanBeQueriedWithRealMajorIdsAndHistoricalAliases() {
        assertThat(service.getColleges()).hasSize(10);
        assertThat(jdbc.queryForObject("select count(*) from majors", Integer.class)).isEqualTo(76);
        assertThat(jdbc.queryForObject("select count(*) from graduation_requirements", Integer.class)).isEqualTo(393);
        assertThat(jdbc.queryForObject("select count(*) from courses", Integer.class)).isEqualTo(3036);
        assertThat(service.getMajors(2026, null)).extracting(MajorResponse::id).contains(35L, 54L);
        assertThat(service.searchCourses(54L, null, null, 0, 20).content())
                .isNotEmpty().allSatisfy(course ->
                        assertThat(course.unitName()).isEqualTo("컴퓨터공학부 컴퓨터공학과"));
        var oldMajor = service.getMajors(2020, null).stream()
                .filter(major -> major.displayName().equals("컴퓨터공학과")).findFirst().orElseThrow();
        assertThat(service.searchCourses(oldMajor.id(), null, null, 0, 20).content())
                .isEqualTo(service.searchCourses(54L, null, null, 0, 20).content());
        assertThat(service.getGeneralEducation(2025).requiredCourses()).hasSize(6);
        assertThat(service.getGeneralEducation(2025).approximate()).isTrue();
    }
}

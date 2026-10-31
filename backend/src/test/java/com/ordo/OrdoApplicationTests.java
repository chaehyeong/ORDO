package com.ordo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OrdoApplicationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    // 부팅 시 Flyway 가 V1(스키마)·V2(시드)·시드 교정 V16·마이크로디그리 V18·전공탐색세미나 V19·2020~2023 교양 기준 V20 을 적용했는지 확인
    @Test
    void flywaySeedApplied() {
        assertThat(count("colleges")).isEqualTo(10);
        assertThat(count("majors")).isEqualTo(76);
        assertThat(count("courses")).isEqualTo(3060);
        assertThat(count("micro_degrees")).isEqualTo(56);
        assertThat(count("micro_degree_courses")).isEqualTo(249);
        assertThat(count("general_education_requirements")).isEqualTo(5);
        assertThat(count("general_education_required_courses")).isEqualTo(35);
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}

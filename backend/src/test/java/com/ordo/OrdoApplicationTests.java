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

    // 부팅 시 Flyway 가 V1(스키마)·V2(시드)를 적용했는지 확인
    @Test
    void flywaySeedApplied() {
        assertThat(count("colleges")).isEqualTo(10);
        assertThat(count("majors")).isEqualTo(76);
        assertThat(count("courses")).isEqualTo(3036);
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}

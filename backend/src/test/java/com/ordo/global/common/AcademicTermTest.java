package com.ordo.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AcademicTermTest {

    @Test
    void marchToAugustIsFirstTerm() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 3, 1))).isEqualTo(new AcademicTerm(2026, Term.FIRST));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 8, 31))).isEqualTo(new AcademicTerm(2026, Term.FIRST));
    }

    @Test
    void septemberToDecemberIsSecondTerm() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 9, 1))).isEqualTo(new AcademicTerm(2026, Term.SECOND));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 12, 31))).isEqualTo(new AcademicTerm(2026, Term.SECOND));
    }

    @Test
    void januaryAndFebruaryBelongToPreviousYearSecondTerm() {
        assertThat(AcademicTerm.of(LocalDate.of(2027, 1, 1))).isEqualTo(new AcademicTerm(2026, Term.SECOND));
        assertThat(AcademicTerm.of(LocalDate.of(2027, 2, 28))).isEqualTo(new AcademicTerm(2026, Term.SECOND));
    }

    @Test
    void label() {
        assertThat(new AcademicTerm(2026, Term.SECOND).label()).isEqualTo("2026년 2학기");
        assertThat(new AcademicTerm(2026, Term.SUMMER).label()).isEqualTo("2026년 여름계절학기");
    }
}

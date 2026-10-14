package com.ahni.backend.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScholarshipCriteriaTest {
    private final Department department = new Department("소프트웨어융합공학과");

    @Test
    void 학과와_입학연도별_장학_기준을_생성한다() {
        ScholarshipCriteria criteria = new ScholarshipCriteria(
            department, 2024, " 성적우수장학금 ",
            new BigDecimal("3.50"), new BigDecimal("12.0"), 0
        );

        assertThat(criteria.getDepartment()).isSameAs(department);
        assertThat(criteria.getAdmissionYear()).isEqualTo(2024);
        assertThat(criteria.getName()).isEqualTo("성적우수장학금");
        assertThat(criteria.getMinGpa()).isEqualByComparingTo("3.50");
        assertThat(criteria.getMinCredits()).isEqualByComparingTo("12.0");
        assertThat(criteria.getMaxWarnings()).isZero();
    }

    @Test
    void 장학_기준의_이름과_기준값을_수정한다() {
        ScholarshipCriteria criteria = criteria();

        criteria.update("국가근로장학금", new BigDecimal("2.00"), new BigDecimal("10.0"), 1);

        assertThat(criteria.getName()).isEqualTo("국가근로장학금");
        assertThat(criteria.getMinGpa()).isEqualByComparingTo("2.00");
        assertThat(criteria.getMinCredits()).isEqualByComparingTo("10.0");
        assertThat(criteria.getMaxWarnings()).isEqualTo(1);
    }

    @Test
    void 최소_평점은_4점50을_넘을_수_없다() {
        assertThatThrownBy(() -> criteria().update(
            "성적우수장학금", new BigDecimal("4.51"), new BigDecimal("12.0"), 0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기준값은_음수일_수_없다() {
        ScholarshipCriteria criteria = criteria();

        assertThatThrownBy(() -> criteria.update(
            "성적우수장학금", new BigDecimal("-0.01"), new BigDecimal("12.0"), 0
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria.update(
            "성적우수장학금", new BigDecimal("3.00"), new BigDecimal("-1.0"), 0
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria.update(
            "성적우수장학금", new BigDecimal("3.00"), new BigDecimal("12.0"), -1
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 장학금_이름은_비어_있을_수_없다() {
        assertThatThrownBy(() -> criteria().update(
            " ", new BigDecimal("3.00"), new BigDecimal("12.0"), 0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private ScholarshipCriteria criteria() {
        return new ScholarshipCriteria(
            department, 2024, "성적우수장학금",
            new BigDecimal("3.50"), new BigDecimal("12.0"), 0
        );
    }
}

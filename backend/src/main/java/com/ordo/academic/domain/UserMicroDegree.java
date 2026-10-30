package com.ordo.academic.domain;

import com.ordo.catalog.domain.MicroDegree;
import com.ordo.global.common.BaseTimeEntity;
import com.ordo.user.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 등록한 마이크로디그리 (T20). 등록 순서가 과목 배정 순서다 */
@Entity
@Table(name = "user_micro_degrees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMicroDegree extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "micro_degree_id")
    private MicroDegree microDegree;

    @Builder
    private UserMicroDegree(User user, MicroDegree microDegree) {
        this.user = user;
        this.microDegree = microDegree;
    }
}

package com.ordo.user.domain;

import com.ordo.catalog.domain.Major;
import com.ordo.global.common.BaseTimeEntity;
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
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    public static final LocalTime DEFAULT_TODO_ALARM_TIME = LocalTime.of(9, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String password;  // BCrypt. 소셜 로그인 사용자는 null

    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String name;
    private String nickname;
    private String studentNumber;
    private String phone;
    private String profileImageUrl;
    private Integer admissionYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id")
    private Major major;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "double_major_id")
    private Major doubleMajor;  // 복수전공·융합전공 (T19). null 이면 단일전공

    private Integer currentSemester;
    private boolean notificationEnabled;
    private LocalTime todoAlarmTime;  // 시간 없는 할 일의 알림 기준 시각 (T11, 기본 09:00)
    private String ecampusFeedToken;  // Canvas 캘린더 피드 토큰. 비밀값이라 응답·로그에 내보내지 않는다

    /** 이메일 회원가입 (password 는 BCrypt 로 인코딩된 값) */
    @Builder
    private User(String email, String password, String name, String nickname, String studentNumber, String phone,
                 Integer admissionYear, Major major, Integer currentSemester) {
        this.email = email;
        this.password = password;
        this.provider = Provider.LOCAL;
        this.role = Role.USER;
        this.name = name;
        this.nickname = nickname;
        this.studentNumber = studentNumber;
        this.phone = phone;
        this.admissionYear = admissionYear;
        this.major = major;
        this.currentSemester = currentSemester;
        this.notificationEnabled = true;
        this.todoAlarmTime = DEFAULT_TODO_ALARM_TIME;
    }

    /** PATCH: null 인 값은 그대로 둔다 */
    public void updateProfile(String name, String nickname, String studentNumber, String phone,
                              Integer admissionYear, Major major, Integer currentSemester, String profileImageUrl) {
        if (name != null) this.name = name;
        if (nickname != null) this.nickname = nickname;
        if (studentNumber != null) this.studentNumber = studentNumber;
        if (phone != null) this.phone = phone;
        if (admissionYear != null) this.admissionYear = admissionYear;
        if (major != null) this.major = major;
        if (currentSemester != null) this.currentSemester = currentSemester;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
    }

    /** PATCH settings: null 인 값은 그대로 둔다 */
    public void changeSettings(Boolean notificationEnabled, LocalTime todoAlarmTime) {
        if (notificationEnabled != null) this.notificationEnabled = notificationEnabled;
        if (todoAlarmTime != null) this.todoAlarmTime = todoAlarmTime;
    }

    /** null 이면 해제 */
    public void changeDoubleMajor(Major doubleMajor) {
        this.doubleMajor = doubleMajor;
    }

    /** null 이면 연결 해제 */
    public void changeEcampusFeedToken(String ecampusFeedToken) {
        this.ecampusFeedToken = ecampusFeedToken;
    }
}

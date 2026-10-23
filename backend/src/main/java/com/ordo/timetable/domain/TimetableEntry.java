package com.ordo.timetable.domain;

import com.ordo.global.common.BaseTimeEntity;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
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

/** 시간표 한 칸. 학기 안에서 매주 반복된다 */
@Entity
@Table(name = "timetable_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableEntry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private int academicYear;

    @Enumerated(EnumType.STRING)
    private Term term;

    private String courseName;
    private String courseCode;
    private int dayOfWeek;  // 1=월 … 7=일 (ISO)
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private String professor;
    private String color;   // #RRGGBB

    @Builder
    private TimetableEntry(User user, int academicYear, Term term, String courseName, String courseCode, int dayOfWeek,
                           LocalTime startTime, LocalTime endTime, String location, String professor, String color) {
        this.user = user;
        this.academicYear = academicYear;
        this.term = term;
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.professor = professor;
        this.color = color;
        validate();
    }

    /** PATCH: null 인 값은 그대로 둔다. 바꾼 뒤의 값으로 다시 검증 */
    public void update(Integer academicYear, Term term, String courseName, String courseCode, Integer dayOfWeek,
                       LocalTime startTime, LocalTime endTime, String location, String professor, String color) {
        if (academicYear != null) this.academicYear = academicYear;
        if (term != null) this.term = term;
        if (courseName != null) this.courseName = courseName;
        if (courseCode != null) this.courseCode = courseCode;
        if (dayOfWeek != null) this.dayOfWeek = dayOfWeek;
        if (startTime != null) this.startTime = startTime;
        if (endTime != null) this.endTime = endTime;
        if (location != null) this.location = location;
        if (professor != null) this.professor = professor;
        if (color != null) this.color = color;
        validate();
    }

    /** 시간이 겹치는지 (같은 학기·요일인지는 호출하는 쪽에서 거른다). 끝과 시작이 딱 붙는 건 겹침이 아니다 */
    public boolean overlaps(TimetableEntry other) {
        return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }

    private void validate() {
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
    }
}

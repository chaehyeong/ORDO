package com.ordo.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ordo.timetable.domain.TimetableEntry;
import java.time.LocalTime;

public record TimetableEntryResponse(Long id, String courseName, String courseCode, int dayOfWeek,
                                     @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                     @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                     String location, String professor, String color) {

    public static TimetableEntryResponse from(TimetableEntry entry) {
        return new TimetableEntryResponse(entry.getId(), entry.getCourseName(), entry.getCourseCode(),
                entry.getDayOfWeek(), entry.getStartTime(), entry.getEndTime(), entry.getLocation(),
                entry.getProfessor(), entry.getColor());
    }
}

package com.ordo.timetable.dto;

import com.ordo.global.common.Term;
import java.util.List;

public record TimetableResponse(int year, Term term, String label, List<TimetableEntryResponse> entries) {
}

package com.ordo.timetable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ordo.global.common.AcademicTerm;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.timetable.domain.TimetableEntry;
import com.ordo.timetable.dto.TimetableEntryBulkRequest;
import com.ordo.timetable.dto.TimetableEntryCreateRequest;
import com.ordo.timetable.dto.TimetableEntryResponse;
import com.ordo.timetable.dto.TimetableEntryUpdateRequest;
import com.ordo.timetable.dto.TimetableResponse;
import com.ordo.timetable.repository.TimetableEntryRepository;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {

    static final int TUESDAY = 2;

    @Mock
    TimetableEntryRepository timetableEntryRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    TimetableService timetableService;

    @Test
    void createReturnsEntry() {
        given(userRepository.getReferenceById(1L)).willReturn(user());
        givenTuesdayEntries();
        given(timetableEntryRepository.save(any(TimetableEntry.class))).willAnswer(invocation -> invocation.getArgument(0));

        TimetableEntryResponse response = timetableService.create(1L, createRequest(at(9, 0), at(10, 30)));

        assertThat(response.courseName()).isEqualTo("타이포그래피 I");
        assertThat(response.courseCode()).isEqualTo("DES201");
        assertThat(response.dayOfWeek()).isEqualTo(TUESDAY);
    }

    @Test
    void endTimeMustBeAfterStartTime() {
        given(userRepository.getReferenceById(1L)).willReturn(user());

        assertErrorCode(() -> timetableService.create(1L, createRequest(at(10, 30), at(10, 30))),
                ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void overlappingEntryIsConflict() {
        given(userRepository.getReferenceById(1L)).willReturn(user());
        givenTuesdayEntries(entry(at(10, 0), at(11, 0)));

        assertErrorCode(() -> timetableService.create(1L, createRequest(at(9, 0), at(10, 30))),
                ErrorCode.TIMETABLE_OVERLAP);
        verify(timetableEntryRepository, never()).save(any());
    }

    @Test
    void touchingEntriesDoNotOverlap() {
        given(userRepository.getReferenceById(1L)).willReturn(user());
        givenTuesdayEntries(entry(at(9, 0), at(10, 30)));
        given(timetableEntryRepository.save(any(TimetableEntry.class))).willAnswer(invocation -> invocation.getArgument(0));

        TimetableEntryResponse response = timetableService.create(1L, createRequest(at(10, 30), at(12, 0)));

        assertThat(response.startTime()).isEqualTo(at(10, 30));
    }

    @Test
    void updateIgnoresItselfWhenCheckingOverlap() {
        TimetableEntry entry = entry(at(9, 0), at(10, 30));
        given(timetableEntryRepository.findByIdAndUserId(3L, 1L)).willReturn(Optional.of(entry));
        givenTuesdayEntries(entry);

        timetableService.update(1L, 3L, endTimeOnly(at(11, 0)));

        assertThat(entry.getEndTime()).isEqualTo(at(11, 0));
        assertThat(entry.getCourseName()).isEqualTo("타이포그래피 I");
    }

    @Test
    void updateIntoOverlapIsConflict() {
        TimetableEntry entry = entry(at(9, 0), at(10, 30));
        given(timetableEntryRepository.findByIdAndUserId(3L, 1L)).willReturn(Optional.of(entry));
        givenTuesdayEntries(entry, entry(at(11, 0), at(12, 0)));

        assertErrorCode(() -> timetableService.update(1L, 3L, endTimeOnly(at(11, 30))), ErrorCode.TIMETABLE_OVERLAP);
    }

    @Test
    void othersEntryIsNotFound() {
        given(timetableEntryRepository.findByIdAndUserId(3L, 2L)).willReturn(Optional.empty());

        assertErrorCode(() -> timetableService.update(2L, 3L, endTimeOnly(at(11, 0))),
                ErrorCode.TIMETABLE_ENTRY_NOT_FOUND);
        assertErrorCode(() -> timetableService.delete(2L, 3L), ErrorCode.TIMETABLE_ENTRY_NOT_FOUND);
        verify(timetableEntryRepository, never()).delete(any());
    }

    @Test
    void omittedYearOrTermIsCurrentTerm() {
        AcademicTerm current = AcademicTerm.now();
        given(timetableEntryRepository.findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(
                1L, current.year(), current.term())).willReturn(List.of(entry(at(9, 0), at(10, 30))));
        given(timetableEntryRepository.findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(
                1L, 2025, current.term())).willReturn(List.of());

        TimetableResponse response = timetableService.getTimetable(1L, null, null);
        TimetableResponse onlyYear = timetableService.getTimetable(1L, 2025, null);

        assertThat(response.label()).isEqualTo(current.label());
        assertThat(response.entries()).hasSize(1);
        assertThat(onlyYear.year()).isEqualTo(2025);
        assertThat(onlyYear.term()).isEqualTo(current.term());
    }

    @Test
    void bulkReplaceSwapsWholeTerm() {
        TimetableEntry old = entry(at(9, 0), at(10, 30));
        givenTermEntries(old);

        TimetableResponse response = timetableService.saveAll(1L, bulk(TimetableEntryBulkRequest.Mode.REPLACE,
                bulkEntry("미분방정식", 4, at(13, 30)), bulkEntry("객체지향프로그래밍", 2, at(15, 0))));

        verify(timetableEntryRepository).deleteAll(List.of(old));
        assertThat(response.entries()).extracting(TimetableEntryResponse::courseName)
                .containsExactly("객체지향프로그래밍", "미분방정식");  // 요일 순
        assertThat(response.label()).isEqualTo("2026년 2학기");
    }

    @Test
    void bulkMergeKeepsExistingAndSkipsSameSlot() {
        TimetableEntry existing = entry(at(9, 0), at(10, 30));  // 화 09:00 타이포그래피 I
        givenTermEntries(existing);

        TimetableResponse response = timetableService.saveAll(1L, bulk(TimetableEntryBulkRequest.Mode.MERGE,
                new TimetableEntryBulkRequest.Entry("타이포그래피 I", null, TUESDAY, at(9, 0), at(10, 30), null, null, null),
                bulkEntry("색채학", 3, at(9, 0))));

        verify(timetableEntryRepository, never()).deleteAll(any());
        verify(timetableEntryRepository).saveAll(org.mockito.ArgumentMatchers.argThat(
                (List<TimetableEntry> saved) -> saved.size() == 1 && saved.get(0).getCourseName().equals("색채학")));
        assertThat(response.entries()).extracting(TimetableEntryResponse::courseName).containsExactly("타이포그래피 I", "색채학");
    }

    @Test
    void bulkWithOverlapChangesNothing() {
        givenTermEntries(entry(at(9, 0), at(10, 30)));

        assertErrorCode(() -> timetableService.saveAll(1L, bulk(TimetableEntryBulkRequest.Mode.REPLACE,
                bulkEntry("A", 1, at(9, 0)), bulkEntry("B", 1, at(10, 0)))), ErrorCode.TIMETABLE_OVERLAP);
        verify(timetableEntryRepository, never()).deleteAll(any());
        verify(timetableEntryRepository, never()).saveAll(any());
    }

    private void givenTermEntries(TimetableEntry... entries) {
        given(userRepository.getReferenceById(1L)).willReturn(user());
        given(timetableEntryRepository.findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(1L, 2026, Term.SECOND))
                .willReturn(List.of(entries));
    }

    private static TimetableEntryBulkRequest bulk(TimetableEntryBulkRequest.Mode mode, TimetableEntryBulkRequest.Entry... entries) {
        return new TimetableEntryBulkRequest(2026, Term.SECOND, mode, List.of(entries));
    }

    private static TimetableEntryBulkRequest.Entry bulkEntry(String name, int dayOfWeek, LocalTime start) {
        return new TimetableEntryBulkRequest.Entry(name, null, dayOfWeek, start, start.plusMinutes(75), null, null, null);
    }

    private void givenTuesdayEntries(TimetableEntry... entries) {
        given(timetableEntryRepository.findByUserIdAndAcademicYearAndTermAndDayOfWeek(1L, 2026, Term.SECOND, TUESDAY))
                .willReturn(List.of(entries));
    }

    private void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    private TimetableEntryCreateRequest createRequest(LocalTime startTime, LocalTime endTime) {
        return new TimetableEntryCreateRequest(2026, Term.SECOND, "타이포그래피 I", "DES201", TUESDAY,
                startTime, endTime, "예405", "김교수", "#3BB39A");
    }

    private TimetableEntryUpdateRequest endTimeOnly(LocalTime endTime) {
        return new TimetableEntryUpdateRequest(null, null, null, null, null, null, endTime, null, null, null);
    }

    private TimetableEntry entry(LocalTime startTime, LocalTime endTime) {
        return TimetableEntry.builder().user(user()).academicYear(2026).term(Term.SECOND).courseName("타이포그래피 I")
                .dayOfWeek(TUESDAY).startTime(startTime).endTime(endTime).build();
    }

    private User user() {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").build();
    }

    private static LocalTime at(int hour, int minute) {
        return LocalTime.of(hour, minute);
    }
}

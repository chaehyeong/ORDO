package com.ordo.timetable.service;

import com.ordo.global.common.AcademicTerm;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.timetable.domain.TimetableEntry;
import com.ordo.timetable.dto.TimetableEntryCreateRequest;
import com.ordo.timetable.dto.TimetableEntryResponse;
import com.ordo.timetable.dto.TimetableEntryUpdateRequest;
import com.ordo.timetable.dto.TimetableResponse;
import com.ordo.timetable.repository.TimetableEntryRepository;
import com.ordo.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableService {

    private final TimetableEntryRepository timetableEntryRepository;
    private final UserRepository userRepository;

    /** year·term 중 생략한 값은 현재 학기 값으로 채운다 */
    public TimetableResponse getTimetable(Long userId, Integer year, Term term) {
        AcademicTerm current = AcademicTerm.now();
        AcademicTerm target = new AcademicTerm(year != null ? year : current.year(),
                term != null ? term : current.term());
        List<TimetableEntryResponse> entries = timetableEntryRepository
                .findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(userId, target.year(), target.term())
                .stream()
                .map(TimetableEntryResponse::from)
                .toList();
        return new TimetableResponse(target.year(), target.term(), target.label(), entries);
    }

    @Transactional
    public TimetableEntryResponse create(Long userId, TimetableEntryCreateRequest request) {
        TimetableEntry entry = TimetableEntry.builder()
                .user(userRepository.getReferenceById(userId))
                .academicYear(request.year())
                .term(request.term())
                .courseName(request.courseName())
                .courseCode(request.courseCode())
                .dayOfWeek(request.dayOfWeek())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .location(request.location())
                .professor(request.professor())
                .color(request.color())
                .build();
        checkOverlap(userId, entry);
        return TimetableEntryResponse.from(timetableEntryRepository.save(entry));
    }

    @Transactional
    public TimetableEntryResponse update(Long userId, Long entryId, TimetableEntryUpdateRequest request) {
        TimetableEntry entry = getOwnEntry(userId, entryId);
        entry.update(request.year(), request.term(), request.courseName(), request.courseCode(), request.dayOfWeek(),
                request.startTime(), request.endTime(), request.location(), request.professor(), request.color());
        checkOverlap(userId, entry);
        return TimetableEntryResponse.from(entry);
    }

    @Transactional
    public void delete(Long userId, Long entryId) {
        timetableEntryRepository.delete(getOwnEntry(userId, entryId));
    }

    // 같은 학기·요일의 다른 칸과 시간이 겹치면 409.
    // 수정 중인 칸은 같은 영속성 컨텍스트에서 같은 인스턴스로 조회되므로 != 로 자기 자신을 뺀다
    private void checkOverlap(Long userId, TimetableEntry entry) {
        boolean overlapped = timetableEntryRepository
                .findByUserIdAndAcademicYearAndTermAndDayOfWeek(userId, entry.getAcademicYear(), entry.getTerm(),
                        entry.getDayOfWeek())
                .stream()
                .anyMatch(other -> other != entry && other.overlaps(entry));
        if (overlapped) {
            throw new BusinessException(ErrorCode.TIMETABLE_OVERLAP);
        }
    }

    // 남의 칸도 "없음"으로 응답해 존재 여부를 노출하지 않는다
    private TimetableEntry getOwnEntry(Long userId, Long entryId) {
        return timetableEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TIMETABLE_ENTRY_NOT_FOUND));
    }
}

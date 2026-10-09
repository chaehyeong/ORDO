package com.ordo.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 명세서 2.5 표. 새 코드가 필요하면 명세서 표부터 갱신한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증·사용자
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "refresh 토큰이 없거나 만료되었습니다."),
    PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST, "입학년도와 전공을 먼저 설정해 주세요."),

    // 학사 기준정보
    MAJOR_NOT_FOUND(HttpStatus.NOT_FOUND, "전공을 찾을 수 없습니다."),
    REQUIREMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 학번·전공의 졸업요건 데이터가 없습니다."),

    // 일정·시간표
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "시간 또는 기간 범위가 올바르지 않습니다."),
    TIMETABLE_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "시간표 항목을 찾을 수 없습니다."),
    TIMETABLE_OVERLAP(HttpStatus.CONFLICT, "같은 시간에 이미 등록된 수업이 있습니다."),

    // 이수내역
    COMPLETED_COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "이수 과목을 찾을 수 없습니다."),
    CLASSIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "이수구분을 입력해 주세요."),
    DISTRIBUTION_AREA_REQUIRED(HttpStatus.BAD_REQUEST, "배분이수 영역(1~5)을 입력해 주세요."),

    // e캠퍼스 연동
    ECAMPUS_FEED_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "e캠퍼스 일정을 가져오지 못했습니다. 캘린더 피드 주소를 다시 등록해 보세요."),

    // 파일 가져오기 (T12)
    IMPORT_UNSUPPORTED_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일입니다. 엑셀(xlsx)로 저장해서 올려주세요."),
    IMPORT_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일이 너무 큽니다. 5MB 이하로 올려주세요."),
    IMPORT_UNRECOGNIZED(HttpStatus.BAD_REQUEST, "수강신청확인서나 전체 성적 보기 파일이 아니거나 내용을 읽지 못했습니다."),
    IMPORT_STUDENT_MISMATCH(HttpStatus.FORBIDDEN, "내 학번의 파일이 아닙니다.");

    private final HttpStatus status;
    private final String message;
}

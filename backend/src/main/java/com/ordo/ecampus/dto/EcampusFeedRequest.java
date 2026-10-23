package com.ordo.ecampus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** e캠퍼스 캘린더 → '캘린더 피드' 에서 복사한 주소. 다른 사이트 주소는 받지 않는다 */
public record EcampusFeedRequest(
        @NotNull
        @Pattern(regexp = "^https://khcanvas\\.khu\\.ac\\.kr/feeds/calendars/user_[A-Za-z0-9]{1,90}\\.ics$",
                message = "e캠퍼스 캘린더 피드 주소(https://khcanvas.khu.ac.kr/feeds/calendars/user_….ics)를 넣어 주세요.")
        String feedUrl) {

    /** 주소에서 'user_...' 부분만 꺼낸다 (형식은 @Pattern 으로 이미 확인됨) */
    public String feedToken() {
        return feedUrl.substring(feedUrl.lastIndexOf('/') + 1, feedUrl.length() - ".ics".length());
    }
}

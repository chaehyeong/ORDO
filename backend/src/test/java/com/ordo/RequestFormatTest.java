package com.ordo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordo.schedule.dto.ScheduleUpdateRequest;
import com.ordo.timetable.dto.TimetableEntryBulkRequest;
import com.ordo.timetable.dto.TimetableEntryUpdateRequest;
import com.ordo.user.dto.UserUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** 여러 도메인의 요청 DTO 가 같은 규칙을 지키는지 본다 */
class RequestFormatTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void patchRejectsBlankOnlyTextAndAllowsOmission() {
        assertThat(VALIDATOR.validate(new ScheduleUpdateRequest("   ", null, null, null, null, null, null, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TimetableEntryUpdateRequest(null, null, "   ", null, null, null, null, null, null, null, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new UserUpdateRequest("   ", null, null, null, null, null, null, null))).isNotEmpty();

        assertThat(VALIDATOR.validate(new ScheduleUpdateRequest(null, null, null, null, null, null, null, null))).isEmpty();
        assertThat(VALIDATOR.validate(new ScheduleUpdateRequest("팀플\n모임", null, null, null, null, null, null, null))).isEmpty();
    }

    @Test
    void bulkEntryTimeIsHourAndMinuteInBothDirections() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TimetableEntryBulkRequest.Entry entry = new TimetableEntryBulkRequest.Entry("수업", null, 2,
                LocalTime.of(15, 0), LocalTime.of(16, 50), null, null, null, false);

        JsonNode json = mapper.valueToTree(entry);

        assertThat(json.get("startTime").asText()).isEqualTo("15:00");
        assertThat(json.get("endTime").asText()).isEqualTo("16:50");
        assertThat(mapper.treeToValue(json, TimetableEntryBulkRequest.Entry.class)).isEqualTo(entry);
    }
}

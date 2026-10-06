package com.ordo.catalog;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ordo.catalog.controller.CatalogController;
import com.ordo.catalog.dto.CollegeResponse;
import com.ordo.catalog.dto.CoursePageResponse;
import com.ordo.catalog.dto.GeneralEducationResponse;
import com.ordo.catalog.dto.MajorResponse;
import com.ordo.catalog.service.CatalogService;
import com.ordo.global.config.SecurityConfig;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.global.error.GlobalExceptionHandler;
import com.ordo.global.security.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CatalogControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CatalogService service;
    @MockBean JwtProvider jwtProvider;

    @Test
    void collegesAndMajorsCanBeReadBeforeLogin() throws Exception {
        given(service.getColleges()).willReturn(List.of(new CollegeResponse(3L, "소프트웨어융합대학")));
        given(service.getMajors(2026, 3L))
                .willReturn(List.of(new MajorResponse(54L, "컴퓨터공학부 컴퓨터공학과", 3L, "소프트웨어융합대학")));
        mvc.perform(get("/api/catalog/colleges"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(3));
        mvc.perform(get("/api/catalog/majors").param("admissionYear", "2026").param("collegeId", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(54))
                .andExpect(jsonPath("$.data[0].collegeName").value("소프트웨어융합대학"));
    }

    @Test
    void searchHasStablePaginationDefaultsAndAllowsAnEmptyOptionalFilter() throws Exception {
        given(service.searchCourses(54L, null, null, 0, 20))
                .willReturn(new CoursePageResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/catalog/courses").param("majorId", "54").param("classification", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20));
        verify(service).searchCourses(54L, null, null, 0, 20);
    }

    @Test
    void generalEducationMakesTheFallbackVisible() throws Exception {
        given(service.getGeneralEducation(2025))
                .willReturn(new GeneralEducationResponse(2025, 2026, true, 17, 9, 3, 3, 29, List.of()));
        mvc.perform(get("/api/catalog/general-education").param("admissionYear", "2025"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.basisYear").value(2026))
                .andExpect(jsonPath("$.data.approximate").value(true));
    }

    @Test
    void requirementsReturnTheCommonBusinessErrorWithoutAuthentication() throws Exception {
        given(service.getRequirements(anyLong(), anyInt()))
                .willThrow(new BusinessException(ErrorCode.REQUIREMENT_NOT_FOUND));
        mvc.perform(get("/api/catalog/requirements").param("majorId", "54").param("admissionYear", "2019"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REQUIREMENT_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/catalog/majors", "/api/catalog/majors?admissionYear=abc",
            "/api/catalog/majors?admissionYear=0", "/api/catalog/majors?admissionYear=2026&collegeId=-1",
            "/api/catalog/courses", "/api/catalog/courses?majorId=-1",
            "/api/catalog/courses?majorId=54&page=-1", "/api/catalog/courses?majorId=54&size=0",
            "/api/catalog/courses?majorId=54&size=101",
            "/api/catalog/courses?majorId=54&classification=UNKNOWN",
            "/api/catalog/requirements?majorId=54", "/api/catalog/general-education?admissionYear=-1"
    })
    void invalidQueryParametersUseTheCommon400Response(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void excessivelyLongSearchTermsAreRejected() throws Exception {
        mvc.perform(get("/api/catalog/courses").param("majorId", "54").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}

package com.ordo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.academic.dto.CompletedCourseBulkRequest;
import com.ordo.academic.dto.CompletedCourseRequest;
import com.ordo.academic.dto.PlannedCoursesRequest;
import com.ordo.academic.service.CompletedCourseService;
import com.ordo.academic.service.PlannedCourseService;
import com.ordo.auth.domain.RefreshToken;
import com.ordo.auth.repository.RefreshTokenRepository;
import com.ordo.auth.service.AuthService;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.timetable.dto.TimetableEntryCreateRequest;
import com.ordo.timetable.service.TimetableService;
import com.ordo.user.domain.User;
import com.ordo.user.dto.UserResponse;
import com.ordo.user.dto.UserSettingsRequest;
import com.ordo.user.dto.UserUpdateRequest;
import com.ordo.user.repository.UserRepository;
import com.ordo.user.service.UserService;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 실제 DB와 독립 트랜잭션으로 같은 사용자의 동시 요청을 검증한다: 500·중복 저장·값 유실이 없어야 한다 */
@SpringBootTest
class ConcurrentRequestsTest {

    private static final int THREADS = 4;

    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthService auth;
    @Autowired TimetableService timetable;
    @Autowired PlannedCourseService plans;
    @Autowired CompletedCourseService completed;
    @Autowired UserService userService;

    private User user;

    @BeforeEach
    void createUser() {
        user = users.saveAndFlush(User.builder()
                .email("concurrency-" + UUID.randomUUID() + "@example.invalid")
                .name("동시성검증").build());
    }

    @AfterEach
    void removeUser() {
        users.deleteById(user.getId());  // 사용자 데이터는 FK ON DELETE CASCADE
    }

    @Test
    void sameRefreshTokenSucceedsOnlyOnce() throws Exception {
        String token = "concurrency-" + UUID.randomUUID();
        refreshTokens.saveAndFlush(new RefreshToken(user, token, LocalDateTime.now().plusDays(1)));

        List<Throwable> failures = runTogether(Collections.nCopies(THREADS, () -> auth.refresh(token)));

        assertThat(failures).hasSize(THREADS - 1);
        assertThat(failures).allSatisfy(e -> assertErrorCode(e, ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void sameTimetableSlotIsSavedOnlyOnce() throws Exception {
        TimetableEntryCreateRequest request = new TimetableEntryCreateRequest(2026, Term.SECOND, "동시", null, 4,
                LocalTime.of(13, 0), LocalTime.of(14, 0), null, null, null, false);

        List<Throwable> failures = runTogether(Collections.nCopies(THREADS, () -> timetable.create(user.getId(), request)));

        assertThat(failures).hasSize(THREADS - 1);
        assertThat(failures).allSatisfy(e -> assertErrorCode(e, ErrorCode.TIMETABLE_OVERLAP));
        assertThat(timetable.getTimetable(user.getId(), 2026, Term.SECOND).entries()).hasSize(1);
    }

    @Test
    void plansReplacedTogetherLeaveOneList() throws Exception {
        plans.replaceAll(user.getId(), plan("기존"));

        List<Throwable> failures = runTogether(Collections.nCopies(THREADS, () -> plans.replaceAll(user.getId(), plan("새 과목"))));

        assertThat(failures).isEmpty();
        assertThat(plans.getPlans(user.getId()).totalCredits()).isEqualTo(3);
    }

    @Test
    void termReplacedTogetherLeavesOneCopy() throws Exception {
        CompletedCourseBulkRequest request = new CompletedCourseBulkRequest(
                List.of(new CompletedCourseRequest(null, "가져온 과목", 3, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST)),
                List.of(new CompletedCourseBulkRequest.TermRef(2026, Term.FIRST)), true);

        List<Throwable> failures = runTogether(Collections.nCopies(THREADS, () -> completed.createAll(user.getId(), request)));

        assertThat(failures).isEmpty();
        assertThat(completed.getCourses(user.getId())).hasSize(1);
    }

    @Test
    void profileAndSettingsChangedTogetherKeepBothValues() throws Exception {
        for (int round = 0; round < 20; round++) {
            String nickname = "n" + round;
            boolean enabled = round % 2 == 0;

            List<Throwable> failures = runTogether(List.of(
                    () -> userService.updateMe(user.getId(), new UserUpdateRequest(null, nickname, null, null, null, null, null, null)),
                    () -> userService.updateSettings(user.getId(), new UserSettingsRequest(enabled, null))));

            assertThat(failures).isEmpty();
            UserResponse me = userService.getMe(user.getId());
            assertThat(me.nickname()).isEqualTo(nickname);
            assertThat(me.notificationEnabled()).isEqualTo(enabled);
        }
    }

    private static PlannedCoursesRequest plan(String courseName) {
        return new PlannedCoursesRequest(List.of(new PlannedCoursesRequest.Course(2027, Term.FIRST, courseName, null, null, 3)));
    }

    private static void assertErrorCode(Throwable error, ErrorCode errorCode) {
        assertThat(error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) error).getErrorCode()).isEqualTo(errorCode);
    }

    /** 모두 함께 시작하고, 실패한 요청의 예외만 돌려준다 */
    private static List<Throwable> runTogether(List<Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (Callable<?> task : tasks) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Object> result : results) {
                try {
                    result.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}

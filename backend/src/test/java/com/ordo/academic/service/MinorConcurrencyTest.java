package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.academic.dto.DoubleMajorRequest;
import com.ordo.academic.dto.MinorRequest;
import com.ordo.academic.repository.UserMinorRepository;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.dto.UserUpdateRequest;
import com.ordo.user.repository.UserRepository;
import com.ordo.user.service.UserService;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MinorConcurrencyTest {

    @Autowired MinorService minors;
    @Autowired DoubleMajorService doubles;
    @Autowired UserService profiles;
    @Autowired UserRepository users;
    @Autowired UserMinorRepository registrations;
    @Autowired MajorRepository majors;
    private Long userId;

    @BeforeEach
    void createUser() {
        userId = users.saveAndFlush(User.builder().name("부전공동시성")
                .email("minor-race-" + UUID.randomUUID() + "@example.invalid")
                .major(majors.findById(35L).orElseThrow()).admissionYear(2026).build()).getId();
    }

    @AfterEach
    void cleanup() {
        if (userId != null) users.deleteById(userId);
    }

    @Test
    void minorAndPrimaryChangesCannotCreateSameMajor() throws Exception {
        int successes = together(() -> minors.register(userId, new MinorRequest(14L)),
                () -> profiles.updateMe(userId, new UserUpdateRequest(null, null, null, null, null, 14L, null, null)));

        assertThat(successes).isEqualTo(1);
        var minor = minors.get(userId);
        if (minor != null) assertThat(profiles.getMe(userId).major().id()).isNotEqualTo(minor.major().id());
    }

    @Test
    void minorAndDoubleMajorCannotRegisterSameMajorConcurrently() throws Exception {
        int successes = together(() -> minors.register(userId, new MinorRequest(14L)),
                () -> doubles.register(userId, new DoubleMajorRequest(14L)));

        assertThat(successes).isEqualTo(1);
        assertThat(minors.get(userId) == null || doubles.get(userId) == null).isTrue();
        assertThat(registrations.findByUserId(userId).isPresent()).isEqualTo(minors.get(userId) != null);
    }

    private int together(Runnable first, Runnable second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var a = executor.submit(task(first, ready, start));
            var b = executor.submit(task(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return a.get(20, TimeUnit.SECONDS) + b.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Callable<Integer> task(Runnable action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("동시 요청 시작 시간 초과");
            try {
                action.run();
                return 1;
            } catch (BusinessException error) {
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                return 0;
            }
        };
    }
}

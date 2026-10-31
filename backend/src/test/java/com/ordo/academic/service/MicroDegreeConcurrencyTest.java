package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.academic.dto.MicroDegreeAddRequest;
import com.ordo.academic.repository.UserMicroDegreeRepository;
import com.ordo.catalog.repository.MicroDegreeRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 실제 DB와 독립 트랜잭션으로 같은 사용자의 동시 MD 등록을 검증한다. */
@SpringBootTest
class MicroDegreeConcurrencyTest {

    @Autowired MicroDegreeService service;
    @Autowired UserRepository users;
    @Autowired UserMicroDegreeRepository registrations;
    @Autowired MicroDegreeRepository degrees;

    private Long userId;
    private List<Long> degreeIds;

    @BeforeEach
    void createUser() {
        userId = users.saveAndFlush(User.builder()
                .email("md-concurrency-" + UUID.randomUUID() + "@example.invalid")
                .name("동시성검증").build()).getId();
        degreeIds = degrees.findAllByOrderByNameAscIdAsc().stream().map(degree -> degree.getId()).toList();
    }

    @AfterEach
    void removeUser() {
        if (userId != null) {
            users.deleteById(userId);  // user_micro_degrees는 FK ON DELETE CASCADE
        }
    }

    @Test
    void simultaneousDifferentDegreesCannotExceedThreeRegistrations() throws Exception {
        service.add(userId, new MicroDegreeAddRequest(degreeIds.get(0)));
        service.add(userId, new MicroDegreeAddRequest(degreeIds.get(1)));

        long succeeded = registerTogether(degreeIds.subList(2, 10));

        assertThat(succeeded).isEqualTo(1);
        assertThat(registrations.findByUserIdOrderByIdAsc(userId)).hasSize(3);
    }

    @Test
    void simultaneousDuplicateDegreeProducesOneRegistrationAndBusinessErrors() throws Exception {
        List<Long> sameDegree = java.util.Collections.nCopies(8, degreeIds.get(0));

        long succeeded = registerTogether(sameDegree);

        assertThat(succeeded).isEqualTo(1);
        assertThat(registrations.findByUserIdOrderByIdAsc(userId)).hasSize(1);
    }

    private long registerTogether(List<Long> ids) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(ids.size());
        CountDownLatch ready = new CountDownLatch(ids.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (Long id : ids) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("동시 등록 시작 시간 초과");
                    }
                    try {
                        service.add(userId, new MicroDegreeAddRequest(id));
                        return true;
                    } catch (BusinessException error) {
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                        return false;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get(20, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            return succeeded;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}

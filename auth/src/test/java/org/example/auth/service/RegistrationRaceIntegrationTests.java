package org.example.auth.service;

import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.exception.UserAlreadyExistsException;
import org.example.auth.repository.UserRepository;
import org.example.auth.support.PostgresTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real concurrent registrations against real PostgreSQL. No test-managed transaction (NOT_SUPPORTED),
 * so each createUser call commits on its own, like in production.
 * Whatever the timing, the outcome must be: exactly one user created, every other request a clean
 * UserAlreadyExistsException. A raw DataIntegrityViolationException escaping fails the test.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.application.token-expiration-duration=604800000"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, UserService.class, RefreshTokenService.class,
        RegistrationRaceIntegrationTests.EncoderConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegistrationRaceIntegrationTests {

    @TestConfiguration
    static class EncoderConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);   // lowest cost: fast tests, still a real hash
        }
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void concurrentRegistrationsOfTheSameUsernameShouldYieldOneUserAndCleanConflicts() throws Exception {
        int threads = 8;
        int created = 0;
        int conflicts = 0;

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);

            List<Future<String>> outcomes = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                outcomes.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();                                    // release all threads at the same moment
                    try {
                        userService.createUser(new RegisterRequestDTO("racer", "mySecurePassword123"));
                        return "created";
                    } catch (UserAlreadyExistsException e) {
                        return "conflict";
                    }
                }));
            }
            ready.await();
            go.countDown();


            for (Future<String> outcome : outcomes) {
                // any other exception (e.g. DataIntegrityViolationException) is rethrown here and fails the test
                if ("created".equals(outcome.get(30, TimeUnit.SECONDS))) {
                    created++;
                } else {
                    conflicts++;
                }
            }
            executor.shutdown();
        }

        assertEquals(1, created, "exactly one registration may succeed");
        assertEquals(threads - 1, conflicts, "all others must be reported as UserAlreadyExistsException");
        assertEquals(1, userRepository.count());
    }
}
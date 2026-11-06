package org.example.auth.service;

import org.example.auth.exception.LastAdminException;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Demotion against real PostgreSQL with real transactions and row locks. No test-managed transaction
 * (NOT_SUPPORTED): data is really committed, so every test cleans up.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.application.token-expiration-duration=604800000"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, UserService.class, RefreshTokenService.class,
        DemotionIntegrationTests.EncoderConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemotionIntegrationTests {

    @TestConfiguration
    static class EncoderConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
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

    private void saveUser(String username, EnumSet<AccessRole> roles) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$hashedPasswordValue");
        user.setRoles(roles);
        userRepository.saveAndFlush(user);
    }

    private Set<AccessRole> rolesOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getRoles();
    }

    private long adminCount() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRoles().contains(AccessRole.ADMIN))
                .count();
    }

    @Test
    void demotionShouldBePersistedWhenAnotherAdminExists() {
        saveUser("admin-one", EnumSet.of(AccessRole.ADMIN, AccessRole.USER));
        saveUser("admin-two", EnumSet.of(AccessRole.ADMIN, AccessRole.USER));

        userService.demoteFromAdmin("admin-two");

        assertEquals(Set.of(AccessRole.USER), rolesOf("admin-two"));
        assertEquals(Set.of(AccessRole.ADMIN, AccessRole.USER), rolesOf("admin-one"));
    }

    @Test
    void lastAdminShouldBeRefusedAndKeepTheRole() {
        saveUser("only-admin", EnumSet.of(AccessRole.ADMIN, AccessRole.USER));
        saveUser("martin", EnumSet.of(AccessRole.USER));

        assertThrows(LastAdminException.class, () -> userService.demoteFromAdmin("only-admin"));

        assertEquals(Set.of(AccessRole.ADMIN, AccessRole.USER), rolesOf("only-admin"));
    }

    @Test
    void demotingARegularUserShouldBeAHarmlessNoOp() {
        saveUser("only-admin", EnumSet.of(AccessRole.ADMIN, AccessRole.USER));
        saveUser("martin", EnumSet.of(AccessRole.USER));

        userService.demoteFromAdmin("martin");

        assertEquals(Set.of(AccessRole.USER), rolesOf("martin"));
        assertEquals(1, adminCount());
    }

    @Test
    void twoAdminsDemotingEachOtherAtOnceShouldNeverLeaveZeroAdmins() throws Exception {
        saveUser("admin-a", EnumSet.of(AccessRole.ADMIN, AccessRole.USER));
        saveUser("admin-b", EnumSet.of(AccessRole.ADMIN, AccessRole.USER));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<String> aDemotesB = () -> attempt(ready, go, "admin-b");
        Callable<String> bDemotesA = () -> attempt(ready, go, "admin-a");
        List<Future<String>> outcomes = List.of(executor.submit(aDemotesB), executor.submit(bDemotesA));

        ready.await();
        go.countDown();

        int demoted = 0;
        int refused = 0;
        for (Future<String> outcome : outcomes) {
            // any other exception (deadlock, lock failure) is rethrown here and fails the test
            if ("demoted".equals(outcome.get(30, TimeUnit.SECONDS))) {
                demoted++;
            } else {
                refused++;
            }
        }
        executor.shutdown();

        assertEquals(1, demoted, "exactly one demotion may succeed");
        assertEquals(1, refused, "the other must be refused as the last admin");
        assertEquals(1, adminCount(), "one admin must remain");
    }

    private String attempt(CountDownLatch ready, CountDownLatch go, String target) throws InterruptedException {
        ready.countDown();
        go.await();                                   // release both threads at the same moment
        try {
            userService.demoteFromAdmin(target);
            return "demoted";
        } catch (LastAdminException e) {
            return "refused";
        }
    }
}
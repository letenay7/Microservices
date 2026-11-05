package org.example.auth.bootstrap;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validation;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AdminBootstrap against real PostgreSQL (each test is rolled back). */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfig.class)
class AdminBootstrapIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private void runWith(String username, String password) {
        new AdminBootstrap(userRepository, encoder,
                Validation.buildDefaultValidatorFactory().getValidator(), username, password)
                .run(new DefaultApplicationArguments());
    }

    @Test
    void shouldCreateAnAdminWhoCanLogInWithTheConfiguredPassword() {
        runWith("admin", "StrongPassword123");
        entityManager.clear();

        User admin = userRepository.findByUsername("admin").orElseThrow();

        assertEquals(Set.of(AccessRole.ADMIN, AccessRole.USER), admin.getRoles());
        assertNotEquals("StrongPassword123", admin.getPassword(), "must be stored hashed");
        assertTrue(encoder.matches("StrongPassword123", admin.getPassword()),
                "the configured password must verify against the stored hash (this is what /login checks)");
    }

    @Test
    void shouldBeIdempotentAcrossRestarts() {
        runWith("admin", "StrongPassword123");
        runWith("admin", "StrongPassword123");
        runWith("admin", "StrongPassword123");

        assertEquals(1, userRepository.count());
    }

    @Test
    void shouldNotPromoteOrModifyAnExistingRegularUser() {
        User registered = new User();
        registered.setUsername("admin");
        registered.setPassword("existing-hash");
        registered.setRoles(EnumSet.of(AccessRole.USER));
        userRepository.saveAndFlush(registered);
        entityManager.clear();

        runWith("admin", "AnotherPassword123");
        entityManager.clear();

        User reloaded = userRepository.findByUsername("admin").orElseThrow();
        assertEquals(Set.of(AccessRole.USER), reloaded.getRoles(), "must not be promoted");
        assertEquals("existing-hash", reloaded.getPassword(), "password must not be overwritten");
    }
}
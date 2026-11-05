package org.example.auth.bootstrap;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the first admin account at startup, from configuration.
 * <p>
 * Set BOTH properties (normally as environment variables) for the first start only:
 * APP_BOOTSTRAP_ADMIN_USERNAME and APP_BOOTSTRAP_ADMIN_PASSWORD
 * (app.bootstrap-admin.username / app.bootstrap-admin.password).
 * <p>
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final String username;
    private final String password;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          Validator validator,
                          @Value("${bootstrap.admin-username}") String username,
                          @Value("${app.bootstrap.admin-password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean noUsername = username.isBlank();
        boolean noPassword = password.isBlank();

        if (noUsername && noPassword) {
            log.debug("No bootstrap admin configured");
            return;
        }
        if (noUsername || noPassword) {
            throw new IllegalStateException(
                    "app.bootstrap-admin.username and app.bootstrap-admin.password must be set together");
        }
        validateConfiguration();

        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            if (!existing.get().getRoles().contains(AccessRole.ADMIN)) {
                log.warn("User '{}' already exists without the ADMIN role and was NOT modified. "
                        + "Use another bootstrap username, or promote that account deliberately.", username);
            }
            return;
        }
        createAdmin();
    }

    // Same rules as registration, so the bootstrap account cannot be weaker than a normal one
    private void validateConfiguration() {
        Set<ConstraintViolation<RegisterRequestDTO>> violations =
                validator.validate(new RegisterRequestDTO(username, password));
        if (!violations.isEmpty()) {
            // field names only: never put the password into a log or an exception message
            String fields = violations.stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Invalid bootstrap admin configuration, check: " + fields);
        }
    }

    private void createAdmin() {
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRoles(EnumSet.of(AccessRole.ADMIN, AccessRole.USER));   // created with both roles in one save
        try {
            userRepository.saveAndFlush(admin);
            log.info("Created bootstrap admin '{}'. Remove the bootstrap credentials from the "
                    + "environment and change this password after the first login.", username);
        } catch (DataIntegrityViolationException e) {
            // another instance (or a registration) took the username between our check and the insert
            log.warn("Could not create bootstrap admin '{}': the username was taken concurrently. "
                    + "Check that account.", username);
        }
    }
}
package org.example.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.example.auth.security.AccessRole;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank
    private String username;

    @Column(nullable = false)
    @NotBlank
    private String password;

    @Column(nullable = false)
    private EnumSet<AccessRole> roles = EnumSet.noneOf(AccessRole.class);

    public boolean addRole(AccessRole accessRole) {
         return roles.add(accessRole);
    }

    public boolean removeRole(AccessRole accessRole) {
        return roles.remove(accessRole);
    }

    public Set<String> rolesToStringSet() {
        return roles.stream()
                .map(AccessRole::getValue)
                .collect(Collectors.toSet());
    }
}

package org.example.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.example.auth.security.AccessRole;

import java.util.EnumSet;

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
    private String username;

    @Column(nullable = false)
    @Size(min = 8)
    private String password;

    @Column(nullable = false)
    private EnumSet<AccessRole> roles;

    public boolean addRole(AccessRole accessRole) {
         return roles.add(accessRole);
    }

    public boolean removeRole(AccessRole accessRole) {
        return roles.remove(accessRole);
    }
}

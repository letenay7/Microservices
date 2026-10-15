package org.example.auth.security;

import lombok.Getter;

@Getter
public enum AccessRole {
    ADMIN("ADMIN"),
    USER("USER");
    private String value;
    AccessRole(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return name().charAt(0) + name().substring(1).toLowerCase();

    }
}

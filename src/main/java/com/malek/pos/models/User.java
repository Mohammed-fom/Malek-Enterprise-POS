package com.malek.pos.models;

import lombok.Data;

@Data
public class User {
    private int userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private int roleId;
    private String pinCode;
    private boolean isActive;

    // Joined Field
    private String roleName;

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(roleName);
    }
}

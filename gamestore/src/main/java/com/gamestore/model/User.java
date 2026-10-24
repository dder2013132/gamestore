package com.gamestore.model;

import java.io.Serializable;

/** Session data only; never contains a password or password hash. */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private final long id;
    private final String username;
    private final String role;

    public User(long id, String username) {
        this(id, username, "USER");
    }

    public User(long id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
}

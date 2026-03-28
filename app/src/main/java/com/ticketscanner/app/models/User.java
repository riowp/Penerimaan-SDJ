package com.ticketscanner.app.models;

public class User {
    // Role constants
    public static final String ROLE_OPERATOR   = "operator";
    public static final String ROLE_SUPERVISOR = "supervisor";
    public static final String ROLE_ADMIN      = "admin";
    public static final String ROLE_GUEST      = "guest";

    private String username;
    private String password;
    private String namaLengkap;
    private String role;
    private String status; // aktif / nonaktif

    public User() {}

    public User(String username, String password, String namaLengkap, String role, String status) {
        this.username    = username;
        this.password    = password;
        this.namaLengkap = namaLengkap;
        this.role        = role;
        this.status      = status;
    }

    public String getUsername()              { return username; }
    public void setUsername(String v)       { this.username = v; }
    public String getPassword()             { return password; }
    public void setPassword(String v)       { this.password = v; }
    public String getNamaLengkap()          { return namaLengkap; }
    public void setNamaLengkap(String v)    { this.namaLengkap = v; }
    public String getRole()                 { return role; }
    public void setRole(String v)           { this.role = v; }
    public String getStatus()               { return status; }
    public void setStatus(String v)         { this.status = v; }

    public boolean isAdmin()      { return ROLE_ADMIN.equals(role); }
    public boolean isSupervisor() { return ROLE_SUPERVISOR.equals(role) || isAdmin(); }
    public boolean isGuest()      { return ROLE_GUEST.equals(role); }
    public boolean canEdit()      { return !isGuest(); } // guest tidak bisa edit/hapus/tambah

    public String getRoleLabel() {
        switch (role != null ? role : "") {
            case ROLE_ADMIN:      return "Admin";
            case ROLE_SUPERVISOR: return "Supervisor";
            case ROLE_GUEST:      return "Guest";
            default:              return "Operator";
        }
    }
}

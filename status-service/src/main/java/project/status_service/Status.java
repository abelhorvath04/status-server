package project.status_service;

import java.time.LocalDateTime;

public class Status {
    private Long id;
    private String username;
    private String statustext;
    private LocalDateTime uhrzeit;

    public Status() {}

    public Status(Long id, String username, String statustext, LocalDateTime uhrzeit) {
        this.id = id;
        this.username = username;
        this.statustext = statustext;
        this.uhrzeit = uhrzeit;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatustext() {
        return statustext;
    }

    public void setStatustext(String statustext) {
        this.statustext = statustext;
    }

    public LocalDateTime getUhrzeit() {
        return uhrzeit;
    }

    public void setUhrzeit(LocalDateTime uhrzeit) {
        this.uhrzeit = uhrzeit;
    }
}

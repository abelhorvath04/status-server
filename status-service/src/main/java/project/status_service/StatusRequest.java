package project.status_service;

public class StatusRequest {
    private String username;
    private String statusText;

    public StatusRequest() {}

    public StatusRequest(String username, String statusText) {
        this.username = username;
        this.statusText = statusText;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }
}

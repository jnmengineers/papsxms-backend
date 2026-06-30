package jnm.engineer.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String role;
    private String username;
    private Long linkedClassId;
    private String linkedClassName;
    private String linkedStream; // ✅ NEW — stream of teacher's linked class (e.g. "YELLOW"), null if no stream

    // ✅ New — tells the frontend to force a password change on first login
    private boolean mustChangePassword;

    // Convenience constructor preserving the old call signature (defaults linkedStream/mustChangePassword)
    public LoginResponse(String token, String role, String username, Long linkedClassId, String linkedClassName) {
        this.token = token;
        this.role = role;
        this.username = username;
        this.linkedClassId = linkedClassId;
        this.linkedClassName = linkedClassName;
        this.linkedStream = null;
        this.mustChangePassword = false;
    }
}
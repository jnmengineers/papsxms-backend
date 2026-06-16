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

    // ✅ New — tells the frontend to force a password change on first login
    private boolean mustChangePassword;

    // Convenience constructor preserving the old call signature (defaults mustChangePassword to false)
    public LoginResponse(String token, String role, String username, Long linkedClassId, String linkedClassName) {
        this.token = token;
        this.role = role;
        this.username = username;
        this.linkedClassId = linkedClassId;
        this.linkedClassName = linkedClassName;
        this.mustChangePassword = false;
    }
}
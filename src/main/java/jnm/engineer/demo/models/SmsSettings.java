package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The SMS account (Africa's Talking) — one row, id = 1. The API key is never sent back to the screen. */
@Entity
@Table(name = "sms_settings")
@Getter
@Setter
@NoArgsConstructor
public class SmsSettings {
    @Id private Long id = 1L;
    @Column(length = 60) private String username;
    @Column(length = 120) private String apiKey;
    /** Approved sender ID (alphanumeric name). Empty = the provider's shared number. */
    @Column(length = 11) private String senderId;
    /** Sandbox = free test messages that aren't delivered to real phones. */
    private Boolean sandbox;
    private Boolean enabled;
}

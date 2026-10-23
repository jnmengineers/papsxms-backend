package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Parent portal sign-in: an SMS code (stored hashed) and, once confirmed, a 30-day session token. */
@Entity
@Table(name = "parent_logins", indexes = { @Index(name = "ix_pl_phone", columnList = "phone"), @Index(name = "ix_pl_token", columnList = "token") })
@Getter
@Setter
@NoArgsConstructor
public class ParentLogin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long guardianId;
    @Column(length = 20) private String phone;
    @Column(length = 64) private String codeHash;
    private LocalDateTime codeExpires;
    private Integer attempts;
    @Column(length = 64) private String token;
    private LocalDateTime tokenExpires;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeen;
}

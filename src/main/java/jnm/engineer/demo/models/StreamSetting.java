package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A stream (Yellow, Blue …). New streams can be added; the code of an existing one never changes. */
@Entity
@Table(name = "setting_streams")
@Getter
@Setter
@NoArgsConstructor
public class StreamSetting {
    @Id @Column(length = 20) private String code;           // YELLOW, BLUE, …
    @Column(nullable = false, length = 40) private String name;
    @Column(nullable = false, length = 9)  private String color;
    @Column(nullable = false) private Integer sortOrder;
    @Column(nullable = false) private boolean active = true;
}

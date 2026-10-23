package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A form built in the system (like Google Forms): questions as JSON, shared by a short code. */
@Entity
@Table(name = "surveys", indexes = @Index(name = "ux_surveys_code", columnList = "code", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class Survey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 12, nullable = false) private String code;
    @Column(length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    /** [{id,type,label,help,required,options:[...]}] — types: short, paragraph, single, multi, dropdown, rating, scale, yesno, number, date, heading */
    @Column(columnDefinition = "MEDIUMTEXT") private String questions;
    @Column(length = 10) private String status;          // DRAFT, OPEN, CLOSED
    private LocalDate opensOn;
    private LocalDate closesOn;
    private Boolean allowAnonymous;
    private Boolean collectContact;
    private Boolean onePerPhone;
    /** Listed in the website's "Forms & surveys" section while open */
    private Boolean showOnWebsite;
    @Column(length = 600) private String thankYou;
    @Column(length = 50) private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

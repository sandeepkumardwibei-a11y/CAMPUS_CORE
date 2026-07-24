package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "department")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Long departmentId; // Auto-incremented primary key

    @Column(name = "department_name", nullable = false, unique = true, length = 150)
    private String departmentName;

    // NOTE: programId removed. A department no longer belongs to a program.
    // The relationship is now one department -> many programs (see Program.departmentId).

    @Column(nullable = false, length = 15)
    private String status;

    @PrePersist
    protected void onCreate() {
        if (status == null) status = "ACTIVE";
    }
}

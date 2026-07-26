package com.Accounting.app.AI;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class AgentFolder {
    @Id
    private String id;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String name;

    @Column(length = 32)
    private String color;

    @Column(nullable = false)
    private Instant createdAt;
}

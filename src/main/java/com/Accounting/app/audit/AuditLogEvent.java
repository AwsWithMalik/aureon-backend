package com.Accounting.app.audit;

import java.time.Instant;

import com.Accounting.app.auth.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "audit_log_events",
        indexes = {
                @Index(name = "idx_audit_user_time", columnList = "user_email, occurred_at"),
                @Index(name = "idx_audit_user_resource", columnList = "user_email, resource"),
                @Index(name = "idx_audit_user_result", columnList = "user_email, result")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    private String actor;
    private String actorType;
    private String action;

    @Column(length = 2000)
    private String detail;

    private String resource;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private String ipAddress;

    @Column(length = 1000)
    private String device;

    private String method;

    @Column(length = 1000)
    private String path;

    private Integer statusCode;
    private String result;
    private String requestId;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;
}

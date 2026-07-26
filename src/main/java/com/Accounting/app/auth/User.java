package com.Accounting.app.auth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


import com.Accounting.app.accounts.Account;
import com.Accounting.app.plaid.PlaidItem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class User {

    @OneToMany(mappedBy = "user")
    private List<Account> accounts = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToMany(mappedBy = "user")
    private List<PlaidItem> financialInstitutions = new ArrayList<>();

    @Column(unique = true, nullable = false)
    private String email;

    private String loginPassword;

    private String name;
    private String MfaSecret = null;
    private Boolean MfaEnabled = false;
    private LocalDateTime MfaEnabledAt = null;

    public User(List<PlaidItem> financialInstitutions, String email, String loginPassword, String name,
            String MfaSecret, Boolean MfaEnabled, LocalDateTime MfaEnabledAt) {
        this.financialInstitutions = financialInstitutions;
        this.email = email;
        this.loginPassword = loginPassword;
        this.name = name;
        this.MfaSecret = MfaSecret;
        this.MfaEnabled = MfaEnabled;
        this.MfaEnabledAt = MfaEnabledAt;
    }

    public User() {

    }

    public String getEmail() {
        return email;

    }

    public String getName() {
        return name;
    }

    public Integer getUserId() {
        return id;
    }

    public String getLoginPassword() {
        return loginPassword;
    }

    public String getMfaSecret() {
        return MfaSecret;
    }

    public Boolean getMfaEnabled() {
        return MfaEnabled;
    }

    public LocalDateTime getMfaEnabledAt() {
        return MfaEnabledAt;
    }

    public List<PlaidItem> getFinancialInstitutions() {
        return financialInstitutions;
    }

    public void setLoginPassword(String hashedPassword) {
        this.loginPassword = hashedPassword;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMfaEnabled(Boolean status) {
        this.MfaEnabled = status;
    }

    public void setMfaSecret(String secret) {
        this.MfaSecret = secret;
    }

    public void setMfaEnabledAt(LocalDateTime enabledAt) {
        this.MfaEnabledAt = enabledAt;
    }
}

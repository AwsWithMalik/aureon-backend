package com.Accounting.app.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class UserDto {
    @NotBlank(message = "Email cannot be blank")
    private String email;
    
    private String name;
    private Integer userId;

    public UserDto(Integer userId, String email, String name) {
        this.email = email;
        this.name = name;
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public Integer getUserId() {
        return userId;
    }

    public Integer getId() {
        return userId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(Integer userId) {
        this.userId = userId;
    }
}

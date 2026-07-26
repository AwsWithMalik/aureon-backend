package com.Accounting.app.auth.dto;


public class AuthRegister {
    private UserDto user;

    public AuthRegister(UserDto user) {
        this.user = user;
    }

    public UserDto getUser() {
        return user;
    }

    public void setUser(UserDto user) {
        this.user = user;
    }

}

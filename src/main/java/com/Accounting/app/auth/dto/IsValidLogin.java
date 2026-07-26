package com.Accounting.app.auth.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsValidLogin {
    private Boolean isValidToken;
    private UserDto userDto;
}
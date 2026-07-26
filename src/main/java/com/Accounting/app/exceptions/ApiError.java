package com.Accounting.app.exceptions;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApiError {
    private Integer status;
    private String message;
    private LocalDateTime timestamp = LocalDateTime.now();

    public ApiError(Integer status, String message, LocalDateTime timestamp) {
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
    }
}
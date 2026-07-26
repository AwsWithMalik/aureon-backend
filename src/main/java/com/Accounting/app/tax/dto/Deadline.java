package com.Accounting.app.tax.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Deadline {
    private String id;
    private String label;
    private LocalDate dueDate;
    private String status;
}

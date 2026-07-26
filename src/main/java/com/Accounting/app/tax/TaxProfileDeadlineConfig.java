package com.Accounting.app.tax;

import java.time.LocalDate;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfileDeadlineConfig {
    private String deadlineId;
    private String label;
    private LocalDate dueDate;
    private String status;
}

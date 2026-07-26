package com.Accounting.app.dashboard.dto;

import com.Accounting.app.accounts.dto.Balance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Approval {
    private String id;
    private String title;
    private String owner;
    private Balance value;
    private String priority;
}

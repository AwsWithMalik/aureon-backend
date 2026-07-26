package com.Accounting.app.accounts.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkedCards {
    private String id;
    private String name;
    private String ownerName;
    private Spend spend;
    private Limit limit;

}

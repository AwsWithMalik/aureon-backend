package com.Accounting.app.plaid.dto;

import java.util.List;

public class PublicTokenExchangeRequest {
    
    private String email;
    private String publicToken;
    private String institutionId;
    private String institutionName;
    private List<String> requestedCapabilities;
    private List<String> requestedProducts;
    private List<String> requestedDataScopes;

    public String getEmail() {
        return email;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public List<String> getRequestedCapabilities() {
        return requestedCapabilities;
    }

    public List<String> getRequestedProducts() {
        return requestedProducts;
    }

    public List<String> getRequestedDataScopes() {
        return requestedDataScopes;
    }
}

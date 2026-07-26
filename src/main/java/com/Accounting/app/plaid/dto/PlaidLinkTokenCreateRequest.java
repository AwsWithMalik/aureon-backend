package com.Accounting.app.plaid.dto;

import java.util.List;

public class PlaidLinkTokenCreateRequest {
    private List<String> requestedCapabilities;
    private List<String> requestedProducts;
    private List<String> requestedDataScopes;

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

package com.Accounting.app.files.dto;

import java.net.URI;

public class UploadedFileContent {
    private final URI redirectUri;
    private final String fileName;
    private final String contentType;

    public UploadedFileContent(URI redirectUri, String fileName, String contentType) {
        this.redirectUri = redirectUri;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public URI getRedirectUri() {
        return redirectUri;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }
}

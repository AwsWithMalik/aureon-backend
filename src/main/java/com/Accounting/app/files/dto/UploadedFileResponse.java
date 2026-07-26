package com.Accounting.app.files.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadedFileResponse {
    private String fileName;
    private String filePath;
    private String contentType;
    private Long fileSize;
}
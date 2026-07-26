package com.Accounting.app.settings.dto;

import java.net.URI;

public record ProfilePhotoContent(URI redirectUri, String fileName, String contentType) {
}

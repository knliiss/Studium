package dev.knalis.file.service.storage;

import java.io.InputStream;

public record FileStorageObject(
        InputStream stream,
        String contentType,
        long sizeBytes
) {
}

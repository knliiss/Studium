package dev.knalis.education.client.file;

import dev.knalis.education.client.file.dto.RemoteStoredFileResponse;

import java.util.UUID;

public interface FileServiceClient {

    RemoteStoredFileResponse getMyFile(String bearerToken, UUID fileId);

    void markFileActive(String bearerToken, UUID fileId);
}


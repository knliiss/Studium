package dev.knalis.assignment.client;

import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;

import java.util.UUID;

public interface FileServiceClient {
    
    RemoteStoredFileResponse getMyFile(String bearerToken, UUID fileId);
    
    void markFileActive(String bearerToken, UUID fileId);
}

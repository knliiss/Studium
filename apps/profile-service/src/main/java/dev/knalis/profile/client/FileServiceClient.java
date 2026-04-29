package dev.knalis.profile.client;

import dev.knalis.profile.client.dto.RemoteStoredFileResponse;

import java.util.UUID;

public interface FileServiceClient {
    
    RemoteStoredFileResponse getMyFile(String bearerToken, UUID fileId);
    
    void markFileActive(String bearerToken, UUID fileId);
    
    void markFileOrphaned(String bearerToken, UUID fileId);
}

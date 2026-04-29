package dev.knalis.profile.client.http;

import dev.knalis.profile.client.FileServiceClient;
import dev.knalis.profile.client.dto.RemoteStoredFileResponse;
import dev.knalis.profile.exception.InvalidAvatarFileException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HttpFileServiceClient implements FileServiceClient {
    
    private final RestClient fileServiceRestClient;
    
    @Override
    public RemoteStoredFileResponse getMyFile(String bearerToken, UUID fileId) {
        try {
            return fileServiceRestClient.get()
                    .uri("/api/files/{fileId}", fileId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(RemoteStoredFileResponse.class);
        } catch (RestClientResponseException exception) {
            throw new InvalidAvatarFileException(fileId, "Avatar file is not accessible for the current user");
        }
    }
    
    @Override
    public void markFileActive(String bearerToken, UUID fileId) {
        invokeLifecycleEndpoint(bearerToken, fileId, "active");
    }
    
    @Override
    public void markFileOrphaned(String bearerToken, UUID fileId) {
        invokeLifecycleEndpoint(bearerToken, fileId, "orphaned");
    }
    
    private void invokeLifecycleEndpoint(String bearerToken, UUID fileId, String state) {
        try {
            fileServiceRestClient.put()
                    .uri("/api/files/{fileId}/lifecycle/{state}", fileId, state)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new InvalidAvatarFileException(fileId, "Avatar file lifecycle transition failed");
        }
    }
}

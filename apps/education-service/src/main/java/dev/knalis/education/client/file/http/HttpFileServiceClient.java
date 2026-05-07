package dev.knalis.education.client.file.http;

import dev.knalis.education.client.file.FileServiceClient;
import dev.knalis.education.client.file.dto.RemoteStoredFileResponse;
import dev.knalis.education.exception.FileAttachmentNotAllowedException;
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
            throw new FileAttachmentNotAllowedException(fileId);
        }
    }

    @Override
    public void markFileActive(String bearerToken, UUID fileId) {
        try {
            fileServiceRestClient.put()
                    .uri("/api/files/{fileId}/lifecycle/active", fileId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new FileAttachmentNotAllowedException(fileId);
        }
    }
}


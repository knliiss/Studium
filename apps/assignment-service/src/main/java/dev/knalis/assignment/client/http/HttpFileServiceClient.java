package dev.knalis.assignment.client.http;

import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.exception.FileServiceUnavailableException;
import dev.knalis.assignment.exception.InvalidSubmissionFileException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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
            if (exception.getStatusCode().is5xxServerError()) {
                throw new FileServiceUnavailableException("metadata", fileId);
            }
            throw new InvalidSubmissionFileException(fileId, "Submission file is not accessible for the current user");
        } catch (RestClientException exception) {
            throw new FileServiceUnavailableException("metadata", fileId);
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
            if (exception.getStatusCode().is5xxServerError()) {
                throw new FileServiceUnavailableException("activate", fileId);
            }
            throw new InvalidSubmissionFileException(fileId, "Submission file lifecycle transition failed");
        } catch (RestClientException exception) {
            throw new FileServiceUnavailableException("activate", fileId);
        }
    }
}

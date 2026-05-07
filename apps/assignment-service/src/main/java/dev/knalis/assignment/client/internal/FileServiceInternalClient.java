package dev.knalis.assignment.client.internal;

import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.config.AssignmentFileServiceProperties;
import dev.knalis.assignment.exception.FileAccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FileServiceInternalClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient fileServiceRestClient;
    private final AssignmentFileServiceProperties properties;

    public RemoteStoredFileResponse getMetadata(UUID fileId) {
        try {
            return fileServiceRestClient.get()
                    .uri("/internal/files/{fileId}/metadata", fileId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .body(RemoteStoredFileResponse.class);
        } catch (RestClientResponseException exception) {
            throw new FileAccessDeniedException();
        }
    }

    public ResponseEntity<byte[]> download(UUID fileId, boolean preview) {
        String path = preview ? "/internal/files/{fileId}/preview" : "/internal/files/{fileId}/download";
        try {
            ResponseEntity<byte[]> response = fileServiceRestClient.get()
                    .uri(path, fileId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .toEntity(byte[].class);
            HttpHeaders headers = response.getHeaders();
            return ResponseEntity.status(response.getStatusCode())
                    .headers(headers)
                    .body(response.getBody());
        } catch (RestClientResponseException exception) {
            throw new FileAccessDeniedException();
        }
    }
}

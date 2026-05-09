package dev.knalis.education.client.file.internal;

import dev.knalis.education.client.file.dto.RemoteStoredFileResponse;
import dev.knalis.education.config.EducationFileServiceProperties;
import dev.knalis.education.exception.FileServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileServiceInternalClientTest {

    @Test
    void metadataConnectionFailureIsMappedToControlledException() {
        UUID fileId = UUID.randomUUID();
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec headersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        EducationFileServiceProperties properties = properties("internal-secret");
        when(restClient.get()).thenReturn(headersUriSpec);
        when(headersUriSpec.uri("/internal/files/{fileId}/metadata", fileId)).thenReturn(headersSpec);
        when(headersSpec.header("X-Internal-Secret", "internal-secret")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(RemoteStoredFileResponse.class)).thenThrow(new ResourceAccessException("Connection refused"));
        FileServiceInternalClient client = new FileServiceInternalClient(restClient, properties);

        assertThrows(FileServiceUnavailableException.class, () -> client.getMetadata(fileId));
    }

    @Test
    void downloadConnectionFailureIsMappedToControlledException() {
        UUID fileId = UUID.randomUUID();
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec headersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        EducationFileServiceProperties properties = properties("internal-secret");
        when(restClient.get()).thenReturn(headersUriSpec);
        when(headersUriSpec.uri("/internal/files/{fileId}/download", fileId)).thenReturn(headersSpec);
        when(headersSpec.header("X-Internal-Secret", "internal-secret")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(byte[].class)).thenThrow(new ResourceAccessException("Connection refused"));
        FileServiceInternalClient client = new FileServiceInternalClient(restClient, properties);

        assertThrows(FileServiceUnavailableException.class, () -> client.download(fileId, false));
    }

    @Test
    void previewConnectionFailureIsMappedToControlledException() {
        UUID fileId = UUID.randomUUID();
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec headersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        EducationFileServiceProperties properties = properties("internal-secret");
        when(restClient.get()).thenReturn(headersUriSpec);
        when(headersUriSpec.uri("/internal/files/{fileId}/preview", fileId)).thenReturn(headersSpec);
        when(headersSpec.header("X-Internal-Secret", "internal-secret")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(byte[].class)).thenThrow(new ResourceAccessException("Connection refused"));
        FileServiceInternalClient client = new FileServiceInternalClient(restClient, properties);

        assertThrows(FileServiceUnavailableException.class, () -> client.download(fileId, true));
    }

    private EducationFileServiceProperties properties(String sharedSecret) {
        EducationFileServiceProperties properties = new EducationFileServiceProperties();
        properties.setBaseUrl("http://localhost:8083");
        properties.setSharedSecret(sharedSecret);
        return properties;
    }
}

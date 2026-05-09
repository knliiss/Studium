package dev.knalis.assignment.client.http;

import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.exception.FileServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpFileServiceClientTest {

    @Test
    void metadataConnectionFailureIsMappedToControlledException() {
        UUID fileId = UUID.randomUUID();
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec headersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(headersUriSpec);
        when(headersUriSpec.uri("/api/files/{fileId}", fileId)).thenReturn(headersSpec);
        when(headersSpec.header("Authorization", "Bearer token")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(RemoteStoredFileResponse.class))
                .thenThrow(new ResourceAccessException("Connection refused"));
        HttpFileServiceClient client = new HttpFileServiceClient(restClient);

        assertThrows(FileServiceUnavailableException.class, () -> client.getMyFile("token", fileId));
    }

    @Test
    void activateConnectionFailureIsMappedToControlledException() {
        UUID fileId = UUID.randomUUID();
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.put()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri("/api/files/{fileId}/lifecycle/active", fileId)).thenReturn(bodySpec);
        when(bodySpec.header("Authorization", "Bearer token")).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenThrow(new ResourceAccessException("Connection refused"));
        HttpFileServiceClient client = new HttpFileServiceClient(restClient);

        assertThrows(FileServiceUnavailableException.class, () -> client.markFileActive("token", fileId));
    }
}

package com.marlabs.desk.engine;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.marlabs.desk.api.dto.AnswerResponse;
import com.marlabs.desk.api.dto.DocumentResult;
import com.marlabs.desk.error.EngineException;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the python service: bounded timeouts, exactly one attempt, no automatic
 * retries. Dependency failures are translated into stable codes with a curated message; the raw
 * response body is never echoed to the caller, and anything unrecognised falls back to a generic
 * message.
 */
@Component
public class HttpPolicyEngineClient implements PolicyEngineClient {

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public HttpPolicyEngineClient(PolicyEngineProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public AnswerResponse answer(String tenant, String role, LocalDate asOf, String question) {
        Map<String, Object> body = Map.of(
                "tenant", tenant, "role", role, "as_of", asOf.toString(), "question", question);
        return call("/answer", spec -> spec.contentType(MediaType.APPLICATION_JSON).body(body),
                AnswerResponse.class);
    }

    @Override
    public DocumentResult process(String tenant, String role, LocalDate asOf, String batchId,
                                  String documentId, String filename, byte[] content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("tenant", tenant);
        form.add("role", role);
        form.add("as_of", asOf.toString());
        form.add("batch_id", batchId);
        form.add("document_id", documentId);
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return call("/documents/process",
                spec -> spec.contentType(MediaType.MULTIPART_FORM_DATA).body(form),
                DocumentResult.class);
    }

    @FunctionalInterface
    private interface BodyWriter {
        RestClient.RequestHeadersSpec<?> apply(RestClient.RequestBodySpec spec);
    }

    private <T> T call(String path, BodyWriter writer, Class<T> type) {
        try {
            return writer.apply(restClient.post().uri(path))
                    .exchange((request, response) -> {
                        byte[] raw = response.getBody().readAllBytes();
                        if (response.getStatusCode().isError()) {
                            throw toEngineException(response.getStatusCode().value(), raw);
                        }
                        return mapper.readValue(raw, type);
                    });
        } catch (EngineException e) {
            throw e;
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new EngineException("ENGINE_TIMEOUT", "Policy engine timed out.", 504);
            }
            throw new EngineException("ENGINE_UNAVAILABLE", "Policy engine is unavailable.", 503);
        } catch (Exception e) {
            throw new EngineException("ENGINE_INVALID_RESPONSE",
                    "Policy engine returned an unreadable response.", 502);
        }
    }

    private EngineException toEngineException(int status, byte[] raw) {
        String code = "ENGINE_ERROR";
        String message = "Policy engine call failed.";
        try {
            JsonNode node = mapper.readTree(raw);
            if (node.hasNonNull("error_code")) {
                code = node.get("error_code").asText();
            }
            if (node.hasNonNull("message")) {
                message = node.get("message").asText();
            }
        } catch (IOException ignored) {
            // Unparseable body: keep the generic message rather than echoing the response.
        }
        int publicStatus = switch (code) {
            case "MODEL_TIMEOUT" -> 504;
            case "MODEL_UNAVAILABLE" -> 503;
            case "EMPTY_DOCUMENT", "UNREADABLE_DOCUMENT", "DOCUMENT_TOO_LARGE" -> 422;
            default -> status >= 500 ? 502 : 400;
        };
        return new EngineException(code, message, publicStatus);
    }
}

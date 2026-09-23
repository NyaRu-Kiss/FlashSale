package com.flashsale.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Servlet gateway proxy used for the local deployment topology. */
@RestController
final class GatewayProxyController {
    private static final Set<String> HOP_BY_HOP = Set.of("connection", "host", "content-length", "transfer-encoding");
    private final RestClient client;
    private final UpstreamResolver upstreams;

    GatewayProxyController(RestClient.Builder builder, UpstreamResolver upstreams) {
        this.client = builder.build();
        this.upstreams = upstreams;
    }

    @RequestMapping("/api/v1/**")
    ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String base = upstreams.baseFor(request.getRequestURI());
        if (base == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        URI target = UriComponentsBuilder.fromUriString(base)
                .path(request.getRequestURI())
                .query(request.getQueryString())
                .build(true)
                .toUri();
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        RestClient.RequestBodySpec call = client.method(HttpMethod.valueOf(request.getMethod())).uri(target);
        call.headers(headers -> copyRequestHeaders(request, headers));
        if (body.length > 0) call.body(body);

        return call.exchange((clientRequest, clientResponse) -> {
            HttpHeaders headers = new HttpHeaders();
            clientResponse.getHeaders().forEach((name, values) -> {
                if (!HOP_BY_HOP.contains(name.toLowerCase())) headers.put(name, values);
            });
            return new ResponseEntity<>(StreamUtils.copyToByteArray(clientResponse.getBody()), headers, clientResponse.getStatusCode());
        });
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> names = request.getHeaderNames();
        for (String name : Collections.list(names)) {
            if (!HOP_BY_HOP.contains(name.toLowerCase())) headers.put(name, Collections.list(request.getHeaders(name)));
        }
    }
}

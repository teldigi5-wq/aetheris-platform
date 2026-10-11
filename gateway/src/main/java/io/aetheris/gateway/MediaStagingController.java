package io.aetheris.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.UUID;

/**
 * Gateway-local signed-owner staging, NOT a media generation endpoint.
 * The WebFlux WebFilter validates every request before this controller runs.
 */
@RestController
@RequestMapping("/api/media")
public final class MediaStagingController {
    private static final Set<String> ALLOWED_IMAGE_FIELDS = Set.of("kind", "prompt", "negativePrompt", "width", "height");
    private final StagedMediaJobService staging;

    public MediaStagingController(StagedMediaJobService staging) {
        this.staging = staging;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities(ServerWebExchange exchange) {
        verifiedOwner(exchange);
        return Map.of(
                "schemaVersion", 1,
                "stagingEnabled", staging.isEnabled(),
                "stagingState", staging.isEnabled() ? "LOCAL_ONLY_NO_EXECUTION" : "DISABLED_BY_DEFAULT",
                "imageGenerationState", "NOT_WIRED",
                "videoGenerationState", "NOT_WIRED",
                "gpuJobStarted", false,
                "cloudFallback", false,
                "paidProviders", false,
                "physicalPcStatus", "BLOCKED_PENDING_HARDWARE");
    }

    @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StagedMediaJobService.JobView> stage(
            ServerWebExchange exchange,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode request) {
        long owner = verifiedOwner(exchange);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(staging.submit(owner, idempotencyKey, strictImageRequest(request)));
    }

    @GetMapping("/jobs/{id}")
    public StagedMediaJobService.JobView job(ServerWebExchange exchange, @PathVariable String id) {
        return staging.get(verifiedOwner(exchange), parseId(id));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> discard(ServerWebExchange exchange, @PathVariable String id) {
        staging.remove(verifiedOwner(exchange), parseId(id));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    /**
     * Explicit allowlist: do not silently discard new client fields like model,
     * workflow, remote URL, path, shell arguments or payment instructions.
     */
    private static StagedMediaJobService.MediaRequest strictImageRequest(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media request must be a JSON object");
        }
        Iterator<String> keys = body.fieldNames();
        while (keys.hasNext()) {
            if (!ALLOWED_IMAGE_FIELDS.contains(keys.next())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unrecognized media request field");
            }
        }
        JsonNode kind = body.get("kind");
        JsonNode prompt = body.get("prompt");
        JsonNode negative = body.get("negativePrompt");
        JsonNode width = body.get("width");
        JsonNode height = body.get("height");
        if (kind == null || !kind.isTextual()
                || prompt == null || !prompt.isTextual()
                || negative != null && !negative.isTextual()
                || width == null || !width.isIntegralNumber() || !width.canConvertToInt()
                || height == null || !height.isIntegralNumber() || !height.canConvertToInt()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media request field types invalid");
        }
        return new StagedMediaJobService.MediaRequest(
                kind.asText(), prompt.asText(), negative == null ? "" : negative.asText(),
                width.intValue(), height.intValue());
    }

    private static long verifiedOwner(ServerWebExchange exchange) {
        Object verified = exchange.getAttribute(MediaJobAuthWebFilter.VERIFIED_OWNER_ATTRIBUTE);
        if (!(verified instanceof Long uid) || uid <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified media identity required");
        }
        return uid;
    }

    private static UUID parseId(String id) {
        try {
            if (id == null || id.length() != 36) throw new IllegalArgumentException();
            UUID uuid = UUID.fromString(id);
            if (!uuid.toString().equals(id)) throw new IllegalArgumentException();
            return uuid;
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media job identifier");
        }
    }
}

package io.aetheris.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Short-lived private staging ONLY. This does not call the runtime, GPU,
 * ComfyUI, storage, webhooks, shell, or paid/cloud API.
 */
@Service
public final class StagedMediaJobService {
    private static final int PER_OWNER_LIMIT = 8;
    private static final int GLOBAL_LIMIT = 256;
    private static final Duration EXPIRY = Duration.ofMinutes(10);

    public record MediaRequest(String kind, String prompt, String negativePrompt,
                               Integer width, Integer height) {}
    public record JobView(UUID id, String state, String kind, int width, int height,
                          Instant createdAt, String physicalPcStatus, boolean gpuJobStarted) {}
    private record StoredJob(long ownerUid, JobView view, String fingerprint, String dedupeKey) {}

    private final boolean enabled;
    private final Clock clock;
    private final byte[] fingerprintSalt = new byte[32];
    private final Map<UUID, StoredJob> jobs = new HashMap<>();
    private final Map<String, UUID> dedupeIndex = new HashMap<>();

    @Autowired
    public StagedMediaJobService(@Value("${aetheris.media.staging-enabled:false}") boolean enabled) {
        this(enabled, Clock.systemUTC());
    }

    StagedMediaJobService(boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.clock = clock;
        new SecureRandom().nextBytes(fingerprintSalt);
    }

    boolean isEnabled() {
        return enabled;
    }

    synchronized JobView submit(long verifiedUid, String idempotencyKey, MediaRequest request) {
        requireEnabled();
        requireOwner(verifiedUid);
        if (idempotencyKey == null || idempotencyKey.length() != 36
                || !isCanonicalUuid(idempotencyKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canonical Idempotency-Key UUID required");
        }
        validate(request);
        purgeExpired();
        String dedupeKey = verifiedUid + ":" + idempotencyKey;
        String fingerprint = fingerprint(request);
        UUID existingId = dedupeIndex.get(dedupeKey);
        if (existingId != null) {
            StoredJob existing = jobs.get(existingId);
            if (existing != null) {
                if (Objects.equals(existing.fingerprint(), fingerprint)) return existing.view();
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with different payload");
            }
        }
        if (jobs.size() >= GLOBAL_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Media staging global limit reached");
        }
        long owned = jobs.values().stream().filter(x -> x.ownerUid() == verifiedUid).count();
        if (owned >= PER_OWNER_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Media staging owner limit reached");
        }
        UUID id = UUID.randomUUID();
        JobView view = new JobView(id, "STAGED_NOT_EXECUTED", request.kind(),
                request.width(), request.height(), clock.instant(), "BLOCKED_PENDING_HARDWARE", false);
        jobs.put(id, new StoredJob(verifiedUid, view, fingerprint, dedupeKey));
        dedupeIndex.put(dedupeKey, id);
        return view;
    }

    synchronized JobView get(long verifiedUid, UUID id) {
        requireEnabled();
        requireOwner(verifiedUid);
        purgeExpired();
        StoredJob found = jobs.get(id);
        if (found == null || found.ownerUid() != verifiedUid) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media staging job not found");
        }
        return found.view();
    }

    synchronized void remove(long verifiedUid, UUID id) {
        requireEnabled();
        requireOwner(verifiedUid);
        purgeExpired();
        StoredJob found = jobs.get(id);
        if (found == null || found.ownerUid() != verifiedUid) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media staging job not found");
        }
        jobs.remove(id);
        dedupeIndex.remove(found.dedupeKey());
    }

    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Media staging disabled by default; hardware unverified");
    }

    private static void requireOwner(long owner) {
        if (owner <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified media identity required");
    }

    private static boolean isCanonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static void validate(MediaRequest request) {
        if (request == null || !"IMAGE".equals(request.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image staging is supported");
        }
        String prompt = request.prompt();
        String negative = request.negativePrompt();
        if (prompt == null || prompt.isBlank() || prompt.length() > 1500
                || negative != null && negative.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid prompt length");
        }
        if (request.width() == null || request.height() == null
                || !(request.width() == 512 && (request.height() == 512 || request.height() == 768)
                  || request.width() == 768 && request.height() == 512)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported media staging dimensions");
        }
    }

    private String fingerprint(MediaRequest request) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(request.kind());
            out.writeInt(request.width());
            out.writeInt(request.height());
            out.writeUTF(request.prompt());
            out.writeUTF(request.negativePrompt() == null ? "" : request.negativePrompt());
            out.flush();
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(fingerprintSalt);
            return Base64.getEncoder().encodeToString(hash.digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException unexpected) {
            throw new IllegalStateException("Media idempotency fingerprint unavailable", unexpected);
        }
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<UUID, StoredJob>> iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            StoredJob entry = iterator.next().getValue();
            if (!entry.view().createdAt().plus(EXPIRY).isAfter(now)) {
                dedupeIndex.remove(entry.dedupeKey());
                iterator.remove();
            }
        }
    }
}

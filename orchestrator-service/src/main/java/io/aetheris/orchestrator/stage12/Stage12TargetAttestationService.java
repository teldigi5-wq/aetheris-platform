package io.aetheris.orchestrator.stage12;

import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceEntity;
import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class Stage12TargetAttestationService {
    private final Stage11RuntimeEvidenceService stage11Evidence;

    public Stage12TargetAttestationService(Stage11RuntimeEvidenceService stage11Evidence) {
        this.stage11Evidence = stage11Evidence;
    }

    public TargetAttestation assess(String kind) {
        String requested = require(kind).toUpperCase(Locale.ROOT);
        List<Stage11RuntimeEvidenceEntity> matching = stage11Evidence.recent().stream()
                .filter(e -> requested.equals(e.getKind()))
                .toList();
        Optional<Stage11RuntimeEvidenceEntity> target = matching.stream()
                .filter(Stage11RuntimeEvidenceEntity::isTargetMeasured)
                .filter(e -> e.getAttestationSha256() != null && e.getAttestationSha256().matches("[a-f0-9]{64}"))
                .filter(e -> e.getStatus().startsWith("TARGET_REPORTED:"))
                .findFirst();
        if (target.isEmpty()) {
            return new TargetAttestation("EVIDENCE_REQUIRED", requested, false, null, null,
                    "Stage 12 cannot promote CI/configuration evidence into target proof");
        }
        Stage11RuntimeEvidenceEntity e = target.get();
        return new TargetAttestation("TARGET_ATTESTATION_AVAILABLE", requested, true,
                e.getAttestationSha256(), e.getObservedAt(),
                "Target-reported Stage 11 evidence is available; downstream gates must still verify their own domain requirements");
    }

    private String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("attestation kind is required");
        String v = value.trim();
        if (!v.matches("[A-Za-z0-9._:-]{1,32}")) throw new IllegalArgumentException("attestation kind is invalid");
        return v;
    }

    public record TargetAttestation(String status, String kind, boolean targetMeasured,
                                    String attestationSha256, Instant observedAt, String detail) {}
}

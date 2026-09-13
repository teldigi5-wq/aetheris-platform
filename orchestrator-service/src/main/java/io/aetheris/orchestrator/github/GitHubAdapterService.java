package io.aetheris.orchestrator.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class GitHubAdapterService {

    private final GitHubChangeProposalRepository proposals;
    private final RestClient client;
    private final String token;
    private final Set<String> allowedRepositories;

    public GitHubAdapterService(
            GitHubChangeProposalRepository proposals,
            RestClient.Builder builder,
            @Value("${aetheris.execution.github.token:}") String token,
            @Value("${aetheris.execution.github.allowed-repositories:}") String allowedRepositories) {
        this.proposals = proposals;
        this.client = builder.baseUrl("https://api.github.com").build();
        this.token = token == null ? "" : token.trim();
        this.allowedRepositories = parseCsv(allowedRepositories);
    }

    @SuppressWarnings("unchecked")
    public GitHubFileReadResult readFile(String repository, String path, String ref) {
        String normalizedRepo = requireAllowed(repository);
        String[] parts = normalizedRepo.split("/", 2);
        if (path == null || path.isBlank()) throw new IllegalArgumentException("GitHub path is required");
        String effectiveRef = ref == null || ref.isBlank() ? "main" : ref.trim();

        RestClient.RequestHeadersSpec<?> request = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/contents/{path}")
                        .queryParam("ref", effectiveRef)
                        .build(parts[0], parts[1], path));
        request.header(HttpHeaders.ACCEPT, "application/vnd.github+json");
        request.header(HttpHeaders.USER_AGENT, "aetheris-platform");
        if (!token.isBlank()) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        Map<String, Object> body = request.retrieve().body(Map.class);
        if (body == null || body.get("content") == null) throw new IllegalStateException("GitHub returned no file content");

        String encoded = String.valueOf(body.get("content"));
        String content = new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        return new GitHubFileReadResult(
                normalizedRepo,
                path,
                effectiveRef,
                String.valueOf(body.getOrDefault("sha", "")),
                content,
                body.get("html_url") == null ? null : String.valueOf(body.get("html_url")));
    }

    public GitHubChangeProposalEntity propose(UUID taskId, String agentId, String repository, String path, String baseRef, String content, String summary) {
        String normalizedRepo = requireAllowed(repository);
        if (path == null || path.isBlank()) throw new IllegalArgumentException("GitHub proposal path is required");
        String effectiveRef = baseRef == null || baseRef.isBlank() ? "main" : baseRef.trim();
        String effectiveSummary = summary == null || summary.isBlank() ? "Aetheris proposed change" : summary.trim();
        return proposals.save(new GitHubChangeProposalEntity(
                UUID.randomUUID(), taskId, agentId, normalizedRepo, path.trim(), effectiveRef,
                content == null ? "" : content, effectiveSummary));
    }

    public List<GitHubChangeProposalEntity> recentProposals() {
        return proposals.findTop100ByOrderByCreatedAtDesc();
    }

    private String requireAllowed(String repository) {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("GitHub repository must be in owner/name format");
        }
        String normalized = repository.toLowerCase(Locale.ROOT);
        if (allowedRepositories.isEmpty() || !allowedRepositories.contains(normalized)) {
            throw new IllegalArgumentException("GitHub repository is not allowlisted: " + repository);
        }
        return normalized;
    }

    private Set<String> parseCsv(String csv) {
        Set<String> values = new HashSet<>();
        if (csv == null) return Set.of();
        for (String value : csv.split(",")) {
            if (!value.isBlank()) values.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(values);
    }
}

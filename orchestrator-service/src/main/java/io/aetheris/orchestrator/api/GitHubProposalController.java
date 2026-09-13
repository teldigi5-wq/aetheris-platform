package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.github.GitHubAdapterService;
import io.aetheris.orchestrator.github.GitHubChangeProposalEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orchestrator/github/proposals")
public class GitHubProposalController {

    private final GitHubAdapterService github;

    public GitHubProposalController(GitHubAdapterService github) {
        this.github = github;
    }

    @GetMapping
    public List<GitHubChangeProposalEntity> recent() {
        return github.recentProposals();
    }
}

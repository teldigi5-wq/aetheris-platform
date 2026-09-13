package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.AgentCatalogProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentCatalogProperties.class)
public class AetherisOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherisOrchestratorApplication.class, args);
    }
}

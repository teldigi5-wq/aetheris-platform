FROM eclipse-temurin:21.0.12_8-jdk-noble@sha256:5dd9dbc6f6eccb82184b340da9488b70b68d2af81474cf8f280f8ce6b2933282 AS build
# CVE-2026-114820: remove all four Oracle-advised eMudhra/cross-sign trust anchors.
# This is defense in depth for the pinned vendor runtime, not a Java CPU upgrade.
RUN set -eu; \
    keytool -list -cacerts -storepass changeit >/dev/null; \
    for alias in 'emsignrootcag1 [jdk]' 'emsignrootcag2 [jdk]' 'emsigneccrootcag3 [jdk]' 'comodoaaaca [jdk]'; do \
      if keytool -list -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1; then \
        keytool -delete -cacerts -storepass changeit -alias "$alias"; \
      fi; \
      if keytool -list -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1; then \
        echo "Untrusted certificate anchor remains: $alias" >&2; exit 1; \
      fi; \
    done; \
    certs="$(keytool -list -v -cacerts -storepass changeit)"; \
    if printf '%s\n' "$certs" | tr -d ':' | grep -Eqi '86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B|D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4'; then \
      echo 'Compromised-chain trust anchor fingerprint persists' >&2; exit 1; \
    fi
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA512=bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978
RUN curl --fail --silent --show-error --location \
      "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      --output /tmp/apache-maven.tar.gz \
 && echo "${MAVEN_SHA512}  /tmp/apache-maven.tar.gz" | sha512sum --check --strict \
 && tar -xzf /tmp/apache-maven.tar.gz -C /opt \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
 && rm /tmp/apache-maven.tar.gz
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21.0.12_8-jre-noble@sha256:86883d2dc1d0e57d4fb2c539f5fd3a2155749c0bdcdccb9cb452828bdc8b0caf
# CVE-2026-114820: remove all four Oracle-advised eMudhra/cross-sign trust anchors.
# This is defense in depth for the pinned vendor runtime, not a Java CPU upgrade.
RUN set -eu; \
    keytool -list -cacerts -storepass changeit >/dev/null; \
    for alias in 'emsignrootcag1 [jdk]' 'emsignrootcag2 [jdk]' 'emsigneccrootcag3 [jdk]' 'comodoaaaca [jdk]'; do \
      if keytool -list -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1; then \
        keytool -delete -cacerts -storepass changeit -alias "$alias"; \
      fi; \
      if keytool -list -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1; then \
        echo "Untrusted certificate anchor remains: $alias" >&2; exit 1; \
      fi; \
    done; \
    certs="$(keytool -list -v -cacerts -storepass changeit)"; \
    if printf '%s\n' "$certs" | tr -d ':' | grep -Eqi '86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B|D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4'; then \
      echo 'Compromised-chain trust anchor fingerprint persists' >&2; exit 1; \
    fi
WORKDIR /app
RUN groupadd --system --gid 10001 aetheris \
 && useradd --system --uid 10001 --gid aetheris --create-home --home-dir /home/aetheris --shell /usr/sbin/nologin aetheris
COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]

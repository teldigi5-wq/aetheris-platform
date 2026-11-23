FROM eclipse-temurin:21.0.13_9-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d AS build
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA512=bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978
# Maven Central rate-limits shared CI runners (HTTP 429). Try the Google Cloud Storage
# copy of Central first, then Central, then the Apache archive; the pinned SHA-512 below
# is the only integrity authority, whichever host served the file.
RUN for url in \
      "https://maven-central.storage-download.googleapis.com/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"; do \
      curl --fail --silent --show-error --location --retry 5 --retry-delay 10 --retry-all-errors \
        "$url" --output /tmp/apache-maven.tar.gz && break; \
      rm -f /tmp/apache-maven.tar.gz; \
    done \
 && echo "${MAVEN_SHA512}  /tmp/apache-maven.tar.gz" | sha512sum --check --strict \
 && tar -xzf /tmp/apache-maven.tar.gz -C /opt \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
 && rm /tmp/apache-maven.tar.gz
# Dependencies: the GCS copy of Maven Central is tried first and Maven Central stays second,
# so a mirror outage or sync lag falls back automatically. --strict-checksums (MAVEN_ARGS)
# verifies every artifact from either host.
RUN mkdir -p /root/.m2 && printf '%s\n' \
      '<settings>' \
      ' <profiles><profile><id>gcs-central-first</id>' \
      '  <repositories><repository><id>gcs-maven-central</id>' \
      '   <url>https://maven-central.storage-download.googleapis.com/maven2/</url>' \
      '   <releases><enabled>true</enabled></releases><snapshots><enabled>false</enabled></snapshots>' \
      '  </repository></repositories>' \
      '  <pluginRepositories><pluginRepository><id>gcs-maven-central</id>' \
      '   <url>https://maven-central.storage-download.googleapis.com/maven2/</url>' \
      '   <releases><enabled>true</enabled></releases><snapshots><enabled>false</enabled></snapshots>' \
      '  </pluginRepository></pluginRepositories>' \
      ' </profile></profiles>' \
      ' <activeProfiles><activeProfile>gcs-central-first</activeProfile></activeProfiles>' \
      '</settings>' > /root/.m2/settings.xml
# Maven 3.9 reads MAVEN_ARGS: retry dependency downloads when Central rate-limits (429/503).
ENV MAVEN_ARGS="--strict-checksums -Daether.connector.http.retryHandler.count=5 -Daether.connector.http.retryHandler.serviceUnavailable=429,503"
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21.0.13_9-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f
WORKDIR /app
# Fixed Ubuntu Noble packages only, exact versions, no blanket upgrade, all found by the Java image
# Trivy gate (java-trust-runtime-proof): apt/libapt-pkg (CVE-2026-109518, HIGH); glibc libc6/libc-bin/
# locales, libpcre2-8-0 and perl-base (MEDIUM findings with published fixes, 14 Nov 2026 triage).
RUN apt-get update \
 && apt-get install -y --no-install-recommends --only-upgrade 'apt=2.8.4ubuntu0.1' 'libapt-pkg6.0t64=2.8.4ubuntu0.1' \
    'libc6=2.39-0ubuntu8.12' 'libc-bin=2.39-0ubuntu8.12' 'locales=2.39-0ubuntu8.12' \
    'libpcre2-8-0=10.42-4ubuntu2.2' 'perl-base=5.38.2-3.2ubuntu0.7' \
 && rm -rf /var/lib/apt/lists/*
RUN groupadd --system --gid 10001 aetheris \
 && useradd --system --uid 10001 --gid aetheris --create-home --home-dir /home/aetheris --shell /usr/sbin/nologin aetheris
COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java","-jar","app.jar"]

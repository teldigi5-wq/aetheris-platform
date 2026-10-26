FROM docker.io/library/amazoncorretto:21.0.13-al2023@sha256:1a307926702559b0086a5c4d339eaafa9c992b3e8ba54201ac8ebbde31d22f0b AS build
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA512=bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978
# Amazon Linux 2023 Corretto images omit tar; install only the required build tool.
RUN dnf -y install tar gzip && dnf clean all
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

FROM docker.io/library/amazoncorretto:21.0.13-al2023-headless@sha256:212faf5f8f93d8f4854a3d89528834d11ad2d685de80721d4d24ac2fdd3e379e
WORKDIR /app
# Preserve the original named unprivileged account on minimal Amazon Linux 2023.
# Apply current signed AL2023 repository security fixes before runtime account creation; re-scan the complete image.
RUN dnf -y upgrade && dnf -y install shadow-utils && dnf clean all
RUN groupadd --system --gid 10001 aetheris \
 && useradd --system --uid 10001 --gid aetheris --create-home --home-dir /home/aetheris --shell /usr/sbin/nologin aetheris
COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]

FROM eclipse-temurin:17-jdk-jammy AS build-jdk
FROM docker:29.8.2-cli AS docker-cli
FROM registry.k8s.io/kubectl:v1.36.1 AS kubectl-cli

FROM jenkins/inbound-agent:jdk21

# Jenkins Remoting uses Java 21; Gradle and the application use Java 17.
ENV JENKINS_JAVA_BIN="${JAVA_HOME}/bin/java"
COPY --from=build-jdk /opt/java/openjdk /opt/java/jdk17
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/libexec/docker/cli-plugins/docker-compose
COPY --from=kubectl-cli /bin/kubectl /usr/local/bin/kubectl
ENV JAVA_HOME=/opt/java/jdk17
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /home/jenkins/agent

FROM eclipse-temurin:17-jdk-jammy AS build-jdk

FROM jenkins/inbound-agent:jdk21

# Jenkins Remoting uses Java 21; Gradle and the application use Java 17.
ENV JENKINS_JAVA_BIN="${JAVA_HOME}/bin/java"
COPY --from=build-jdk /opt/java/openjdk /opt/java/jdk17
ENV JAVA_HOME=/opt/java/jdk17
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /home/jenkins/agent

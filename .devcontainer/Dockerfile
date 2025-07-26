FROM mcr.microsoft.com/devcontainers/java:1-21-bullseye

# Install additional tools for Minecraft development
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
        curl \
        wget \
        unzip \
        git \
        vim \
        nano \
        htop \
        tree \
        jq \
    && apt-get autoremove -y && apt-get clean -y && rm -rf /var/lib/apt/lists/*

# Install latest Git (for better patch support)
RUN add-apt-repository ppa:git-core/ppa -y \
    && apt-get update \
    && apt-get install git -y

# Set up Gradle daemon properties for better performance
RUN mkdir -p /home/vscode/.gradle \
    && echo "org.gradle.daemon=true" >> /home/vscode/.gradle/gradle.properties \
    && echo "org.gradle.parallel=true" >> /home/vscode/.gradle/gradle.properties \
    && echo "org.gradle.caching=true" >> /home/vscode/.gradle/gradle.properties \
    && echo "org.gradle.configureondemand=true" >> /home/vscode/.gradle/gradle.properties \
    && echo "org.gradle.jvmargs=-Xmx4g -XX:+UseG1GC -XX:+UseStringDeduplication" >> /home/vscode/.gradle/gradle.properties \
    && chown -R vscode:vscode /home/vscode/.gradle

# Install useful development tools
RUN npm install -g http-server

# Create workspace directory
RUN mkdir -p /workspaces && chown vscode:vscode /workspaces

USER vscode

# Pre-download Gradle wrapper (common version)
RUN mkdir -p /tmp/gradle-test \
    && cd /tmp/gradle-test \
    && gradle wrapper --gradle-version 8.5 \
    && ./gradlew --version \
    && rm -rf /tmp/gradle-test

ARG CI_BASE_UPSTREAM=docker.io/library/

# Keep the registry setting in the base so branches with older Dockerfiles also work with pnpm 11.
FROM ${CI_BASE_UPSTREAM}node:22-alpine@sha256:0e1754758c2df4cfa3e68d59e2e1b7374bb7fd9ea6328eaaacf15cc934af8202 AS frontend
ENV pnpm_config_registry=https://registry.npmmirror.com

FROM ${CI_BASE_UPSTREAM}maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS backend
COPY maven-settings.xml /usr/share/maven/conf/settings.xml

FROM ${CI_BASE_UPSTREAM}eclipse-temurin:21-jre-noble@sha256:54999a956a8bf8be9af0052790a6a832bf91a62fad3199fd194c3cc28057fc62 AS runtime

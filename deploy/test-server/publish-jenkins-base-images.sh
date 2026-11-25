#!/usr/bin/env bash
set -Eeuo pipefail

CONTEXT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/jenkins" && pwd)"
BASE_REGISTRY="${BASE_REGISTRY:-192.168.1.182:5000/cyt-common-ci-r1/}"
CI_BASE_UPSTREAM="${CI_BASE_UPSTREAM:-docker.io/library/}"

if [[ "$BASE_REGISTRY" != */ || "$CI_BASE_UPSTREAM" != */ ]]; then
  echo 'ERROR: BASE_REGISTRY 和 CI_BASE_UPSTREAM 必须以 / 结尾' >&2
  exit 2
fi

build_base() {
  docker build --file "$CONTEXT/ci-base-images.Dockerfile" \
    --target "$1" --tag "$BASE_REGISTRY$2" \
    --build-arg "CI_BASE_UPSTREAM=$CI_BASE_UPSTREAM" "$CONTEXT"
}

build_base frontend node:22-alpine
build_base backend maven:3.9-eclipse-temurin-21
build_base runtime eclipse-temurin:21-jre-noble

docker push "${BASE_REGISTRY}node:22-alpine"
docker push "${BASE_REGISTRY}maven:3.9-eclipse-temurin-21"
docker push "${BASE_REGISTRY}eclipse-temurin:21-jre-noble"

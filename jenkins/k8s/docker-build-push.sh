#!/usr/bin/env bash
# Сборка и push образа (как в freestyle java-build-k8s). Запускать из корня репозитория.
set -euo pipefail

: "${DOCKER_REGISTRY:=docker.io}"
: "${DOCKER_IMAGE:?set DOCKER_IMAGE, e.g. dwezis/quizbot}"
: "${DOCKER_TAG:?set DOCKER_TAG, e.g. BUILD_NUMBER}"
: "${DOCKER_USER:?set DOCKER_USER}"
: "${DOCKER_PASS:?set DOCKER_PASS}"

echo "${DOCKER_PASS}" | docker login "${DOCKER_REGISTRY}" -u "${DOCKER_USER}" --password-stdin
docker build -t "${DOCKER_IMAGE}:${DOCKER_TAG}" -t "${DOCKER_IMAGE}:latest" .
docker push "${DOCKER_IMAGE}:${DOCKER_TAG}"
docker push "${DOCKER_IMAGE}:latest"
docker logout "${DOCKER_REGISTRY}" || true

printf '%s\n' "${DOCKER_IMAGE}" > docker_image.txt
printf '%s\n' "${DOCKER_TAG}" > docker_tag.txt
echo "Wrote docker_image.txt docker_tag.txt"

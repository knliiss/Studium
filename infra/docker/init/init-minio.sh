#!/usr/bin/env sh
set -eu

: "${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "local/${MINIO_BUCKET_PUBLIC:-public}"
mc mb --ignore-existing "local/${MINIO_BUCKET_PRIVATE:-private}"

echo "MinIO buckets are initialized."


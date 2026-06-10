#!/bin/sh
set -eu

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
  echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' '\n' | while IFS= read -r db; do
    db="$(echo "$db" | xargs)"

    if [ -n "$db" ]; then
      psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
        CREATE DATABASE "$db";
EOSQL
    fi
  done
fi

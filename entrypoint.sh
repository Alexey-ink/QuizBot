#!/bin/sh
set -e

# ---------- helper ----------
warn() { printf "WARN: %s\n" "$1" >&2; }
err()  { printf "ERROR: %s\n" "$1" >&2; exit 1; }

# Формируем JVM -D параметры, которые Spring сможет прочитать как системные свойства
JAVA_OPTS="$JAVA_OPTS -Dtelegram.bot.token=${TELEGRAM_BOT_TOKEN}"
JAVA_OPTS="$JAVA_OPTS -Dtelegram.bot.username=${TELEGRAM_BOT_USERNAME}"

JAVA_OPTS="$JAVA_OPTS -Dspring.datasource.url=${POSTGRES_URL}"
JAVA_OPTS="$JAVA_OPTS -Dspring.datasource.username=${POSTGRES_USER}"
JAVA_OPTS="$JAVA_OPTS -Dspring.datasource.password=${POSTGRES_PASSWORD}"
JAVA_OPTS="$JAVA_OPTS -Dspring.datasource.driver-class-name=org.postgresql.Driver"

JAVA_OPTS="$JAVA_OPTS -Dspring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:-update}"
JAVA_OPTS="$JAVA_OPTS -Dspring.jpa.database-platform=${SPRING_JPA_DATABASE_PLATFORM:-org.hibernate.dialect.PostgreSQLDialect}"

JAVA_OPTS="$JAVA_OPTS -Dadmin.default.login=${ADMIN_DEFAULT_LOGIN:-admin}"
JAVA_OPTS="$JAVA_OPTS -Dadmin.default.password=${ADMIN_DEFAULT_PASSWORD:-}"
JAVA_OPTS="$JAVA_OPTS -Dadmin.default.telegram-id=${ADMIN_DEFAULT_TELEGRAM_ID:-}"

echo "Starting application with JVM options:"
echo "$JAVA_OPTS"

exec java $JAVA_OPTS -jar /app/app.jar

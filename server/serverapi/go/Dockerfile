# ------------------------------------------------------------------------------
# Build Stage (Go 1.27.2)
# ------------------------------------------------------------------------------
FROM golang:1.27.2-alpine AS builder

WORKDIR /src

# Сертификаты, tzdata и создание пользователя для переноса в scratch
RUN apk add --no-cache ca-certificates tzdata git && \
    adduser -D -g '' -u 10001 -h /tmp appuser

COPY go.mod go.sum* ./
RUN go mod download || true

COPY . ./
RUN CGO_ENABLED=0 GOOS=linux go build \
    -trimpath \
    -ldflags="-s -w -extldflags '-static'" \
    -o /bin/api-proxy main.go

# ------------------------------------------------------------------------------
# Final Runtime Stage: Pure Scratch (0 MB ОС, нулевая поверхность атаки)
# ------------------------------------------------------------------------------
FROM scratch

# Корневые SSL-сертификаты для TLS-проксирования к api.telegram.org:443
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
# База временных зон
COPY --from=builder /usr/share/zoneinfo /usr/share/zoneinfo
# Учетная запись non-root пользователя
COPY --from=builder /etc/passwd /etc/passwd
COPY --from=builder /etc/group /etc/group

# Статический бинарник
COPY --from=builder /bin/api-proxy /app/api-proxy

USER 10001:10001

ENV PORT=8081
ENV TARGET_HOST=api.telegram.org

EXPOSE 8081

# Автономный healthcheck через флаг -healthcheck самого бинарника
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD ["/app/api-proxy", "-healthcheck"]

ENTRYPOINT ["/app/api-proxy"]

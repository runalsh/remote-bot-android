# ------------------------------------------------------------------------------
# Build Stage (Go 1.27.2)
# ------------------------------------------------------------------------------
FROM golang:1.27.2-alpine AS builder

WORKDIR /src

# Certificates, tzdata, and user creation for copying into scratch
RUN apk add --no-cache ca-certificates tzdata git && \
    adduser -D -g '' -u 10001 -h /tmp appuser

COPY go.mod go.sum* ./
RUN go mod download || true

COPY . ./
RUN CGO_ENABLED=0 GOOS=linux go build \
    -trimpath \
    -ldflags="-s -w -extldflags '-static'" \
    -o /bin/push-gateway main.go

# ------------------------------------------------------------------------------
# Final Runtime Stage: Pure Scratch (0 MB OS, zero attack surface)
# ------------------------------------------------------------------------------
FROM scratch

# Root SSL certificates for outbound requests to Google FCM / Huawei HMS
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
# Timezone database
COPY --from=builder /usr/share/zoneinfo /usr/share/zoneinfo
# Non-root user account
COPY --from=builder /etc/passwd /etc/passwd
COPY --from=builder /etc/group /etc/group

# Static binary
COPY --from=builder /bin/push-gateway /app/push-gateway

USER 10001:10001

ENV PORT=8080
EXPOSE 8080

# Self-contained healthcheck via -healthcheck flag of the binary itself (no curl/wget/shell)
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD ["/app/push-gateway", "-healthcheck"]

ENTRYPOINT ["/app/push-gateway"]

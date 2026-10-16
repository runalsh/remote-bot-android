FROM python:3.14.8-alpine

RUN apk --no-cache add ca-certificates tzdata wget && \
    addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY main.py ./

USER appuser:appgroup

ENV PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget --spider -q http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["python3", "main.py"]


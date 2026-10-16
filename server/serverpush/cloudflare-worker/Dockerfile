FROM node:24.22.0-alpine

RUN apk --no-cache add wget

WORKDIR /app

COPY package.json ./
RUN npm install

COPY . ./

ENV PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --spider -q http://localhost:8080/health || exit 1

CMD ["npx", "wrangler", "dev", "--ip", "0.0.0.0", "--port", "8080", "--compatibility-date", "2024-11-01"]


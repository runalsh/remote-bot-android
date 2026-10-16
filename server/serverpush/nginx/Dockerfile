FROM openresty/openresty:1.31.1.1-alpine-slim

RUN apk --no-cache add ca-certificates wget tzdata && \
    /usr/local/openresty/bin/opm get pintsized/lua-resty-http SkyLothar/lua-resty-jwt || true

RUN mkdir -p /etc/nginx/remotebot /etc/nginx/conf.d

COPY gateway.lua /etc/nginx/remotebot/gateway.lua
COPY nginx.conf /etc/nginx/remotebot/nginx-ssl.conf.example
COPY nginx-container.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget --spider -q http://localhost:8080/health || exit 1

CMD ["/usr/local/openresty/bin/openresty", "-g", "daemon off;"]


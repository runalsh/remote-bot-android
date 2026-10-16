FROM nginx:1.30.6-alpine-slim

RUN apk --no-cache add wget ca-certificates

# Сохраняем SSL-шаблон для хостов с монтированием сертификатов Let's Encrypt
COPY nginx.conf /etc/nginx/nginx-ssl.conf.example

# Конфигурация для запуска внутри контейнера на порту 8081
COPY default.conf /etc/nginx/conf.d/default.conf

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget --spider -q http://localhost:8081/health || exit 1

CMD ["nginx", "-g", "daemon off;"]


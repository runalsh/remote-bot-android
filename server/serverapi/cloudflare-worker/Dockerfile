FROM node:24.22.0-alpine

RUN apk --no-cache add wget

WORKDIR /app

COPY package.json ./
RUN npm install

COPY . ./

ENV PORT=8081
EXPOSE 8081

CMD ["npx", "wrangler", "dev", "--ip", "0.0.0.0", "--port", "8081", "--compatibility-date", "2024-11-01"]


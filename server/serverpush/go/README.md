[English](README.md) | [Русский](README_RU.md)

# RemoteBot Push Gateway (Go)

Lightweight, high-performance Webhook $\to$ Push Wakeup Gateway microservice written in Go (supporting both Google FCM and Huawei HMS Push Kit).

## Environment Variables
* `PORT` — HTTP server listening port (default: `8080`).
* `PUSH_GATEWAY_SECRET` — secret key for webhook authentication.

### For Google Firebase (FCM):
* `FIREBASE_SERVICE_ACCOUNT` — service account JSON string (FCM v1).
* `GOOGLE_APPLICATION_CREDENTIALS` — alternatively: path to `serviceAccount.json` file.
* `FCM_LEGACY_SERVER_KEY` — Firebase Legacy server key (optional fallback).

### For Huawei HMS Push:
* `HUAWEI_APP_ID` — Application ID from `agconnect-services.json`.
* `HUAWEI_CLIENT_ID` — Client ID (usually identical to `HUAWEI_APP_ID`).
* `HUAWEI_CLIENT_SECRET` — Client secret from `agconnect-services.json`.

## Endpoints
* `POST /tg/:token/:secretKey` — automatic provider selection (or via query `?provider=hms`/`fcm`).
* `POST /tg/fcm/:fcmToken/:secretKey` — delivery via Google FCM.
* `POST /tg/hms/:hmsToken/:secretKey` — delivery via Huawei Push Kit.
* `GET /health` — healthcheck endpoint (`{"status":"healthy","fcm":true,"hms":true}`).

## Running
```bash
go run main.go
```

## Running Tests
```bash
go test -v ./...
```

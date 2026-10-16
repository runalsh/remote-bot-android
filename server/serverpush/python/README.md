[English](README.md) | [Русский](README_RU.md)

# RemoteBot Push Gateway (Python)

Lightweight Telegram Webhook $\to$ Push Wakeup Gateway in Python (supporting both Google FCM and Huawei HMS Push Kit).
Runs on standard Python 3.10+ (utilizes `cryptography` package for Google FCM v1 RS256 JWT generation).

## Environment Variables
* `PORT` — HTTP server port (default: `8080`).
* `PUSH_GATEWAY_SECRET` — secret authentication key for incoming webhooks.

### For Google Firebase (FCM):
* `FIREBASE_SERVICE_ACCOUNT` — service account JSON string (FCM v1).
* `GOOGLE_APPLICATION_CREDENTIALS` — file path to `serviceAccount.json`.
* `FCM_LEGACY_SERVER_KEY` — Firebase Legacy server key (optional fallback).

### For Huawei HMS Push:
* `HUAWEI_APP_ID` — Application ID from `agconnect-services.json`.
* `HUAWEI_CLIENT_ID` — Client ID (usually identical to `HUAWEI_APP_ID`).
* `HUAWEI_CLIENT_SECRET` — Client secret from `agconnect-services.json`.

## Endpoints
* `POST /tg/:token/:secretKey` — automatic provider selection.
* `POST /tg/fcm/:fcmToken/:secretKey` — delivery via Google FCM.
* `POST /tg/hms/:hmsToken/:secretKey` — delivery via Huawei Push Kit.
* `GET /health` — healthcheck endpoint (`{"status":"healthy","fcm":true,"hms":true}`).

## Running
```bash
python3 main.py
```

## Running Tests
```bash
python3 -m unittest -v test_main.py
```

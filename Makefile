.PHONY: all build build-apk build-debug build-release build-release-testkey install reinstall run build-server check check-compile check-app check-ui lint lint-ktlint lint-detekt lint-android lint-fix test test-coverage test-android test-domain test-server test-serverpush test-serverpush-go test-serverpush-py test-serverpush-js test-serverapi test-serverapi-go test-serverapi-py test-serverapi-js docker-build-all docker-build-push docker-build-api docker-compose-build clean help

# Export environment variable for headless builds on Linux
export ANDROID_HOME ?= /opt/android-sdk

# ADB connection configuration
ADB ?= $(ANDROID_HOME)/platform-tools/adb
ADB_HOST ?= 127.0.0.1
ADB_PORT ?= 5037
PACKAGE_NAME ?= com.remotebot.app
MAIN_ACTIVITY ?= com.remotebot.app/.MainActivity
APK_PATH ?= app/build/outputs/apk/debug/app-debug.apk

# Host and port flags for ADB
ADB_CMD := $(ADB)
ifneq ($(ADB_HOST),)
    ADB_CMD += -H $(ADB_HOST)
endif
ifneq ($(ADB_PORT),)
    ADB_CMD += -P $(ADB_PORT)
endif

# Macro for safe Gradle execution with local.properties.linux substitution
define run_gradle
	if [ -d "/opt/android-sdk" ] && [ -f local.properties.linux ]; then \
		trap 'if [ -f local.properties.win ]; then cp local.properties.win local.properties; fi' EXIT INT TERM; \
		cp local.properties.linux local.properties; \
		./gradlew $(1); \
	else \
		./gradlew $(1); \
	fi
endef

all: build

help:
	@echo "Available commands:"
	@echo "  make check          - Full quality gate: compilation (check-compile), linting (lint) and tests (test)"
	@echo "  make check-compile  - Fast type and compilation check for all Kotlin modules (compileDebugKotlin)"
	@echo "  make check-app      - Alias for check-compile"
	@echo "  make check-ui       - Compilation check for UI module (:app:compileDebugKotlin)"
	@echo "  make lint           - Run all static analyzers without code modification (ktlint + detekt + Android Lint)"
	@echo "  make lint-fix       - Auto-fix Kotlin formatting errors (ktlintFormat)"
	@echo "  make lint-ktlint    - Run Kotlin linter and style checker (ktlintCheck)"
	@echo "  make lint-detekt    - Run Kotlin static analyzer for complexity, smells, and bugs (detekt)"
	@echo "  make lint-android   - Run official Android Lint (lintDebug)"
	@echo "  make build          - Build full project (Debug APK + Go server)"
	@echo "  make build-apk      - Build Android Debug APK (./gradlew assembleDebug)"
	@echo "  make build-release  - Build Android Release APK (./gradlew assembleRelease)"
	@echo "  make build-release-testkey - Build Android Release APK signed with testkey (./gradlew assembleReleaseTestkey)"
	@echo "  make install        - Build, install on device via ADB, and launch application"
	@echo "  make reinstall      - Build, completely uninstall from device, clean install, and launch"
	@echo "  make run            - Launch installed application on target device"
	@echo "  make test           - Run all project tests (Android + Push Gateway + API Proxy)"
	@echo "  make test-coverage  - Run tests and generate Jacoco code coverage report (:domain:jacocoTestReport)"
	@echo "  make test-android   - Run Android/Kotlin module unit tests (test-domain)"
	@echo "  make test-domain    - Run Kotlin Domain module tests (:domain:test)"
	@echo "  make test-server    - Run all server gateway tests (Push Gateway + API Proxy)"
	@echo "  make test-serverpush- Run Push Gateway unit tests (Go, Python, JS)"
	@echo "  make test-serverapi - Run Telegram API Proxy unit tests (Go, Python, JS)"
	@echo "  make docker-build-all  - Build all 8 server Docker images (Go, Python, Nginx, NPX)"
	@echo "  make docker-build-push - Build 4 Push Gateway Docker images"
	@echo "  make docker-build-api  - Build 4 API Proxy Docker images"
	@echo "  make docker-compose-build - Build all service profiles via Docker Compose"
	@echo "  make clean          - Clean temporary files and Gradle cache"

check: check-compile lint test
	@echo "🎉 Full quality check (compilation + linting + tests) completed successfully!"

check-compile:
	@echo "=== Fast type and compilation check for all Kotlin modules (compileDebugKotlin) ==="
	@$(call run_gradle,compileDebugKotlin)
	@echo "✅ All Kotlin modules compiled successfully!"

check-app: check-compile

check-ui:
	@echo "=== Fast compilation check for UI module (:app:compileDebugKotlin) ==="
	@$(call run_gradle,:app:compileDebugKotlin)
	@echo "✅ UI module (:app) compiled without errors!"

lint: lint-ktlint lint-detekt lint-android
	@echo "🎉 All static analyzers (ktlint, detekt, Android Lint) executed successfully!"

lint-ktlint:
	@echo "=== [1/3] Running ktlint (Kotlin code style and formatting) ==="
	@$(call run_gradle,ktlintCheck)
	@echo "✅ ktlint completed!"

lint-detekt:
	@echo "=== [2/3] Running detekt (static code analysis, complexity, smells, bugs) ==="
	@$(call run_gradle,detekt)
	@echo "✅ detekt completed!"

lint-android:
	@echo "=== [3/3] Running Android Lint (resources, manifest, security, Android APIs) ==="
	@$(call run_gradle,lintDebug)
	@echo "✅ Android Lint completed!"

lint-fix:
	@echo "=== Auto-formatting via ktlint (ktlintFormat) ==="
	@$(call run_gradle,ktlintFormat)
	@echo "✅ Auto-formatting completed!"

build: build-apk build-server

build-apk: build-debug

build-debug:
	@echo "=== [1/2] Building Android Debug APK ==="
	@$(call run_gradle,assembleDebug)
	@echo "APK built: app/build/outputs/apk/debug/app-debug.apk"

build-release:
	@echo "=== Building Android Release APK ==="
	@$(call run_gradle,assembleRelease)
	@echo "APK built: app/build/outputs/apk/release/app-release.apk"

build-release-testkey:
	@echo "=== Building Android Release APK (Testkey) ==="
	@$(call run_gradle,assembleReleaseTestkey)
	@echo "APK built: app/build/outputs/apk/releaseTestkey/app-releaseTestkey.apk"

install: build-apk
	@echo "=== Installing APK on device via ADB ($(ADB_HOST)) ==="
	@$(ADB_CMD) install -r $(APK_PATH)
	@echo "=== Launching application $(MAIN_ACTIVITY) ==="
	@$(ADB_CMD) shell am start -n $(MAIN_ACTIVITY)
	@echo "✅ Application successfully installed and launched!"

reinstall: build-apk
	@echo "=== Uninstalling existing application $(PACKAGE_NAME) from device ==="
	@$(ADB_CMD) uninstall $(PACKAGE_NAME) || true
	@echo "=== Clean installing APK on device via ADB ($(ADB_HOST)) ==="
	@$(ADB_CMD) install -r $(APK_PATH)
	@echo "=== Launching application $(MAIN_ACTIVITY) ==="
	@$(ADB_CMD) shell am start -n $(MAIN_ACTIVITY)
	@echo "✅ Application successfully reinstalled and launched!"

run:
	@echo "=== Launching application $(MAIN_ACTIVITY) on device ==="
	@$(ADB_CMD) shell am start -n $(MAIN_ACTIVITY)

build-server:
	@echo "=== [2/2] Building Go Push Gateway ==="
	cd server/serverpush/go && go build -o /tmp/push-gateway main.go
	@echo "Binary built: /tmp/push-gateway"

test: test-android test-server
	@echo "🎉 All project tests (Android + Push Gateway + API Proxy) passed successfully!"

test-android: test-domain
	@echo "✅ All Android/Kotlin module tests passed successfully!"

test-domain:
	@echo "=== [Android] Running Kotlin Domain module unit tests ==="
	@$(call run_gradle,:domain:test --info)

test-coverage:
	@echo "=== Generating Jacoco code coverage report ==="
	@$(call run_gradle,:domain:jacocoTestReport -Pcoverage)
	@echo "Report generated: domain/build/reports/jacoco/test/html/index.html"

test-server: test-serverpush test-serverapi
	@echo "🎉 All server gateway tests (Push Gateway + API Proxy) passed!"

test-serverpush: test-serverpush-go test-serverpush-py test-serverpush-js

test-serverpush-go:
	@echo "=== [Go] Running Push Gateway unit tests ==="
	cd server/serverpush/go && go test -v -race ./...

test-serverpush-py:
	@echo "=== [Python] Running Push Gateway unit tests ==="
	cd server/serverpush/python && python3 -m unittest -v test_main.py

test-serverpush-js:
	@echo "=== [JS / Cloudflare Worker] Running Push Gateway unit tests ==="
	cd server/serverpush/cloudflare-worker && npm test

test-serverapi: test-serverapi-go test-serverapi-py test-serverapi-js

test-serverapi-go:
	@echo "=== [Go] Running Telegram API Proxy unit tests ==="
	cd server/serverapi/go && go test -v ./...

test-serverapi-py:
	@echo "=== [Python] Running Telegram API Proxy unit tests ==="
	cd server/serverapi/python && python3 -m unittest -v test_main.py

test-serverapi-js:
	@echo "=== [JS / Cloudflare Worker] Running Telegram API Proxy unit tests ==="
	cd server/serverapi/cloudflare-worker && node --test test_worker.js

docker-build-all: docker-build-push docker-build-api
	@echo "🎉 All 8 server Docker images built successfully!"

docker-build-push:
	@echo "=== [1/4] Building Push Gateway: Go ==="
	docker build -t remotebot-push-gateway:go -f server/serverpush/go/Dockerfile server/serverpush/go
	@echo "=== [2/4] Building Push Gateway: Python ==="
	docker build -t remotebot-push-gateway:python -f server/serverpush/python/Dockerfile server/serverpush/python
	@echo "=== [3/4] Building Push Gateway: OpenResty ==="
	docker build -t remotebot-push-gateway:nginx -f server/serverpush/nginx/Dockerfile server/serverpush/nginx
	@echo "=== [4/4] Building Push Gateway: Cloudflare Worker / NPX ==="
	docker build -t remotebot-push-gateway:npx -f server/serverpush/cloudflare-worker/Dockerfile server/serverpush/cloudflare-worker

docker-build-api:
	@echo "=== [1/4] Building API Proxy: Go ==="
	docker build -t remotebot-api-proxy:go -f server/serverapi/go/Dockerfile server/serverapi/go
	@echo "=== [2/4] Building API Proxy: Python ==="
	docker build -t remotebot-api-proxy:python -f server/serverapi/python/Dockerfile server/serverapi/python
	@echo "=== [3/4] Building API Proxy: Nginx ==="
	docker build -t remotebot-api-proxy:nginx -f server/serverapi/nginx/Dockerfile server/serverapi/nginx
	@echo "=== [4/4] Building API Proxy: Cloudflare Worker / NPX ==="
	docker build -t remotebot-api-proxy:npx -f server/serverapi/cloudflare-worker/Dockerfile server/serverapi/cloudflare-worker

docker-compose-build:
	@echo "=== Building all services via Docker Compose ==="
	cd server && docker compose --profile go --profile python --profile nginx --profile npx build

clean:
	rm -f /tmp/push-gateway
	@$(call run_gradle,clean)


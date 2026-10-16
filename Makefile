.PHONY: all build build-apk build-debug build-release install reinstall run build-server check check-compile check-app check-ui lint lint-ktlint lint-detekt lint-android lint-fix test test-android test-domain test-server test-serverpush test-serverpush-go test-serverpush-py test-serverpush-js test-serverapi test-serverapi-go test-serverapi-py test-serverapi-js docker-build-all docker-build-push docker-build-api docker-compose-build clean help

# Экспорт переменной окружения для headless сборки в Linux
export ANDROID_HOME ?= /opt/android-sdk

# Настройки ADB подключения
ADB ?= $(ANDROID_HOME)/platform-tools/adb
ADB_HOST ?= 127.0.0.1
ADB_PORT ?= 5037
PACKAGE_NAME ?= com.remotebot.app
MAIN_ACTIVITY ?= com.remotebot.app/.MainActivity
APK_PATH ?= app/build/outputs/apk/debug/app-debug.apk

# Флаги хоста и порта для ADB
ADB_CMD := $(ADB)
ifneq ($(ADB_HOST),)
    ADB_CMD += -H $(ADB_HOST)
endif
ifneq ($(ADB_PORT),)
    ADB_CMD += -P $(ADB_PORT)
endif

# Макрос безопасного запуска Gradle с подстановкой local.properties.linux
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
	@echo "Доступные команды:"
	@echo "  make check          - Полный гейт качества: компиляция (check-compile), линтинг (lint) и тесты (test)"
	@echo "  make check-compile  - Быстрая проверка типов и компиляции Kotlin всех модулей (compileDebugKotlin)"
	@echo "  make check-app      - Алиас для check-compile"
	@echo "  make check-ui       - Проверка компиляции модуля UI приложения (:app:compileDebugKotlin)"
	@echo "  make lint           - Запуск всех статических анализаторов без изменения кода (ktlint + detekt + Android Lint)"
	@echo "  make lint-fix       - Автоисправление ошибок форматирования Kotlin (ktlintFormat)"
	@echo "  make lint-ktlint    - Запуск линтера форматирования и стиля Kotlin (ktlintCheck)"
	@echo "  make lint-detekt    - Запуск статического анализатора кода Kotlin (detekt)"
	@echo "  make lint-android   - Запуск официального Android Lint (lintDebug)"
	@echo "  make build          - Сборка всего проекта (Debug APK + Go сервер)"
	@echo "  make build-apk      - Сборка Android Debug APK (./gradlew assembleDebug)"
	@echo "  make build-release  - Сборка Android Release APK (./gradlew assembleRelease)"
	@echo "  make install        - Сборка, установка на устройство через ADB и запуск приложения"
	@echo "  make reinstall      - Сборка, полное удаление с устройства, чистая установка и запуск"
	@echo "  make run            - Запуск уже установленного приложения на устройстве"
	@echo "  make test           - Запуск всех тестов проекта (Android + Push Gateway + API Proxy)"
	@echo "  make test-android   - Запуск тестов Android/Kotlin модулей (test-domain)"
	@echo "  make test-domain    - Запуск тестов Kotlin Domain модуля (:domain:test)"
	@echo "  make test-server    - Запуск тестов всех серверных шлюзов (Push Gateway + API Proxy)"
	@echo "  make test-serverpush- Запуск тестов шлюзов Push Gateway (Go, Python, JS)"
	@echo "  make test-serverapi - Запуск тестов шлюзов Telegram API Proxy (Go, Python, JS)"
	@echo "  make docker-build-all  - Сборка всех 8 Docker-образов серверов (Go, Python, Nginx, NPX)"
	@echo "  make docker-build-push - Сборка 4 Docker-образов Push Gateway"
	@echo "  make docker-build-api  - Сборка 4 Docker-образов API Proxy"
	@echo "  make docker-compose-build - Сборка всех профилей через Docker Compose"
	@echo "  make clean          - Очистка временных файлов и Gradle кэша"

check: check-compile lint test
	@echo "🎉 Полная проверка (компиляция + линтинг + тесты) успешно завершена!"

check-compile:
	@echo "=== Быстрая проверка типов и компиляции Kotlin всех модулей (compileDebugKotlin) ==="
	@$(call run_gradle,compileDebugKotlin)
	@echo "✅ Все модули Kotlin скомпилированы успешно!"

check-app: check-compile

check-ui:
	@echo "=== Быстрая проверка компиляции модуля UI (:app:compileDebugKotlin) ==="
	@$(call run_gradle,:app:compileDebugKotlin)
	@echo "✅ Модуль UI (:app) компилируется без ошибок!"

lint: lint-ktlint lint-detekt lint-android
	@echo "🎉 Все статические анализаторы (ktlint, detekt, Android Lint) отработали успешно!"

lint-ktlint:
	@echo "=== [1/3] Запуск ktlint (стиль и форматирование кода Kotlin) ==="
	@$(call run_gradle,ktlintCheck)
	@echo "✅ ktlint завершен!"

lint-detekt:
	@echo "=== [2/3] Запуск detekt (статический анализ сложности, code smell, багов) ==="
	@$(call run_gradle,detekt)
	@echo "✅ detekt завершен!"

lint-android:
	@echo "=== [3/3] Запуск Android Lint (ресурсы, манифест, безопасность, Android API) ==="
	@$(call run_gradle,lintDebug)
	@echo "✅ Android Lint завершен!"

lint-fix:
	@echo "=== Автоисправление форматирования через ktlint (ktlintFormat) ==="
	@$(call run_gradle,ktlintFormat)
	@echo "✅ Автоисправление завершено!"

build: build-apk build-server

build-apk: build-debug

build-debug:
	@echo "=== [1/2] Сборка Android Debug APK ==="
	@$(call run_gradle,assembleDebug)
	@echo "APK собран: app/build/outputs/apk/debug/app-debug.apk"

build-release:
	@echo "=== Сборка Android Release APK ==="
	@$(call run_gradle,assembleRelease)
	@echo "APK собран: app/build/outputs/apk/release/app-release-unsigned.apk"

install: build-apk
	@echo "=== Установка APK на устройство через ADB ($(ADB_HOST)) ==="
	@$(ADB_CMD) install -r $(APK_PATH)
	@echo "=== Запуск приложения $(MAIN_ACTIVITY) ==="
	@$(ADB_CMD) shell am start -n $(MAIN_ACTIVITY)
	@echo "✅ Приложение успешно установлено и запущено!"

reinstall: build-apk
	@echo "=== Удаление существующего приложения $(PACKAGE_NAME) с устройства ==="
	@$(ADB_CMD) uninstall $(PACKAGE_NAME) || true
	@echo "=== Чистая установка APK на устройство через ADB ($(ADB_HOST)) ==="
	@$(ADB_CMD) install -r $(APK_PATH)
	@echo "=== Запуск приложения $(MAIN_ACTIVITY) ==="
	@$(ADB_CMD) shell am start -n $(MAIN_ACTIVITY)
	@echo "✅ Приложение успешно переустановлено и запущено!"

run:
	@echo "=== Запуск приложения $(MAIN_ACTIVITY) на устройстве ==="
	@$(ADB_CMD) shell am start -n $(MAIN_ACTIVITY)

build-server:
	@echo "=== [2/2] Сборка Go Push Gateway ==="
	cd server/serverpush/go && go build -o /tmp/push-gateway main.go
	@echo "Бинарник собран: /tmp/push-gateway"

test: test-android test-server
	@echo "🎉 Все тесты проекта (Android + Push Gateway + API Proxy) успешно пройдены!"

test-android: test-domain
	@echo "✅ Все тесты Android/Kotlin модулей успешно пройдены!"

test-domain:
	@echo "=== [Android] Запуск тестов Kotlin Domain модуля ==="
	@$(call run_gradle,:domain:test --info)

test-server: test-serverpush test-serverapi
	@echo "🎉 Все тесты серверных шлюзов (Push Gateway + API Proxy) пройдены!"

test-serverpush: test-serverpush-go test-serverpush-py test-serverpush-js

test-serverpush-go:
	@echo "=== [Go] Запуск тестов Push Gateway ==="
	cd server/serverpush/go && go test -v -race ./...

test-serverpush-py:
	@echo "=== [Python] Запуск тестов Push Gateway ==="
	cd server/serverpush/python && python3 -m unittest -v test_main.py

test-serverpush-js:
	@echo "=== [JS / Cloudflare Worker] Запуск тестов Push Gateway ==="
	cd server/serverpush/cloudflare-worker && npm test

test-serverapi: test-serverapi-go test-serverapi-py test-serverapi-js

test-serverapi-go:
	@echo "=== [Go] Запуск тестов Telegram API Proxy ==="
	cd server/serverapi/go && go test -v ./...

test-serverapi-py:
	@echo "=== [Python] Запуск тестов Telegram API Proxy ==="
	cd server/serverapi/python && python3 -m unittest -v test_main.py

test-serverapi-js:
	@echo "=== [JS / Cloudflare Worker] Запуск тестов Telegram API Proxy ==="
	cd server/serverapi/cloudflare-worker && node --test test_worker.js

docker-build-all: docker-build-push docker-build-api
	@echo "🎉 Все 8 Docker-образов серверов успешно собраны!"

docker-build-push:
	@echo "=== [1/4] Сборка Push Gateway: Go ==="
	docker build -t remotebot-push-gateway:go -f server/serverpush/go/Dockerfile server/serverpush/go
	@echo "=== [2/4] Сборка Push Gateway: Python ==="
	docker build -t remotebot-push-gateway:python -f server/serverpush/python/Dockerfile server/serverpush/python
	@echo "=== [3/4] Сборка Push Gateway: OpenResty ==="
	docker build -t remotebot-push-gateway:nginx -f server/serverpush/nginx/Dockerfile server/serverpush/nginx
	@echo "=== [4/4] Сборка Push Gateway: Cloudflare Worker / NPX ==="
	docker build -t remotebot-push-gateway:npx -f server/serverpush/cloudflare-worker/Dockerfile server/serverpush/cloudflare-worker

docker-build-api:
	@echo "=== [1/4] Сборка API Proxy: Go ==="
	docker build -t remotebot-api-proxy:go -f server/serverapi/go/Dockerfile server/serverapi/go
	@echo "=== [2/4] Сборка API Proxy: Python ==="
	docker build -t remotebot-api-proxy:python -f server/serverapi/python/Dockerfile server/serverapi/python
	@echo "=== [3/4] Сборка API Proxy: Nginx ==="
	docker build -t remotebot-api-proxy:nginx -f server/serverapi/nginx/Dockerfile server/serverapi/nginx
	@echo "=== [4/4] Сборка API Proxy: Cloudflare Worker / NPX ==="
	docker build -t remotebot-api-proxy:npx -f server/serverapi/cloudflare-worker/Dockerfile server/serverapi/cloudflare-worker

docker-compose-build:
	@echo "=== Сборка всех сервисов через Docker Compose ==="
	cd server && docker compose --profile go --profile python --profile nginx --profile npx build

clean:
	rm -f /tmp/push-gateway
	@$(call run_gradle,clean)

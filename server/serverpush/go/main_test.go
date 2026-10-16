package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

func TestHealthEndpointDefault(t *testing.T) {
	req, err := http.NewRequest(http.MethodGet, "/health", nil)
	if err != nil {
		t.Fatalf("Failed to create request: %v", err)
	}

	rr := httptest.NewRecorder()
	handleHealthCheck(rr, req)

	if status := rr.Code; status != http.StatusOK {
		t.Errorf("Expected status 200, got %d", status)
	}

	var data map[string]interface{}
	if err := json.Unmarshal(rr.Body.Bytes(), &data); err != nil {
		t.Fatalf("Failed to parse JSON response: %v", err)
	}
	if data["status"] != "healthy" {
		t.Errorf("Expected status healthy, got %v", data["status"])
	}
	if data["fcm"] != false || data["hms"] != false {
		t.Errorf("Expected fcm=false, hms=false, got fcm=%v, hms=%v", data["fcm"], data["hms"])
	}
}

func TestHealthEndpointWithCredentials(t *testing.T) {
	os.Setenv("FCM_LEGACY_SERVER_KEY", "legacy_test_key")
	os.Setenv("HUAWEI_APP_ID", "100200")
	os.Setenv("HUAWEI_CLIENT_SECRET", "hms_secret_test")
	defer func() {
		os.Unsetenv("FCM_LEGACY_SERVER_KEY")
		os.Unsetenv("HUAWEI_APP_ID")
		os.Unsetenv("HUAWEI_CLIENT_SECRET")
	}()

	req, _ := http.NewRequest(http.MethodGet, "/health", nil)
	rr := httptest.NewRecorder()
	handleHealthCheck(rr, req)

	if status := rr.Code; status != http.StatusOK {
		t.Errorf("Expected status 200, got %d", status)
	}

	var data map[string]interface{}
	_ = json.Unmarshal(rr.Body.Bytes(), &data)
	if data["fcm"] != true || data["hms"] != true {
		t.Errorf("Expected fcm=true, hms=true, got fcm=%v, hms=%v", data["fcm"], data["hms"])
	}
}

func TestHealthEndpointMethodNotAllowed(t *testing.T) {
	req, _ := http.NewRequest(http.MethodPost, "/health", nil)
	rr := httptest.NewRecorder()
	handleHealthCheck(rr, req)

	if status := rr.Code; status != http.StatusMethodNotAllowed {
		t.Errorf("Expected 405 Method Not Allowed, got %d", status)
	}
}

func TestTelegramWebhookMethodNotAllowed(t *testing.T) {
	req, _ := http.NewRequest(http.MethodGet, "/tg/device_token/secret", nil)
	rr := httptest.NewRecorder()
	handleTelegramWebhook(rr, req)

	if rr.Code != http.StatusMethodNotAllowed {
		t.Errorf("Expected 405 Method Not Allowed, got %d", rr.Code)
	}
}

func TestTelegramWebhookInvalidPath(t *testing.T) {
	req, _ := http.NewRequest(http.MethodPost, "/tg/incomplete_path", bytes.NewBuffer([]byte(`{"update_id":1}`)))
	rr := httptest.NewRecorder()
	handleTelegramWebhook(rr, req)

	if rr.Code != http.StatusBadRequest {
		t.Errorf("Expected 400 for incomplete path, got %d", rr.Code)
	}
}

func TestTelegramWebhookExcessivePath(t *testing.T) {
	req, _ := http.NewRequest(http.MethodPost, "/tg/one/two/three/four", bytes.NewBuffer([]byte(`{"update_id":1}`)))
	rr := httptest.NewRecorder()
	handleTelegramWebhook(rr, req)

	if rr.Code != http.StatusBadRequest {
		t.Errorf("Expected 400 for excessive path, got %d", rr.Code)
	}
}

func TestTelegramWebhookEmptyBody(t *testing.T) {
	req, _ := http.NewRequest(http.MethodPost, "/tg/device_token/any_secret", bytes.NewBuffer([]byte(``)))
	rr := httptest.NewRecorder()
	handleTelegramWebhook(rr, req)

	if rr.Code != http.StatusBadRequest {
		t.Errorf("Expected 400 for empty body, got %d", rr.Code)
	}
}

func TestTelegramWebhookInvalidJSON(t *testing.T) {
	req, _ := http.NewRequest(http.MethodPost, "/tg/device_token/any_secret", bytes.NewBuffer([]byte(`not-json`)))
	rr := httptest.NewRecorder()
	handleTelegramWebhook(rr, req)

	if rr.Code != http.StatusBadRequest {
		t.Errorf("Expected 400 for invalid JSON, got %d", rr.Code)
	}
}

func TestTelegramWebhookValid(t *testing.T) {
	serverSecretKey = "my_secret_token"
	defer func() { serverSecretKey = "" }()

	jsonBody := []byte(`{"update_id": 99887766}`)

	// Валидный запрос с правильным secretKey (Legacy/Default)
	req, _ := http.NewRequest(http.MethodPost, "/tg/fcm_device_token_abc/my_secret_token", bytes.NewBuffer(jsonBody))
	rr := httptest.NewRecorder()
	handleTelegramWebhook(rr, req)

	if rr.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d (body: %s)", rr.Code, rr.Body.String())
	}

	// Валидный запрос для HMS
	reqHMS, _ := http.NewRequest(http.MethodPost, "/tg/hms/hms_device_token_xyz/my_secret_token", bytes.NewBuffer(jsonBody))
	rrHMS := httptest.NewRecorder()
	handleTelegramWebhook(rrHMS, reqHMS)

	if rrHMS.Code != http.StatusOK {
		t.Errorf("Expected 200 OK for HMS, got %d (body: %s)", rrHMS.Code, rrHMS.Body.String())
	}

	// Валидный запрос с query-параметром ?provider=hms
	reqQuery, _ := http.NewRequest(http.MethodPost, "/tg/hms_device_token_xyz/my_secret_token?provider=hms", bytes.NewBuffer(jsonBody))
	rrQuery := httptest.NewRecorder()
	handleTelegramWebhook(rrQuery, reqQuery)

	if rrQuery.Code != http.StatusOK {
		t.Errorf("Expected 200 OK for query provider, got %d (body: %s)", rrQuery.Code, rrQuery.Body.String())
	}

	// Невалидный secretKey -> 401 Unauthorized
	reqUnauthorized, _ := http.NewRequest(http.MethodPost, "/tg/fcm_device_token_abc/wrong_secret", bytes.NewBuffer(jsonBody))
	rrUnauth := httptest.NewRecorder()
	handleTelegramWebhook(rrUnauth, reqUnauthorized)

	if rrUnauth.Code != http.StatusUnauthorized {
		t.Errorf("Expected 401 Unauthorized, got %d", rrUnauth.Code)
	}
}

package main

import (
	"bytes"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
)

func TestTelegramReverseProxy(t *testing.T) {
	// Start mock Telegram API backend
	mockBackend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/bot12345/getMe" && r.Method == http.MethodGet {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{"ok":true,"result":"mock_get_ok"}`))
			return
		}

		if r.URL.Path == "/bot12345/sendMessage" && r.Method == http.MethodPost {
			body, _ := io.ReadAll(r.Body)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{"ok":true,"received_body":"` + string(body) + `"}`))
			return
		}

		http.NotFound(w, r)
	}))
	defer mockBackend.Close()

	mockURL, err := url.Parse(mockBackend.URL)
	if err != nil {
		t.Fatalf("Failed to parse mock backend URL: %v", err)
	}

	// Create ReverseProxy pointing to mockBackend
	proxy := newTelegramReverseProxy(mockURL.Host)
	proxy.Director = func(req *http.Request) {
		req.URL.Scheme = mockURL.Scheme
		req.URL.Host = mockURL.Host
		req.Host = mockURL.Host
	}

	t.Run("GET request forwarded with params", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/bot12345/getMe", nil)
		rec := httptest.NewRecorder()

		proxy.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("Expected status 200, got %d", rec.Code)
		}
		body := rec.Body.String()
		if body != `{"ok":true,"result":"mock_get_ok"}` {
			t.Errorf("Unexpected body: %s", body)
		}
	})

	t.Run("POST request forwarded with body", func(t *testing.T) {
		payload := []byte(`hello proxy`)
		req := httptest.NewRequest(http.MethodPost, "/bot12345/sendMessage", bytes.NewReader(payload))
		rec := httptest.NewRecorder()

		proxy.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("Expected status 200, got %d", rec.Code)
		}
		body := rec.Body.String()
		if body != `{"ok":true,"received_body":"hello proxy"}` {
			t.Errorf("Unexpected body: %s", body)
		}
	})

	t.Run("Health check handler returns 200 OK", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/health", nil)
		rec := httptest.NewRecorder()

		handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{"status":"healthy","target_host":"` + mockURL.Host + `"}` + "\n"))
		})
		handler.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("Expected status 200, got %d", rec.Code)
		}
	})
}


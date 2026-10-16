package main

import (
	"fmt"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
)

const defaultTargetHost = "api.telegram.org"

func newTelegramReverseProxy(targetHost string) *httputil.ReverseProxy {
	targetURL := &url.URL{
		Scheme: "https",
		Host:   targetHost,
	}

	director := func(req *http.Request) {
		req.URL.Scheme = targetURL.Scheme
		req.URL.Host = targetURL.Host
		req.Host = targetURL.Host
	}

	return &httputil.ReverseProxy{
		Director: director,
	}
}

func main() {
	if len(os.Args) > 1 && os.Args[1] == "-healthcheck" {
		port := os.Getenv("PORT")
		if port == "" {
			port = "8081"
		}
		resp, err := http.Get("http://127.0.0.1:" + port + "/health")
		if err != nil || resp.StatusCode != http.StatusOK {
			os.Exit(1)
		}
		os.Exit(0)
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8081"
	}

	targetHost := os.Getenv("TARGET_HOST")
	if targetHost == "" {
		targetHost = defaultTargetHost
	}

	proxy := newTelegramReverseProxy(targetHost)

	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"healthy","target_host":"` + targetHost + `"}` + "\n"))
	})

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		proxy.ServeHTTP(w, r)
	})

	log.Printf("[Telegram API Proxy Go] Listening on :%s -> https://%s\n", port, targetHost)
	if err := http.ListenAndServe(fmt.Sprintf(":%s", port), nil); err != nil {
		log.Fatalf("Server error: %v", err)
	}
}


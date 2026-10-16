package main

import (
	"bytes"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"
)

type ServiceAccount struct {
	Type         string `json:"type"`
	ProjectID    string `json:"project_id"`
	PrivateKeyID string `json:"private_key_id"`
	PrivateKey   string `json:"private_key"`
	ClientEmail  string `json:"client_email"`
}

type FCMv1Message struct {
	Message FCMv1MessageContent `json:"message"`
}

type FCMv1MessageContent struct {
	Token   string            `json:"token"`
	Data    map[string]string `json:"data"`
	Android FCMv1Android      `json:"android"`
}

type FCMv1Android struct {
	Priority string `json:"priority"`
}

type HMSMessage struct {
	ValidateOnly bool              `json:"validate_only"`
	Message      HMSMessageContent `json:"message"`
}

type HMSMessageContent struct {
	Data    string     `json:"data"`
	Token   []string   `json:"token"`
	Android HMSAndroid `json:"android"`
}

type HMSAndroid struct {
	Urgency  string `json:"urgency"`
	Category string `json:"category"`
}

var (
	serverSecretKey = os.Getenv("PUSH_GATEWAY_SECRET")

	// Google OAuth
	cachedToken string
	tokenExpiry time.Time
	tokenMutex  sync.Mutex

	// Huawei OAuth
	cachedHuaweiToken string
	huaweiTokenExpiry time.Time
	huaweiTokenMutex  sync.Mutex
)

func getServiceAccount() (*ServiceAccount, error) {
	raw := os.Getenv("FIREBASE_SERVICE_ACCOUNT")
	if raw == "" {
		filePath := os.Getenv("GOOGLE_APPLICATION_CREDENTIALS")
		if filePath != "" {
			data, err := os.ReadFile(filePath)
			if err != nil {
				return nil, err
			}
			raw = string(data)
		}
	}
	if raw == "" {
		return nil, nil
	}

	var sa ServiceAccount
	if err := json.Unmarshal([]byte(raw), &sa); err != nil {
		return nil, err
	}
	return &sa, nil
}

func getGoogleOAuthToken(sa *ServiceAccount) (string, error) {
	tokenMutex.Lock()
	defer tokenMutex.Unlock()

	now := time.Now()
	if cachedToken != "" && tokenExpiry.After(now.Add(2*time.Minute)) {
		return cachedToken, nil
	}

	header := map[string]string{
		"alg": "RS256",
		"typ": "JWT",
	}
	if sa.PrivateKeyID != "" {
		header["kid"] = sa.PrivateKeyID
	}

	claims := map[string]interface{}{
		"iss":   sa.ClientEmail,
		"scope": "https://www.googleapis.com/auth/firebase.messaging",
		"aud":   "https://oauth2.googleapis.com/token",
		"iat":   now.Add(-30 * time.Second).Unix(),
		"exp":   now.Add(1 * time.Hour).Unix(),
	}

	headerJSON, _ := json.Marshal(header)
	claimsJSON, _ := json.Marshal(claims)

	headerB64 := base64.RawURLEncoding.EncodeToString(headerJSON)
	claimsB64 := base64.RawURLEncoding.EncodeToString(claimsJSON)
	sigInput := headerB64 + "." + claimsB64

	pemStr := strings.ReplaceAll(sa.PrivateKey, "\\n", "\n")
	block, _ := pem.Decode([]byte(pemStr))
	if block == nil {
		return "", fmt.Errorf("failed to decode PEM block from private_key")
	}

	parsedKey, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		parsedKey, err = x509.ParsePKCS1PrivateKey(block.Bytes)
		if err != nil {
			return "", fmt.Errorf("failed to parse private key: %w", err)
		}
	}

	rsaKey, ok := parsedKey.(*rsa.PrivateKey)
	if !ok {
		return "", fmt.Errorf("private key is not RSA")
	}

	hashed := sha256.Sum256([]byte(sigInput))
	signature, err := rsa.SignPKCS1v15(rand.Reader, rsaKey, crypto.SHA256, hashed[:])
	if err != nil {
		return "", fmt.Errorf("RSA sign failed: %w", err)
	}

	jwt := sigInput + "." + base64.RawURLEncoding.EncodeToString(signature)

	formData := url.Values{
		"grant_type": {"urn:ietf:params:oauth:grant-type:jwt-bearer"},
		"assertion":  {jwt},
	}

	resp, err := http.PostForm("https://oauth2.googleapis.com/token", formData)
	if err != nil {
		return "", fmt.Errorf("oauth request failed: %w", err)
	}
	defer resp.Body.Close()

	bodyBytes, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("oauth error HTTP %d: %s", resp.StatusCode, string(bodyBytes))
	}

	var tokenResp struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	if err := json.Unmarshal(bodyBytes, &tokenResp); err != nil {
		return "", err
	}

	cachedToken = tokenResp.AccessToken
	tokenExpiry = now.Add(time.Duration(tokenResp.ExpiresIn) * time.Second)
	return cachedToken, nil
}

func getHuaweiOAuthToken(clientID, clientSecret string) (string, error) {
	huaweiTokenMutex.Lock()
	defer huaweiTokenMutex.Unlock()

	now := time.Now()
	if cachedHuaweiToken != "" && huaweiTokenExpiry.After(now.Add(1*time.Minute)) {
		return cachedHuaweiToken, nil
	}

	formData := url.Values{
		"grant_type":    {"client_credentials"},
		"client_id":     {clientID},
		"client_secret": {clientSecret},
	}

	resp, err := http.PostForm("https://oauth-login.cloud.huawei.com/oauth2/v3/token", formData)
	if err != nil {
		return "", fmt.Errorf("huawei oauth request failed: %w", err)
	}
	defer resp.Body.Close()

	bodyBytes, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("huawei oauth error HTTP %d: %s", resp.StatusCode, string(bodyBytes))
	}

	var tokenResp struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	if err := json.Unmarshal(bodyBytes, &tokenResp); err != nil {
		return "", err
	}

	cachedHuaweiToken = tokenResp.AccessToken
	huaweiTokenExpiry = now.Add(time.Duration(tokenResp.ExpiresIn) * time.Second)
	return cachedHuaweiToken, nil
}

func sendHMSPush(appID, clientID, clientSecret, hmsToken string, updateID int64, updateJSON string) {
	token, err := getHuaweiOAuthToken(clientID, clientSecret)
	if err != nil {
		log.Printf("[HMS] OAuth error: %v", err)
		return
	}

	dataMap := map[string]string{
		"action":    "wakeup",
		"update_id": fmt.Sprintf("%d", updateID),
	}
	if updateJSON != "" && len(updateJSON) < 3800 {
		dataMap["update_json"] = updateJSON
	}

	dataJSON, _ := json.Marshal(dataMap)

	payload := HMSMessage{
		ValidateOnly: false,
		Message: HMSMessageContent{
			Data:  string(dataJSON),
			Token: []string{hmsToken},
			Android: HMSAndroid{
				Urgency:  "HIGH",
				Category: "IM",
			},
		},
	}

	jsonBytes, _ := json.Marshal(payload)
	targetURL := fmt.Sprintf("https://push-api.cloud.huawei.com/v1/%s/messages:send", appID)

	req, err := http.NewRequest(http.MethodPost, targetURL, bytes.NewBuffer(jsonBytes))
	if err != nil {
		log.Printf("[HMS] Error creating request: %v", err)
		return
	}

	req.Header.Set("Content-Type", "application/json; charset=UTF-8")
	req.Header.Set("Authorization", "Bearer "+token)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		log.Printf("[HMS] Request failed: %v", err)
		return
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)
	if resp.StatusCode == http.StatusOK {
		log.Printf("[HMS] Delivered wakeup for update_id %d", updateID)
	} else {
		log.Printf("[HMS] Failed with HTTP %d: %s", resp.StatusCode, string(respBody))
	}
}

func sendFCMv1(sa *ServiceAccount, fcmToken string, updateID int64, updateJSON string) {
	token, err := getGoogleOAuthToken(sa)
	if err != nil {
		log.Printf("[FCM v1] OAuth error: %v", err)
		return
	}

	dataMap := map[string]string{
		"action":    "wakeup",
		"update_id": fmt.Sprintf("%d", updateID),
	}
	if updateJSON != "" && len(updateJSON) < 3800 {
		dataMap["update_json"] = updateJSON
	}

	payload := FCMv1Message{
		Message: FCMv1MessageContent{
			Token: fcmToken,
			Data:  dataMap,
			Android: FCMv1Android{
				Priority: "high",
			},
		},
	}

	jsonBytes, _ := json.Marshal(payload)
	url := fmt.Sprintf("https://fcm.googleapis.com/v1/projects/%s/messages:send", sa.ProjectID)

	req, err := http.NewRequest(http.MethodPost, url, bytes.NewBuffer(jsonBytes))
	if err != nil {
		log.Printf("[FCM v1] Error creating request: %v", err)
		return
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		log.Printf("[FCM v1] Request failed: %v", err)
		return
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)
	if resp.StatusCode == http.StatusOK {
		log.Printf("[FCM v1] Delivered wakeup for update_id %d", updateID)
	} else {
		log.Printf("[FCM v1] Failed with HTTP %d: %s", resp.StatusCode, string(respBody))
	}
}

func sendFCMLegacy(serverKey string, fcmToken string, updateID int64, updateJSON string) {
	dataMap := map[string]string{
		"action":    "wakeup",
		"update_id": fmt.Sprintf("%d", updateID),
	}
	if updateJSON != "" && len(updateJSON) < 3800 {
		dataMap["update_json"] = updateJSON
	}

	payload := map[string]interface{}{
		"to":   fcmToken,
		"data": dataMap,
	}

	jsonBytes, _ := json.Marshal(payload)
	req, err := http.NewRequest(http.MethodPost, "https://fcm.googleapis.com/fcm/send", bytes.NewBuffer(jsonBytes))
	if err != nil {
		log.Printf("[FCM Legacy] Error creating request: %v", err)
		return
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "key="+serverKey)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		log.Printf("[FCM Legacy] Request failed: %v", err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusOK {
		log.Printf("[FCM Legacy] Delivered wakeup for update_id %d", updateID)
	} else {
		log.Printf("[FCM Legacy] Failed with HTTP %d", resp.StatusCode)
	}
}

func handleTelegramWebhook(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	// Supported URL formats:
	// - /tg/:token/:secretKey
	// - /tg/fcm/:token/:secretKey
	// - /tg/hms/:token/:secretKey
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	var provider, pushToken, clientSecret string

	if len(parts) == 3 && parts[0] == "tg" {
		provider = "auto"
		if q := r.URL.Query().Get("provider"); q != "" {
			provider = strings.ToLower(q)
		}
		pushToken = parts[1]
		clientSecret = parts[2]
	} else if len(parts) == 4 && parts[0] == "tg" {
		provider = strings.ToLower(parts[1])
		pushToken = parts[2]
		clientSecret = parts[3]
	} else {
		http.Error(w, "Invalid path format. Expected /tg/:token/:secretKey or /tg/hms/:token/:secretKey", http.StatusBadRequest)
		return
	}

	if serverSecretKey != "" && clientSecret != serverSecretKey {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}
	if len(body) == 0 {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(`{"error":"Empty body"}`))
		return
	}

	var update struct {
		UpdateID int64 `json:"update_id"`
	}
	if err := json.Unmarshal(body, &update); err != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(`{"error":"Invalid JSON"}`))
		return
	}

	updateJSON := string(body)
	log.Printf("[WAKEUP] Received update_id: %d for device: %s...", update.UpdateID, pushToken[:min(len(pushToken), 10)])

	hmsAppID := os.Getenv("HUAWEI_APP_ID")
	hmsClientID := os.Getenv("HUAWEI_CLIENT_ID")
	if hmsClientID == "" {
		hmsClientID = hmsAppID
	}
	hmsClientSecret := os.Getenv("HUAWEI_CLIENT_SECRET")
	hasHMS := hmsAppID != "" && hmsClientSecret != ""

	sa, _ := getServiceAccount()
	legacyKey := os.Getenv("FCM_LEGACY_SERVER_KEY")
	hasFCM := sa != nil || legacyKey != ""

	targetHMS := provider == "hms" || (provider == "auto" && hasHMS && !hasFCM)
	targetFCM := provider == "fcm" || (provider == "auto" && hasFCM)

	if targetHMS && hasHMS {
		go sendHMSPush(hmsAppID, hmsClientID, hmsClientSecret, pushToken, update.UpdateID, updateJSON)
	}

	if targetFCM && hasFCM {
		if sa != nil {
			go sendFCMv1(sa, pushToken, update.UpdateID, updateJSON)
		} else if legacyKey != "" {
			go sendFCMLegacy(legacyKey, pushToken, update.UpdateID, updateJSON)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte(`{"status":"ok"}`))
}

func handleHealthCheck(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	sa, _ := getServiceAccount()
	hasFCM := sa != nil || os.Getenv("FCM_LEGACY_SERVER_KEY") != ""
	hasHMS := os.Getenv("HUAWEI_APP_ID") != "" && os.Getenv("HUAWEI_CLIENT_SECRET") != ""

	resp := map[string]interface{}{
		"status": "healthy",
		"fcm":    hasFCM,
		"hms":    hasHMS,
	}
	respBytes, _ := json.Marshal(resp)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write(respBytes)
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func main() {
	if len(os.Args) > 1 && os.Args[1] == "-healthcheck" {
		port := os.Getenv("PORT")
		if port == "" {
			port = "8080"
		}
		resp, err := http.Get("http://127.0.0.1:" + port + "/health")
		if err != nil || resp.StatusCode != http.StatusOK {
			os.Exit(1)
		}
		os.Exit(0)
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	http.HandleFunc("/tg/", handleTelegramWebhook)
	http.HandleFunc("/health", handleHealthCheck)

	log.Printf("Starting RemoteBot Webhook Push Gateway (Go) on :%s", port)
	if err := http.ListenAndServe(":"+port, nil); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}

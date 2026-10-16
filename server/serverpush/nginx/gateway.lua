--[[
    RemoteBot Push Gateway для OpenResty / Nginx Lua
    Обрабатывает входящие Webhooks от Telegram и доставляет Wakeup через FCM и HMS.
]]

local cjson = require("cjson.safe")
local http = require("resty.http")
local jwt = require("resty.jwt")

-- Чтение переменных окружения
local PUSH_SECRET = os.getenv("PUSH_GATEWAY_SECRET") or ""
local FIREBASE_KEY_PATH = os.getenv("FIREBASE_SERVICE_ACCOUNT") or "/etc/remotebot/firebase.json"
local HUAWEI_APP_ID = os.getenv("HUAWEI_APP_ID") or ""
local HUAWEI_CLIENT_ID = os.getenv("HUAWEI_CLIENT_ID") or HUAWEI_APP_ID
local HUAWEI_CLIENT_SECRET = os.getenv("HUAWEI_CLIENT_SECRET") or ""

-- Кэш OAuth токенов в памяти OpenResty
local token_cache = ngx.shared.push_token_cache

local function read_file(path)
    local f = io.open(path, "r")
    if not f then return nil end
    local content = f:read("*a")
    f:close()
    return content
end

-- Получение OAuth2 access_token для Google FCM v1 (RS256 JWT)
local function get_fcm_access_token()
    local cached = token_cache:get("fcm_token")
    if cached then return cached end

    local sa_raw = read_file(FIREBASE_KEY_PATH)
    if not sa_raw then
        ngx.log(ngx.ERR, "Firebase service account file not found: " .. FIREBASE_KEY_PATH)
        return nil, nil
    end

    local sa = cjson.decode(sa_raw)
    if not sa or not sa.private_key or not sa.client_email then
        return nil, nil
    end

    local now = ngx.time()
    local claim = {
        iss = sa.client_email,
        scope = "https://www.googleapis.com/auth/firebase.messaging",
        aud = "https://oauth2.googleapis.com/token",
        iat = now,
        exp = now + 3600
    }

    local jwt_token = jwt:sign(sa.private_key, {
        header = { typ = "JWT", alg = "RS256" },
        payload = claim
    })

    local httpc = http.new()
    local res, err = httpc:request_uri("https://oauth2.googleapis.com/token", {
        method = "POST",
        body = ngx.encode_args({
            grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer",
            assertion = jwt_token
        }),
        headers = { ["Content-Type"] = "application/x-www-form-urlencoded" },
        ssl_verify = false
    })

    if not res or res.status ~= 200 then
        ngx.log(ngx.ERR, "Failed to get Google OAuth token: " .. (err or res.body))
        return nil, nil
    end

    local token_data = cjson.decode(res.body)
    local token = token_data and token_data.access_token
    if token then
        token_cache:set("fcm_token", token, 3500) -- кэш на ~58 минут
    end
    return token, sa.project_id
end

-- Получение Bearer токена для Huawei HMS
local function get_hms_access_token()
    local cached = token_cache:get("hms_token")
    if cached then return cached end

    if HUAWEI_CLIENT_ID == "" or HUAWEI_CLIENT_SECRET == "" then
        return nil
    end

    local httpc = http.new()
    local res, err = httpc:request_uri("https://oauth-login.cloud.huawei.com/oauth2/v2/token", {
        method = "POST",
        body = ngx.encode_args({
            grant_type = "client_credentials",
            client_id = HUAWEI_CLIENT_ID,
            client_secret = HUAWEI_CLIENT_SECRET
        }),
        headers = { ["Content-Type"] = "application/x-www-form-urlencoded" },
        ssl_verify = false
    })

    if not res or res.status ~= 200 then
        ngx.log(ngx.ERR, "Failed to get Huawei OAuth token: " .. (err or res.body))
        return nil
    end

    local token_data = cjson.decode(res.body)
    local token = token_data and token_data.access_token
    if token then
        token_cache:set("hms_token", token, 3500)
    end
    return token
end

-- Отправка Data Push в FCM v1
local function send_fcm_push(device_token, update_id, update_json)
    local access_token, project_id = get_fcm_access_token()
    if not access_token then return false, "No FCM access token" end

    local payload = {
        message = {
            token = device_token,
            data = {
                action = "wakeup",
                update_id = tostring(update_id),
                update_json = update_json or ""
            },
            android = { priority = "HIGH" }
        }
    }

    local httpc = http.new()
    local url = string.format("https://fcm.googleapis.com/v1/projects/%s/messages:send", project_id)
    local res, err = httpc:request_uri(url, {
        method = "POST",
        body = cjson.encode(payload),
        headers = {
            ["Content-Type"] = "application/json",
            ["Authorization"] = "Bearer " .. access_token
        },
        ssl_verify = false
    })

    if not res or res.status ~= 200 then
        return false, (err or res.body)
    end
    return true
end

-- Отправка Data Push в Huawei HMS
local function send_hms_push(device_token, update_id, update_json)
    local access_token = get_hms_access_token()
    if not access_token then return false, "No HMS access token" end

    local payload = {
        validate_only = false,
        message = {
            token = { device_token },
            data = cjson.encode({
                action = "wakeup",
                update_id = tostring(update_id),
                update_json = update_json or ""
            }),
            android = {
                urgency = "HIGH",
                category = "PLAY_VOICE"
            }
        }
    }

    local httpc = http.new()
    local url = string.format("https://push-api.cloud.huawei.com/v1/%s/messages:send", HUAWEI_APP_ID)
    local res, err = httpc:request_uri(url, {
        method = "POST",
        body = cjson.encode(payload),
        headers = {
            ["Content-Type"] = "application/json",
            ["Authorization"] = "Bearer " .. access_token
        },
        ssl_verify = false
    })

    if not res or res.status ~= 200 then
        return false, (err or res.body)
    end
    return true
end

-- Маршрутизатор запросов
local uri = ngx.var.uri
local method = ngx.req.get_method()

if uri == "/health" then
    if method ~= "GET" then
        ngx.status = 405
        ngx.say(cjson.encode({ error = "Method Not Allowed" }))
        return ngx.exit(405)
    end
    ngx.header["Content-Type"] = "application/json"
    ngx.say(cjson.encode({
        status = "healthy",
        fcm = (FIREBASE_KEY_PATH ~= ""),
        hms = (HUAWEI_CLIENT_ID ~= "")
    }))
    return ngx.exit(200)
end

if method ~= "POST" then
    ngx.status = 405
    ngx.say(cjson.encode({ error = "Method Not Allowed" }))
    return ngx.exit(405)
end

-- Разбор URL: /tg/fcm/:token/:secretKey или /tg/:token/:secretKey
local parts = {}
for p in string.gmatch(uri, "[^/]+") do
    table.insert(parts, p)
end

local target_provider = "auto"
local device_token = nil
local secret_key = nil

if #parts == 4 and parts[1] == "tg" then
    target_provider = string.lower(parts[2])
    device_token = parts[3]
    secret_key = parts[4]
elseif #parts == 3 and parts[1] == "tg" then
    device_token = parts[2]
    secret_key = parts[3]
else
    ngx.status = 400
    ngx.say(cjson.encode({ error = "Invalid URL path format" }))
    return ngx.exit(400)
end

-- Проверка секретного ключа
if PUSH_SECRET ~= "" and secret_key ~= PUSH_SECRET then
    ngx.status = 401
    ngx.say(cjson.encode({ error = "Unauthorized" }))
    return ngx.exit(401)
end

-- Чтение тела запроса
ngx.req.read_body()
local body_raw = ngx.req.get_body_data()
if not body_raw or body_raw == "" then
    ngx.status = 400
    ngx.say(cjson.encode({ error = "Empty request body" }))
    return ngx.exit(400)
end

local update = cjson.decode(body_raw)
local update_id = update and update.update_id or 0

-- Выбор провайдера (с учетом query параметра ?provider=...)
local args = ngx.req.get_uri_args()
if args.provider then
    target_provider = string.lower(args.provider)
end

local ok, err_msg
if target_provider == "hms" or (target_provider == "auto" and HUAWEI_CLIENT_ID ~= "" and FIREBASE_KEY_PATH == "") then
    ok, err_msg = send_hms_push(device_token, update_id, body_raw)
else
    ok, err_msg = send_fcm_push(device_token, update_id, body_raw)
end

if ok then
    ngx.header["Content-Type"] = "application/json"
    ngx.say(cjson.encode({ ok = true, update_id = update_id }))
    return ngx.exit(200)
else
    ngx.status = 502
    ngx.say(cjson.encode({ ok = false, error = err_msg or "Failed to deliver push" }))
    return ngx.exit(502)
end


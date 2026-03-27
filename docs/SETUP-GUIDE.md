# Thought Input - Backend Setup Guide

Set up your backend before submitting your first capture. Thought Input supports several destination types — pick the one that matches your setup.

## Payload Reference

Every capture sends a POST request with this JSON body:

```json
{
  "text": "Buy milk on the way home",
  "timestamp": "2026-03-27T10:30:00Z",
  "source_platform": "macos",
  "client_version": "0.1.0",
  "capture_method": "typed",
  "idempotency_key": "550e8400-e29b-41d4-a716-446655440000",
  "device_name": "Jaco's MacBook Pro"
}
```

### Field Reference

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `text` | string | `"Buy milk"` | The captured thought (never empty) |
| `timestamp` | string (ISO 8601) | `"2026-03-27T10:30:00Z"` | When the capture was initiated |
| `source_platform` | string | `"macos"` or `"android"` | Which app sent the capture |
| `client_version` | string (semver) | `"0.1.0"` | App version |
| `capture_method` | string | `"typed"` or `"voice"` | How the text was entered |
| `idempotency_key` | string (UUID) | `"550e8400-..."` | Unique per capture, use for deduplication |
| `device_name` | string | `"Jaco's MacBook Pro"` | Human-readable device name |

---

## Supabase Setup

### 1. Create the Table

Open the **SQL Editor** in your Supabase dashboard and run:

```sql
CREATE TABLE IF NOT EXISTS captures (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text            text        NOT NULL,
    timestamp       timestamptz NOT NULL,
    source_platform text        NOT NULL,
    client_version  text        NOT NULL,
    capture_method  text        NOT NULL,
    idempotency_key uuid        UNIQUE NOT NULL,
    device_name     text        NOT NULL,
    created_at      timestamptz DEFAULT now()
);
```

The `created_at` column is optional but useful for tracking when the row was inserted (vs. when the capture happened on the device).

### 2. Enable Row Level Security (RLS)

If you're using the **anon key** (recommended for client apps), enable RLS and add an insert-only policy:

```sql
ALTER TABLE captures ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow anonymous inserts"
    ON captures
    FOR INSERT
    TO anon
    WITH CHECK (true);
```

This allows the app to insert rows but not read, update, or delete them. You can query captures from the Supabase dashboard or with a service-role key.

If you're using a **service-role key** instead, RLS is bypassed automatically — you don't need the policy above, but be aware that the service-role key has full database access.

### 3. Configure in the App

In Thought Input Settings > Add Destination > Supabase:

| Field | Value |
|-------|-------|
| **Project URL** | Your Supabase project URL, e.g. `https://abcdefgh.supabase.co` |
| **Table Name** | `captures` (or whatever you named your table) |
| **API Key** | Your anon key or service-role key (found in Settings > API) |

---

## REST (No Auth) Setup

For a simple endpoint with no authentication.

### Endpoint Requirements

Your endpoint must:
- Accept `POST` requests
- Accept `Content-Type: application/json`
- Return a `2xx` status code on success

### What the App Sends

```
POST https://your-endpoint.example.com/captures
Content-Type: application/json

{
  "text": "Buy milk",
  "timestamp": "2026-03-27T10:30:00Z",
  "source_platform": "macos",
  "client_version": "0.1.0",
  "capture_method": "typed",
  "idempotency_key": "550e8400-e29b-41d4-a716-446655440000",
  "device_name": "Jaco's MacBook Pro"
}
```

### Configure in the App

| Field | Value |
|-------|-------|
| **Endpoint URL** | Your POST endpoint URL |

---

## REST (API Key) Setup

For endpoints that require an API key in a request header.

### What the App Sends

```
POST https://your-endpoint.example.com/captures
Content-Type: application/json
X-API-Key: your-api-key-here

{
  "text": "Buy milk",
  ...same fields as above...
}
```

The header name is configurable (default: `X-API-Key`). Common alternatives: `Authorization`, `X-Auth-Token`, `Api-Key`.

### Configure in the App

| Field | Value |
|-------|-------|
| **Endpoint URL** | Your POST endpoint URL |
| **Header Name** | The HTTP header for your API key (e.g. `X-API-Key`) |
| **API Key** | Your secret API key |

---

## REST (OAuth Password) Setup

For endpoints that use OAuth 2.0 Resource Owner Password Credentials grant.

### How It Works

1. The app exchanges your username/password for an access token by POSTing to the token URL:
   ```
   POST https://auth.example.com/oauth/token
   Content-Type: application/x-www-form-urlencoded

   grant_type=password&username=you&password=secret
   ```
2. The token server responds with `{ "access_token": "...", "expires_in": 3600, ... }`
3. The app uses the token to call your data endpoint:
   ```
   POST https://api.example.com/captures
   Authorization: Bearer <access_token>
   Content-Type: application/json

   { ...capture payload... }
   ```
4. Tokens are cached and refreshed automatically. On 401, the app retries with a fresh token.

### Configure in the App

| Field | Value |
|-------|-------|
| **Endpoint URL** | Your data endpoint (receives the capture JSON) |
| **Token URL** | Your OAuth token endpoint |
| **Username** | Your OAuth username |
| **Password** | Your OAuth password |

---

## REST (OAuth Client Credentials) Setup

For server-to-server OAuth 2.0 Client Credentials grant.

### How It Works

1. The app exchanges client credentials for an access token:
   ```
   POST https://auth.example.com/oauth/token
   Content-Type: application/x-www-form-urlencoded

   grant_type=client_credentials&client_id=abc&client_secret=xyz
   ```
2. The token server responds with `{ "access_token": "...", "expires_in": 3600 }`
3. The app uses the token to call your data endpoint:
   ```
   POST https://api.example.com/captures
   Authorization: Bearer <access_token>
   Content-Type: application/json

   { ...capture payload... }
   ```

### Configure in the App

| Field | Value |
|-------|-------|
| **Endpoint URL** | Your data endpoint (receives the capture JSON) |
| **Token URL** | Your OAuth token endpoint |
| **Client ID** | Your OAuth client ID |
| **Client Secret** | Your OAuth client secret |

---

## Troubleshooting

- **"No destination configured"** — Add a destination in Settings before capturing.
- **Captures queued but not delivered** — Check your endpoint URL is reachable. Pending captures retry automatically on next app launch.
- **401 / 403 errors** — Verify your API key or OAuth credentials. For Supabase, ensure the RLS policy allows inserts.
- **Use `--debug` mode** — Launch with `--debug` flag or set `THOUGHT_INPUT_DEBUG=1` to see detailed logs in the console.

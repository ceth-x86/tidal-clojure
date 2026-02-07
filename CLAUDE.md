# tidal-clojure

Clojure CLI that authenticates with Tidal via OAuth 2.0 PKCE and lists favourite albums.

## Project Structure

```
src/tidal_clojure/
  config.clj   — URLs, client ID, scopes, token path
  auth.clj     — PKCE auth flow, token persistence/refresh, JWT decoding
  api.clj      — Tidal v2 API calls (JSON:API format)
  core.clj     — CLI entry point (-main)
```

## Running

```bash
export TIDAL_CLIENT_ID=<your-client-id>   # from developer.tidal.com
lein run auth                              # opens browser for PKCE login
lein run favorites                         # list favourite albums
```

## Dependencies

- `clj-http` — HTTP client
- `cheshire` — JSON parsing
- No Tidal wrapper libraries; all API calls are raw HTTP

## Tidal API Details

### Auth (OAuth 2.0 Authorization Code + PKCE)
- Authorize: `https://login.tidal.com/authorize`
- Token: `https://auth.tidal.com/v1/oauth2/token`
- Redirect: `http://localhost:8888/callback` (local HTTP server via `com.sun.net.httpserver`)
- Scopes: `user.read collection.read` (new-style, NOT `r_usr`/`w_usr`)
- No client secret needed (PKCE replaces it)
- Register apps at https://developer.tidal.com; add `http://localhost:8888/callback` as redirect URI

### API (v2 — JSON:API)
- Base URL: `https://openapi.tidal.com/v2/`
- Accept header: `application/vnd.api+json`
- User info: `GET /users/me`
- Favourite albums: `GET /userCollections/{userId}/relationships/albums?countryCode=XX&include=albums,albums.artists`
- Response uses `included` array — albums and artists are separate objects joined by ID

### JWT Access Token
The access token is a JWT. Payload contains `uid` (user ID) and `cc` (country code) — decoded in `auth.clj` to avoid extra API calls.

### Token Storage
Tokens saved to `~/.tidal-clojure/tokens.json`. Auto-refreshed when expired (60s safety margin).

## Known Pitfalls

### clj-http `:as :json` + `:throw-exceptions false`
clj-http defaults to `:coerce :unexceptional` — only parses JSON for 2xx responses. With `:throw-exceptions false`, non-2xx bodies stay as raw strings and keyword lookups silently return `nil`. This project uses manual cheshire parsing via `post-form` to avoid this.

### Old vs New API
- `api.tidal.com/v1/` requires legacy scopes (`r_usr`, `w_usr`) — **does not work** with developer portal tokens
- `openapi.tidal.com/v2/` uses new scopes (`user.read`, `collection.read`) — **use this one**

### Device Code Flow
Not available to third-party developer portal apps. Only PKCE authorization code flow works.

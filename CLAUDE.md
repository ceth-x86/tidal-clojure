# tidal-clojure

Clojure CLI that exports a Tidal music library (albums, artists, playlists) to CSV via the Tidal v2 API.

## Project Structure

```
src/tidal_clojure/
  config.clj   — URLs, client ID, scopes, token path
  auth.clj     — PKCE auth flow, token persistence/refresh, JWT decoding
  api.clj      — Tidal v2 API calls (JSON:API format)
  core.clj     — CLI entry point (-main), CSV export, sorting/filtering
```

## Running

```bash
export TIDAL_CLIENT_ID=<your-client-id>   # from developer.tidal.com
lein run auth                              # opens browser for PKCE login
lein run albums                            # list favourite albums
lein run albums --output albums.csv        # export to CSV
lein run artists                           # list favourite artists
lein run artists --output artists.csv      # export to CSV
lein run playlists                         # list all favourite playlists
lein run playlists mine                    # playlists you created
lein run playlists saved                   # playlists by others
lein run playlists mine --output pl.csv    # export playlist list to CSV
lein run playlists mine --detail           # export each playlist's tracks to playlists/ dir
lein run playlists mine --detail --output dir        # export to custom dir
lein run playlists mine --detail --id <playlist-id>  # export single playlist
```

## Makefile

```bash
make albums    # export albums to ~/Documents/tidal-music-catalog/albums.csv
make artists   # export artists to ~/Documents/tidal-music-catalog/artists.csv
```

## Tests

```bash
lein test
```

## Dependencies

- `clj-http` — HTTP client
- `cheshire` — JSON parsing
- No Tidal wrapper libraries; all API calls are raw HTTP

## CSV Export Columns

- **Albums**: artist, title, year, id
- **Artists**: name, id
- **Playlists list**: id, name, items, type
- **Playlist tracks** (--detail): id, artist, title, duration

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
- Favourite albums: `GET /userCollections/{userId}/relationships/albums?countryCode=XX&include=albums,albums.artists`
- Favourite artists: `GET /userCollections/{userId}/relationships/artists?countryCode=XX&include=artists`
- Favourite playlists: `GET /userCollections/{userId}/relationships/playlists?include=playlists,playlists.owners` (no `countryCode` needed)
- Playlist tracks: `GET /playlists/{id}/relationships/items?include=items,items.artists&countryCode=XX`
- Response uses JSON:API `included` array — resources (albums, artists, tracks, playlists, users) are separate objects joined by ID
- Pagination: cursor-based via `page[cursor]` query param; next cursor in `links.meta.nextCursor`
- Some playlists return 0 items (empty)

### JWT Access Token
The access token is a JWT. Payload contains `uid` (user ID, numeric) and `cc` (country code) — decoded in `auth.clj` to avoid extra API calls.

### Token Storage
Tokens saved to `~/.tidal-clojure/tokens.json`. Auto-refreshed when expired (60s safety margin).

## Known Pitfalls

### clj-http `:as :json` + `:throw-exceptions false`
clj-http defaults to `:coerce :unexceptional` — only parses JSON for 2xx responses. With `:throw-exceptions false`, non-2xx bodies stay as raw strings and keyword lookups silently return `nil`. This project uses manual cheshire parsing to avoid this.

### ISO 8601 Durations
Track durations from the v2 API are ISO 8601 strings (`PT5M11S`, `PT30S`, `PT3M`), not numeric seconds. `format-duration` in `api.clj` handles both formats.

### Playlist Owner ID Types
Owner ID from API is a string; `user_id` from JWT is numeric. Compare with `(str user_id)`.

### Old vs New API
- `api.tidal.com/v1/` requires legacy scopes (`r_usr`, `w_usr`) — **does not work** with developer portal tokens
- `openapi.tidal.com/v2/` uses new scopes (`user.read`, `collection.read`) — **use this one**

### Device Code Flow
Not available to third-party developer portal apps. Only PKCE authorization code flow works.

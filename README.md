# tidal-clojure

A command-line client for [Tidal](https://tidal.com) that authenticates via OAuth 2.0 PKCE and lists your favourite albums and artists.

## Prerequisites

- Java 11+
- [Leiningen](https://leiningen.org)
- A Tidal account
- A registered app on the [Tidal Developer Portal](https://developer.tidal.com)

## Setup

1. Register an app at https://developer.tidal.com
2. Add `http://localhost:8888/callback` as a redirect URI
3. Enable the `user.read` and `collection.read` scopes
4. Set your client ID:

```bash
export TIDAL_CLIENT_ID=<your-client-id>
```

5. Install dependencies:

```bash
lein deps
```

## Usage

### Authorize

```bash
lein run auth
```

Opens your browser to log in with Tidal. After authorizing, tokens are saved to `~/.tidal-clojure/tokens.json` and automatically refreshed when they expire.

### List favourite albums

```bash
lein run albums
```

```
1. Dark Tranquillity - The Gallery (1995)
2. Dream Theater - Six Degrees of Inner Turbulence (2002)
3. Opeth - Pale Communion (2014)
...
```

### List favourite artists

```bash
lein run artists
```

```
1. Opeth
2. Dream Theater
3. Dark Tranquillity
...
```

### List favourite playlists

```bash
lein run playlists            # all playlists
lein run playlists mine       # playlists you created
lein run playlists saved      # playlists by others
```

```
1. My Daily Mix (42 items)
2. Progressive Metal (128 items)
3. Chill Ambient (35 items)
...
```

### Export each playlist's tracks

```bash
lein run playlists mine --detail                              # to playlists/ dir
lein run playlists mine --detail --output mydir               # to custom dir
lein run playlists mine --detail --id <playlist-id>           # single playlist
```

Creates one CSV per playlist with columns: `id, artist, title, duration`.

### Export to CSV

```bash
lein run albums --output albums.csv
lein run artists --output artists.csv
lein run playlists --output playlists.csv
```

## Commands

| Command | Description |
|---------|-------------|
| `auth` | Authorize with Tidal (opens browser) |
| `albums` | List your favourite albums |
| `artists` | List your favourite artists |
| `playlists` | List all favourite playlists |
| `playlists mine` | List playlists you created |
| `playlists saved` | List playlists by others |
| `help` | Show usage |

| Option | Description |
|--------|-------------|
| `--output <path>` | Write results to a CSV file (or directory with `--detail`) |
| `--detail` | With playlists: export each playlist's tracks to a separate file |
| `--id <id>` | With `--detail`: export only the playlist with this ID |

## Makefile Targets

All targets export to `~/Documents/tidal-music-catalog/`.

```bash
make albums                                        # albums.csv
make artists                                       # artists.csv
make playlists_mine                                # playlists_mine.csv (list only)
make playlists_mine_detail                         # playlists/ dir (tracks per playlist)
make playlists_mine_one ID=<playlist-id>           # playlists/ dir (single playlist)
make playlists_saved                               # saved_playlists.csv
```

## REPL

Start a REPL:

```bash
lein repl
```

### Load namespaces

```clojure
(require '[tidal-clojure.auth :as auth])
(require '[tidal-clojure.api :as api])
(require '[tidal-clojure.config :as config])
```

### Authenticate

Run the full PKCE auth flow (opens browser):

```clojure
(auth/pkce-auth-flow!)
```

### Load existing tokens

If you've already authorized via `lein run auth`:

```clojure
(def tokens (auth/ensure-valid-tokens))
;; => {:access_token "..." :user_id 207377275 :country_code "NL" ...}
```

This loads tokens from `~/.tidal-clojure/tokens.json` and refreshes them automatically if expired.

### Fetch data

```clojure
;; Favourite albums
(def albums (api/get-favorite-albums tokens))
(count albums)
(first albums)
;; => {:id "12345" :title "Pale Communion" :artist "Opeth" :year "2014"}

;; Favourite artists
(def artists (api/get-favorite-artists tokens))
(count artists)
(first artists)
;; => {:name "Opeth" :id "67890"}

;; Favourite playlists
(def playlists (api/get-favorite-playlists tokens))
(count playlists)
(first playlists)
;; => {:id "abc-123" :name "My Mix" :numberOfItems 42 :playlistType "USER" :ownerId "207377275"}

;; Playlist tracks
(def tracks (api/get-playlist-items tokens "abc-123"))
(first tracks)
;; => {:id "111" :title "Sorceress" :artist "Opeth" :duration "5:11"}
```

### Run tests from REPL

```clojure
(require '[clojure.test :refer [run-tests]])
(require 'tidal-clojure.auth-test)
(require 'tidal-clojure.api-test)

(run-tests 'tidal-clojure.auth-test 'tidal-clojure.api-test)
```

## How it works

- **Auth**: OAuth 2.0 Authorization Code + PKCE flow. A local HTTP server on port 8888 captures the callback. No client secret needed.
- **API**: Uses the Tidal v2 API (`openapi.tidal.com/v2/`) with JSON:API format. Paginated responses are followed automatically with rate limit handling.
- **Tokens**: Saved to `~/.tidal-clojure/tokens.json`. User ID and country code are extracted from the JWT access token.

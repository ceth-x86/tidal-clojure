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
lein run favorites
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

### Export to CSV

```bash
lein run favorites --output albums.csv
lein run artists --output artists.csv
```

## Commands

| Command | Description |
|---------|-------------|
| `auth` | Authorize with Tidal (opens browser) |
| `favorites` | List your favourite albums |
| `albums` | Alias for `favorites` |
| `artists` | List your favourite artists |
| `help` | Show usage |

| Option | Description |
|--------|-------------|
| `--output <path>` | Write results to a CSV file instead of printing |

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
;; => {:title "Pale Communion" :artist "Opeth" :year "2014"}

;; Favourite artists
(def artists (api/get-favorite-artists tokens))
(count artists)
(first artists)
;; => {:name "Opeth"}
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

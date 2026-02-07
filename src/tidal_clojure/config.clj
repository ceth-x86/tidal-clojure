(ns tidal-clojure.config)

(def authorize-url "https://login.tidal.com/authorize")
(def token-url "https://auth.tidal.com/v1/oauth2/token")
(def api-base "https://openapi.tidal.com/v2/")

(def client-id
  (or (System/getenv "TIDAL_CLIENT_ID") "CzET4vdadNUFQ5JU"))

(def oauth-scope "user.read collection.read")

(def redirect-port 8888)
(def redirect-uri (str "http://localhost:" redirect-port "/callback"))

(def token-path
  (str (System/getProperty "user.home") "/.tidal-clojure/tokens.json"))

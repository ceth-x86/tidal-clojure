(ns tidal-clojure.auth
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [tidal-clojure.config :as config])
  (:import [com.sun.net.httpserver HttpServer HttpHandler]
           [java.net InetSocketAddress URLDecoder]
           [java.security MessageDigest SecureRandom]
           [java.util Base64]))

;; --- Token persistence ---

(defn save-tokens!
  "Persist tokens map to disk as JSON."
  [tokens]
  (let [f (io/file config/token-path)]
    (.mkdirs (.getParentFile f))
    (spit f (json/generate-string tokens {:pretty true}))))

(defn load-tokens
  "Load tokens from disk, or nil if missing/corrupt."
  []
  (let [f (io/file config/token-path)]
    (when (.exists f)
      (try
        (json/parse-string (slurp f) true)
        (catch Exception _ nil)))))

(defn tokens-expired?
  "True when the access token has expired (with 60s safety margin)."
  [tokens]
  (let [saved-at (:saved_at tokens 0)
        expires  (:expires_in tokens 0)
        now      (quot (System/currentTimeMillis) 1000)]
    (>= now (+ saved-at expires -60))))

;; --- HTTP helpers ---

(defn- post-form
  "POST form-encoded data and return parsed JSON body regardless of HTTP status."
  [url opts]
  (let [resp (http/post url (merge opts {:throw-exceptions false
                                         :content-type     :x-www-form-urlencoded}))
        body (:body resp)]
    (if (string? body)
      (json/parse-string body true)
      body)))

(defn- post-token [form-params]
  (post-form config/token-url {:form-params form-params}))

;; --- JWT helpers ---

(defn- decode-jwt-payload
  "Decode the payload section of a JWT and return it as a map."
  [token]
  (try
    (let [parts   (str/split token #"\.")
          payload (second parts)
          padded  (str payload (apply str (repeat (mod (- 4 (mod (count payload) 4)) 4) "=")))
          decoded (String. (.decode (Base64/getUrlDecoder) padded) "UTF-8")]
      (json/parse-string decoded true))
    (catch Exception _ nil)))

(defn- extract-user-info
  "Extract user_id and country_code from a JWT access token."
  [access-token]
  (when-let [jwt (decode-jwt-payload access-token)]
    {:user_id      (:uid jwt)
     :country_code (:cc jwt)}))

;; --- Token refresh ---

(defn refresh-access-token!
  "Use the refresh token to obtain a new access token."
  [tokens]
  (try
    (let [resp (post-token {:grant_type    "refresh_token"
                            :refresh_token (:refresh_token tokens)
                            :client_id     config/client-id
                            :scope         config/oauth-scope})
          _    (when (:error resp)
                 (throw (ex-info (str "Refresh error: " (:error resp)) resp)))
          user-info (extract-user-info (:access_token resp))
          updated (merge tokens resp user-info {:saved_at (quot (System/currentTimeMillis) 1000)})]
      (save-tokens! updated)
      updated)
    (catch Exception e
      (println "Token refresh failed:" (.getMessage e))
      nil)))

(defn ensure-valid-tokens
  "Return valid tokens: load from disk, refresh if expired, or nil."
  []
  (when-let [tokens (load-tokens)]
    (if (tokens-expired? tokens)
      (refresh-access-token! tokens)
      tokens)))

;; --- PKCE helpers ---

(defn- generate-code-verifier
  "Generate a random 64-byte URL-safe code verifier."
  []
  (let [random (SecureRandom.)
        bytes  (byte-array 64)]
    (.nextBytes random bytes)
    (-> (Base64/getUrlEncoder)
        (.withoutPadding)
        (.encodeToString bytes))))

(defn- generate-code-challenge
  "SHA-256 hash the verifier, then base64url-encode it."
  [verifier]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes verifier "US-ASCII"))]
    (-> (Base64/getUrlEncoder)
        (.withoutPadding)
        (.encodeToString digest))))

(defn- parse-query-string
  "Parse a URL query string into a map."
  [query]
  (when query
    (->> (str/split query #"&")
         (map #(str/split % #"=" 2))
         (into {} (map (fn [[k v]] [(keyword k) (URLDecoder/decode (or v "") "UTF-8")]))))))

;; --- Local callback server ---

(defn- wait-for-callback
  "Start a local HTTP server and wait for the OAuth callback.
   Returns the authorization code, or nil on error."
  []
  (let [result  (promise)
        server  (HttpServer/create (InetSocketAddress. config/redirect-port) 0)]
    (.createContext server "/callback"
                    (reify HttpHandler
                      (handle [_ exchange]
                        (let [params (parse-query-string (.getQuery (.getRequestURI exchange)))
                              body   (if (:code params)
                                       "<html><body><h2>Authorization successful!</h2><p>You can close this window.</p></body></html>"
                                       (str "<html><body><h2>Authorization failed</h2><p>" (:error params) "</p></body></html>"))
                              bytes  (.getBytes body "UTF-8")]
                          (.sendResponseHeaders exchange 200 (count bytes))
                          (with-open [os (.getResponseBody exchange)]
                            (.write os bytes))
                          (deliver result params)))))
    (.setExecutor server nil)
    (.start server)
    (try
      (let [params (deref result 300000 nil)]
        (when-not params
          (println "Timed out waiting for authorization callback."))
        params)
      (finally
        (.stop server 0)))))

;; --- PKCE Authorization Code flow ---

(defn pkce-auth-flow!
  "Run the full OAuth 2.0 Authorization Code + PKCE flow.
   Opens the browser, waits for callback, exchanges code for tokens."
  []
  (let [verifier  (generate-code-verifier)
        challenge (generate-code-challenge verifier)
        auth-url  (str config/authorize-url
                       "?response_type=code"
                       "&client_id=" config/client-id
                       "&redirect_uri=" (http/url-encode-illegal-characters config/redirect-uri)
                       "&scope=" (http/url-encode-illegal-characters config/oauth-scope)
                       "&code_challenge=" challenge
                       "&code_challenge_method=S256")]

    (println "Opening browser for Tidal authorization...")
    (println auth-url)
    (println)

    ;; Try to open browser automatically
    (try
      (.browse (java.awt.Desktop/getDesktop) (java.net.URI. auth-url))
      (catch Exception _
        (println "Could not open browser automatically. Please open the URL above.")))

    (println "Waiting for authorization callback on" config/redirect-uri "...")
    (println)

    (let [params (wait-for-callback)]
      (cond
        (nil? params)
        (do (println "Authorization timed out.") nil)

        (:error params)
        (do (println "Authorization denied:" (:error params)) nil)

        (:code params)
        (let [resp (post-token {:grant_type    "authorization_code"
                                :code          (:code params)
                                :code_verifier verifier
                                :redirect_uri  config/redirect-uri
                                :client_id     config/client-id
                                :scope         config/oauth-scope})]
          (if (:access_token resp)
            (let [user-info (extract-user-info (:access_token resp))
                  tokens    (merge resp user-info
                                   {:saved_at (quot (System/currentTimeMillis) 1000)})]
              (save-tokens! tokens)
              (println "Authorization successful!")
              (when (:user_id tokens)
                (println (str "Logged in as user " (:user_id tokens)
                              " (country: " (:country_code tokens) ")")))
              tokens)
            (do (println "Token exchange failed:" (:error resp) (:error_description resp))
                nil)))

        :else
        (do (println "Unexpected callback response:" params) nil)))))

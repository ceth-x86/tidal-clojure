(ns tidal-clojure.auth-test
  (:require [clojure.test :refer [deftest testing is]]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [tidal-clojure.auth :as auth]))

;; Access private fns via var
(def ^:private decode-jwt-payload  #'tidal-clojure.auth/decode-jwt-payload)
(def ^:private extract-user-info   #'tidal-clojure.auth/extract-user-info)
(def ^:private generate-code-verifier  #'tidal-clojure.auth/generate-code-verifier)
(def ^:private generate-code-challenge #'tidal-clojure.auth/generate-code-challenge)
(def ^:private parse-query-string  #'tidal-clojure.auth/parse-query-string)

;; --- tokens-expired? ---

(deftest tokens-expired?-test
  (testing "fresh token is not expired"
    (let [now (quot (System/currentTimeMillis) 1000)]
      (is (false? (auth/tokens-expired? {:saved_at now :expires_in 3600})))))

  (testing "old token is expired"
    (let [old (- (quot (System/currentTimeMillis) 1000) 7200)]
      (is (true? (auth/tokens-expired? {:saved_at old :expires_in 3600})))))

  (testing "token within 60s safety margin is expired"
    (let [now (quot (System/currentTimeMillis) 1000)]
      (is (true? (auth/tokens-expired? {:saved_at (- now 3590) :expires_in 3600})))))

  (testing "missing fields default to 0 and are expired"
    (is (true? (auth/tokens-expired? {})))))

;; --- save-tokens! / load-tokens round-trip ---

(deftest save-load-tokens-test
  (let [tmp-dir  (System/getProperty "java.io.tmpdir")
        tmp-path (str tmp-dir "/tidal-clojure-test-" (System/nanoTime) "/tokens.json")]
    (try
      (with-redefs [tidal-clojure.config/token-path tmp-path]
        (testing "save and load round-trip"
          (let [tokens {:access_token "abc" :refresh_token "def" :expires_in 3600 :saved_at 100}]
            (auth/save-tokens! tokens)
            (is (.exists (io/file tmp-path)))
            (is (= tokens (auth/load-tokens)))))

        (testing "load returns nil for missing file"
          (with-redefs [tidal-clojure.config/token-path (str tmp-dir "/nonexistent-" (System/nanoTime) "/tokens.json")]
            (is (nil? (auth/load-tokens)))))

        (testing "load returns nil for corrupt JSON"
          (spit tmp-path "not valid json{{{")
          (is (nil? (auth/load-tokens)))))
      (finally
        (let [f (io/file tmp-path)]
          (when (.exists f)
            (.delete f)
            (.delete (.getParentFile f))))))))

;; --- JWT decoding ---

(deftest decode-jwt-payload-test
  (let [;; Build a fake JWT: header.payload.signature
        payload  (json/generate-string {:uid 12345 :cc "NL" :scope "user.read"})
        b64      (.encodeToString (.withoutPadding (java.util.Base64/getUrlEncoder)) (.getBytes payload "UTF-8"))
        fake-jwt (str "eyJhbGciOiJSUzI1NiJ9." b64 ".fake-signature")]

    (testing "decodes valid JWT payload"
      (let [result (decode-jwt-payload fake-jwt)]
        (is (= 12345 (:uid result)))
        (is (= "NL" (:cc result)))))

    (testing "extract-user-info returns user_id and country_code"
      (let [result (extract-user-info fake-jwt)]
        (is (= 12345 (:user_id result)))
        (is (= "NL" (:country_code result)))))

    (testing "returns nil for garbage input"
      (is (nil? (decode-jwt-payload "not-a-jwt")))
      (is (nil? (decode-jwt-payload "")))
      (is (nil? (extract-user-info "garbage"))))))

;; --- PKCE ---

(deftest pkce-test
  (testing "code verifier is URL-safe base64, 86 chars"
    (let [v (generate-code-verifier)]
      (is (string? v))
      (is (= 86 (count v)))
      (is (re-matches #"[A-Za-z0-9_-]+" v))))

  (testing "code challenge is deterministic for same verifier"
    (let [v  (generate-code-verifier)
          c1 (generate-code-challenge v)
          c2 (generate-code-challenge v)]
      (is (= c1 c2))))

  (testing "code challenge differs for different verifiers"
    (let [v1 (generate-code-verifier)
          v2 (generate-code-verifier)]
      (is (not= (generate-code-challenge v1)
                (generate-code-challenge v2)))))

  (testing "code challenge is URL-safe base64"
    (let [c (generate-code-challenge (generate-code-verifier))]
      (is (re-matches #"[A-Za-z0-9_-]+" c)))))

;; --- parse-query-string ---

(deftest parse-query-string-test
  (testing "parses standard query string"
    (is (= {:code "abc123" :state "xyz"}
           (parse-query-string "code=abc123&state=xyz"))))

  (testing "decodes URL-encoded values"
    (is (= {:msg "hello world"}
           (parse-query-string "msg=hello+world"))))

  (testing "returns nil for nil input"
    (is (nil? (parse-query-string nil)))))

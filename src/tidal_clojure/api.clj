(ns tidal-clojure.api
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [tidal-clojure.config :as config]
            [tidal-clojure.auth :as auth]))

(defn- api-get
  "Authenticated GET against Tidal v2 API (JSON:API format).
   Returns {:status N :body parsed-json} for all responses."
  [tokens path & {:keys [query-params]}]
  (let [do-req (fn [t]
                 (let [resp (http/get (str config/api-base path)
                                      {:headers          {"Authorization" (str "Bearer " (:access_token t))
                                                          "accept"        "application/vnd.api+json"}
                                       :query-params     query-params
                                       :throw-exceptions false})
                       http-status (:status resp)
                       body        (let [b (:body resp)]
                                     (when (and (string? b) (seq b))
                                       (json/parse-string b true)))]
                   {:status http-status :body body}))
        {:keys [status body]} (do-req tokens)]
    (cond
      (and (>= status 200) (< status 300))
      body

      (= 401 status)
      (if-let [refreshed (auth/refresh-access-token! tokens)]
        (:body (do-req refreshed))
        (throw (ex-info "Token refresh failed — please re-authorize with `auth`" {})))

      :else
      {:api-error true :status status :body body})))

(defn- api-get-all-pages
  "Fetch all pages from a paginated v2 endpoint.
   Retries once on error with backoff. Small delay between pages to avoid rate limits."
  [tokens path query-params]
  (loop [cursor nil
         all-data []
         all-included []
         page 0]
    (when (pos? page) (Thread/sleep 200))
    (let [params (cond-> query-params
                   cursor (assoc "page[cursor]" cursor))
          resp   (api-get tokens path :query-params params)
          resp   (if (:api-error resp)
                   (do (Thread/sleep 3000)
                       (api-get tokens path :query-params params))
                   resp)]
      (when (:api-error resp)
        (throw (ex-info (str "API error after retry: " (:status resp))
                        (:body resp))))
      (let [data        (into all-data (:data resp))
            incl        (into all-included (:included resp))
            next-cursor (get-in resp [:links :meta :nextCursor])]
        (if next-cursor
          (recur next-cursor data incl (inc page))
          {:data data :included incl})))))

(defn- parse-albums
  "Parse JSON:API included array into a list of {:title :artist :year} maps."
  [included]
  (let [artists (->> included
                     (filter #(= "artists" (:type %)))
                     (reduce (fn [m a] (assoc m (:id a) (get-in a [:attributes :name]))) {}))
        albums  (->> included
                     (filter #(= "albums" (:type %)))
                     (map (fn [a]
                            (let [artist-ids  (get-in a [:relationships :artists :data])
                                  artist-names (map #(get artists (:id %) "Unknown") artist-ids)]
                              {:title  (get-in a [:attributes :title])
                               :artist (if (seq artist-names)
                                         (str/join ", " artist-names)
                                         "Unknown")
                               :year   (some-> (get-in a [:attributes :releaseDate])
                                               (subs 0 4))}))))]
    albums))

(defn get-favorite-artists
  "Fetch all favourite artists from Tidal (v2 JSON:API, all pages).
   Returns a list of maps with :name."
  [tokens]
  (let [user-id      (:user_id tokens)
        country-code (:country_code tokens)]
    (when-not user-id
      (throw (ex-info "No user_id in tokens — please re-authorize with `auth`" {})))
    (let [resp (api-get-all-pages tokens
                                  (str "userCollections/" user-id "/relationships/artists")
                                  {"countryCode" country-code
                                   "include"     "artists"})]
      (->> (:included resp)
           (filter #(= "artists" (:type %)))
           (map (fn [a] {:name (get-in a [:attributes :name])}))))))

(defn get-favorite-albums
  "Fetch all favourite albums from Tidal (v2 JSON:API, all pages)."
  [tokens]
  (let [user-id      (:user_id tokens)
        country-code (:country_code tokens)]
    (when-not user-id
      (throw (ex-info "No user_id in tokens — please re-authorize with `auth`" {})))
    (let [resp (api-get-all-pages tokens
                                  (str "userCollections/" user-id "/relationships/albums")
                                  {"countryCode" country-code
                                   "include"     "albums,albums.artists"})]
      (parse-albums (:included resp)))))

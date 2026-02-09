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
                              {:id     (:id a)
                               :title  (get-in a [:attributes :title])
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
           (map (fn [a] {:name (get-in a [:attributes :name])
                          :id   (:id a)}))))))

(defn- parse-playlists
  "Parse JSON:API included array into a list of playlist maps.
   Extracts owner ID from relationships.owners.data for mine/saved filtering."
  [included]
  (->> included
       (filter #(= "playlists" (:type %)))
       (map (fn [p]
              {:id            (:id p)
               :name          (get-in p [:attributes :name])
               :numberOfItems (get-in p [:attributes :numberOfItems])
               :playlistType  (get-in p [:attributes :playlistType])
               :ownerId       (-> p :relationships :owners :data first :id)}))))

(defn- format-duration
  "Convert ISO 8601 duration (PT5M11S) or seconds to M:SS format."
  [dur]
  (when dur
    (if (and (string? dur) (str/starts-with? dur "PT"))
      (let [m (re-find #"(\d+)M" dur)
            s (re-find #"(\d+)S" dur)
            mins (if m (Long/parseLong (second m)) 0)
            secs (if s (Long/parseLong (second s)) 0)]
        (format "%d:%02d" mins secs))
      (let [n (if (number? dur) dur (Long/parseLong (str dur)))
            mins (quot n 60)
            secs (mod n 60)]
        (format "%d:%02d" mins secs)))))

(defn- parse-tracks
  "Parse JSON:API included array into a list of {:title :artist :duration} maps."
  [included]
  (let [artists (->> included
                     (filter #(= "artists" (:type %)))
                     (reduce (fn [m a] (assoc m (:id a) (get-in a [:attributes :name]))) {}))
        tracks  (->> included
                     (filter #(= "tracks" (:type %)))
                     (map (fn [t]
                            (let [artist-ids  (get-in t [:relationships :artists :data])
                                  artist-names (map #(get artists (:id %) "Unknown") artist-ids)]
                              {:id       (:id t)
                               :title    (get-in t [:attributes :title])
                               :artist   (if (seq artist-names)
                                           (str/join ", " artist-names)
                                           "Unknown")
                               :duration (format-duration (get-in t [:attributes :duration]))}))))]
    tracks))

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

(defn get-favorite-playlists
  "Fetch all favourite playlists from Tidal (v2 JSON:API, all pages).
   Returns a list of maps with :id, :name, :numberOfItems, :playlistType, :ownerId."
  [tokens]
  (let [user-id (:user_id tokens)]
    (when-not user-id
      (throw (ex-info "No user_id in tokens — please re-authorize with `auth`" {})))
    (let [resp (api-get-all-pages tokens
                                  (str "userCollections/" user-id "/relationships/playlists")
                                  {"include" "playlists,playlists.owners"})]
      (parse-playlists (:included resp)))))

(defn get-playlist-items
  "Fetch all tracks from a specific playlist (v2 JSON:API, all pages).
   Returns a list of maps with :title, :artist, :duration."
  [tokens playlist-id]
  (let [country-code (:country_code tokens)
        resp (api-get-all-pages tokens
                                (str "playlists/" playlist-id "/relationships/items")
                                {"include"     "items,items.artists"
                                 "countryCode" country-code})]
    (parse-tracks (:included resp))))

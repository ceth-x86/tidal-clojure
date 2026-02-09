(ns tidal-clojure.core
  (:require [tidal-clojure.auth :as auth]
            [tidal-clojure.api :as api]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:gen-class))

(defn- print-usage []
  (println "Usage: lein run <command> [--output <path>]")
  (println)
  (println "Commands:")
  (println "  auth       Authorize with Tidal (opens browser)")
  (println "  albums     List your favourite albums")
  (println "  artists    List your favourite artists")
  (println "  playlists       List all favourite playlists")
  (println "  playlists mine  List playlists you created")
  (println "  playlists saved List playlists by others")
  (println "  help       Show this message")
  (println)
  (println "Options:")
  (println "  --output <path>   Write results to a CSV file (or directory with --detail)")
  (println "  --detail          With playlists: export each playlist's tracks to a separate file")
  (println "  --id <id>         With --detail: export only the playlist with this ID"))

(defn- parse-output-flag [args]
  (let [rest-args (rest args)
        idx (.indexOf (vec rest-args) "--output")]
    (when (and (>= idx 0) (< (inc idx) (count rest-args)))
      (nth rest-args (inc idx)))))

(defn- parse-detail-flag [args]
  (boolean (some #{"--detail"} (rest args))))

(defn- parse-flag-value
  "Find --flag value in args. Returns the value after the flag, or nil."
  [args flag]
  (let [rest-args (vec (rest args))
        idx (.indexOf rest-args flag)]
    (when (and (>= idx 0) (< (inc idx) (count rest-args)))
      (nth rest-args (inc idx)))))

(defn- parse-subcommand
  "Returns the first arg after the command that isn't a flag or its value."
  [args]
  (let [rest-args (vec (rest args))]
    (loop [i 0]
      (when (< i (count rest-args))
        (let [a (nth rest-args i)]
          (cond
            (= "--output" a) (recur (+ i 2))
            (= "--id" a) (recur (+ i 2))
            (= "--detail" a) (recur (inc i))
            :else a))))))

(defn- csv-escape [value]
  (let [s (str value)]
    (if (str/includes? s ",")
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- write-csv [path header rows]
  (spit path (str (str/join "," header) "\n"
                  (str/join "\n" (map #(str/join "," (map csv-escape %)) rows))
                  "\n"))
  (println (str "Wrote " (count rows) " rows to " path)))

(defn sort-albums [albums]
  (sort-by (juxt #(str/lower-case (or (:artist %) ""))
                 #(or (:year %) ""))
           albums))

(defn sort-artists [artists]
  (sort-by #(str/lower-case (or (:name %) "")) artists))

(defn sort-playlists [playlists]
  (sort-by #(str/lower-case (or (:name %) "")) playlists))

(defn sanitize-filename
  "Replace characters unsafe for filenames with underscores."
  [s]
  (-> (or s "unnamed")
      str/trim
      (str/replace #"[/\\:*?\"<>|]" "_")))

(defn filter-playlists
  "Filter playlists by ownership. subcmd is \"mine\", \"saved\", or nil (all).
   user-id is the numeric user ID from tokens; ownerId is a string from the API."
  [playlists user-id subcmd]
  (let [uid-str (str user-id)]
    (case subcmd
      "mine"  (filter #(= uid-str (:ownerId %)) playlists)
      "saved" (remove #(= uid-str (:ownerId %)) playlists)
      playlists)))

(defn- print-albums [tokens output-path]
  (let [albums (sort-albums (api/get-favorite-albums tokens))]
    (if (seq albums)
      (if output-path
        (write-csv output-path
                   ["artist" "title" "year" "id"]
                   (map (fn [a] [(:artist a) (:title a) (or (:year a) "") (:id a)]) albums))
        (doseq [[i album] (map-indexed vector albums)]
          (println (str (inc i) ". " (:artist album) " - " (:title album)
                        (when (:year album) (str " (" (:year album) ")"))))))
      (println "No favourite albums found."))))

(defn -main [& args]
  (let [cmd (first args)
        output-path (parse-output-flag args)]
    (case cmd
      "auth"
      (auth/pkce-auth-flow!)

      "albums"
      (if-let [tokens (auth/ensure-valid-tokens)]
        (print-albums tokens output-path)
        (do (println "Not authorized. Run `lein run auth` first.")
            (System/exit 1)))

      "artists"
      (if-let [tokens (auth/ensure-valid-tokens)]
        (let [artists (sort-artists (api/get-favorite-artists tokens))]
          (if (seq artists)
            (if output-path
              (write-csv output-path
                         ["name" "id"]
                         (map (fn [a] [(:name a) (:id a)]) artists))
              (doseq [[i artist] (map-indexed vector artists)]
                (println (str (inc i) ". " (:name artist)))))
            (println "No favourite artists found.")))
        (do (println "Not authorized. Run `lein run auth` first.")
            (System/exit 1)))

      "playlists"
      (if-let [tokens (auth/ensure-valid-tokens)]
        (let [subcmd    (parse-subcommand args)
              detail?   (parse-detail-flag args)
              id-filter (parse-flag-value args "--id")
              all       (api/get-favorite-playlists tokens)
              filtered  (sort-playlists (filter-playlists all (:user_id tokens) subcmd))
              playlists (if id-filter
                          (filter #(= id-filter (:id %)) filtered)
                          filtered)
              label     (case subcmd "mine" "your" "saved" "saved" "favourite")]
          (when (and id-filter (empty? playlists))
            (println (str "No playlist found with ID " id-filter)))
          (if (seq playlists)
            (if detail?
              (let [dir (io/file (or output-path "playlists"))]
                (when (and (.exists dir) (not (.isDirectory dir)))
                  (.delete dir))
                (.mkdirs dir)
                (doseq [[i p] (map-indexed vector playlists)]
                  (println (str (inc i) "/" (count playlists) " " (:name p) "..."))
                  (let [tracks (api/get-playlist-items tokens (:id p))
                        fname  (str (sanitize-filename (:name p)) " (" (:id p) ").csv")
                        fpath  (str (io/file dir fname))]
                    (if (seq tracks)
                      (write-csv fpath
                                 ["id" "artist" "title" "duration"]
                                 (map (fn [t] [(:id t) (:artist t) (:title t) (or (:duration t) "")]) tracks))
                      (println "  (no tracks)"))))
                (println (str "Done. " (count playlists) " playlists exported to " (.getPath dir))))
              (if output-path
                (write-csv output-path
                           ["name" "items" "type" "id"]
                           (map (fn [p] [(:name p) (or (:numberOfItems p) "") (or (:playlistType p) "") (:id p)]) playlists))
                (doseq [[i p] (map-indexed vector playlists)]
                  (println (str (inc i) ". " (:name p)
                                (when (:numberOfItems p) (str " (" (:numberOfItems p) " items)")))))))
            (println (str "No " label " playlists found."))))
        (do (println "Not authorized. Run `lein run auth` first.")
            (System/exit 1)))

      "help"
      (print-usage)

      ;; default
      (print-usage))))

(ns tidal-clojure.core
  (:require [tidal-clojure.auth :as auth]
            [tidal-clojure.api :as api]
            [clojure.string :as str])
  (:gen-class))

(defn- print-usage []
  (println "Usage: lein run <command> [--output <path>]")
  (println)
  (println "Commands:")
  (println "  auth       Authorize with Tidal (opens browser)")
  (println "  favorites  List your favourite albums")
  (println "  albums     Alias for favorites")
  (println "  artists    List your favourite artists")
  (println "  help       Show this message")
  (println)
  (println "Options:")
  (println "  --output <path>  Write results to a CSV file"))

(defn- parse-output-flag [args]
  (let [rest-args (rest args)
        idx (.indexOf (vec rest-args) "--output")]
    (when (and (>= idx 0) (< (inc idx) (count rest-args)))
      (nth rest-args (inc idx)))))

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

(defn- print-albums [tokens output-path]
  (let [albums (sort-albums (api/get-favorite-albums tokens))]
    (if (seq albums)
      (if output-path
        (write-csv output-path
                   ["artist" "title" "year"]
                   (map (fn [a] [(:artist a) (:title a) (or (:year a) "")]) albums))
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

      ("favorites" "albums")
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
                         ["name"]
                         (map (fn [a] [(:name a)]) artists))
              (doseq [[i artist] (map-indexed vector artists)]
                (println (str (inc i) ". " (:name artist)))))
            (println "No favourite artists found.")))
        (do (println "Not authorized. Run `lein run auth` first.")
            (System/exit 1)))

      "help"
      (print-usage)

      ;; default
      (print-usage))))

(ns tidal-clojure.core
  (:require [tidal-clojure.auth :as auth]
            [tidal-clojure.api :as api])
  (:gen-class))

(defn- print-usage []
  (println "Usage: lein run <command>")
  (println)
  (println "Commands:")
  (println "  auth       Authorize with Tidal (opens browser)")
  (println "  favorites  List your favourite albums")
  (println "  albums     Alias for favorites")
  (println "  artists    List your favourite artists")
  (println "  help       Show this message"))

(defn- print-albums [tokens]
  (let [albums (api/get-favorite-albums tokens)]
    (if (seq albums)
      (doseq [[i album] (map-indexed vector albums)]
        (println (str (inc i) ". " (:artist album) " - " (:title album)
                      (when (:year album) (str " (" (:year album) ")")))))
      (println "No favourite albums found."))))

(defn -main [& args]
  (let [cmd (first args)]
    (case cmd
      "auth"
      (auth/pkce-auth-flow!)

      ("favorites" "albums")
      (if-let [tokens (auth/ensure-valid-tokens)]
        (print-albums tokens)
        (do (println "Not authorized. Run `lein run auth` first.")
            (System/exit 1)))

      "artists"
      (if-let [tokens (auth/ensure-valid-tokens)]
        (let [artists (api/get-favorite-artists tokens)]
          (if (seq artists)
            (doseq [[i artist] (map-indexed vector artists)]
              (println (str (inc i) ". " (:name artist))))
            (println "No favourite artists found.")))
        (do (println "Not authorized. Run `lein run auth` first.")
            (System/exit 1)))

      "help"
      (print-usage)

      ;; default
      (print-usage))))

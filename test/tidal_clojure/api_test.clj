(ns tidal-clojure.api-test
  (:require [clojure.test :refer [deftest testing is]]
            [tidal-clojure.api]))

(def ^:private parse-albums #'tidal-clojure.api/parse-albums)
(def ^:private parse-playlists #'tidal-clojure.api/parse-playlists)
(def ^:private parse-tracks #'tidal-clojure.api/parse-tracks)
(def ^:private format-duration #'tidal-clojure.api/format-duration)

;; --- parse-albums ---

(deftest parse-albums-test
  (testing "parses album with single artist"
    (let [included [{:id "1" :type "artists"
                     :attributes {:name "Opeth"}}
                    {:id "100" :type "albums"
                     :attributes {:title "Pale Communion" :releaseDate "2014-08-25"}
                     :relationships {:artists {:data [{:id "1" :type "artists"}]}}}]
          result (parse-albums included)]
      (is (= 1 (count result)))
      (is (= "100" (:id (first result))))
      (is (= "Pale Communion" (:title (first result))))
      (is (= "Opeth" (:artist (first result))))
      (is (= "2014" (:year (first result))))))

  (testing "parses album with multiple artists"
    (let [included [{:id "1" :type "artists" :attributes {:name "Artist A"}}
                    {:id "2" :type "artists" :attributes {:name "Artist B"}}
                    {:id "100" :type "albums"
                     :attributes {:title "Collab Album" :releaseDate "2023-01-01"}
                     :relationships {:artists {:data [{:id "1" :type "artists"}
                                                      {:id "2" :type "artists"}]}}}]
          result (parse-albums included)]
      (is (= "Artist A, Artist B" (:artist (first result))))))

  (testing "handles album with no artist relationship"
    (let [included [{:id "100" :type "albums"
                     :attributes {:title "Mystery" :releaseDate "2020-06-15"}
                     :relationships {:artists {:data []}}}]
          result (parse-albums included)]
      (is (= "Unknown" (:artist (first result))))))

  (testing "handles album with unknown artist ID"
    (let [included [{:id "100" :type "albums"
                     :attributes {:title "Lost" :releaseDate "2021-03-01"}
                     :relationships {:artists {:data [{:id "999" :type "artists"}]}}}]
          result (parse-albums included)]
      (is (= "Unknown" (:artist (first result))))))

  (testing "handles nil releaseDate"
    (let [included [{:id "1" :type "artists" :attributes {:name "X"}}
                    {:id "100" :type "albums"
                     :attributes {:title "No Date"}
                     :relationships {:artists {:data [{:id "1" :type "artists"}]}}}]
          result (parse-albums included)]
      (is (nil? (:year (first result))))))

  (testing "returns empty list for empty included"
    (is (empty? (parse-albums [])))
    (is (empty? (parse-albums nil))))

  (testing "multiple albums are all parsed"
    (let [included [{:id "1" :type "artists" :attributes {:name "A"}}
                    {:id "2" :type "artists" :attributes {:name "B"}}
                    {:id "100" :type "albums"
                     :attributes {:title "Album 1" :releaseDate "2020-01-01"}
                     :relationships {:artists {:data [{:id "1" :type "artists"}]}}}
                    {:id "101" :type "albums"
                     :attributes {:title "Album 2" :releaseDate "2021-01-01"}
                     :relationships {:artists {:data [{:id "2" :type "artists"}]}}}]
          result (parse-albums included)]
      (is (= 2 (count result)))
      (is (= #{"Album 1" "Album 2"} (set (map :title result)))))))

;; --- parse-playlists ---

(deftest parse-playlists-test
  (testing "parses playlist with owner"
    (let [included [{:id "p1" :type "playlists"
                     :attributes {:name "My Mix" :numberOfItems 42 :playlistType "USER"}
                     :relationships {:owners {:data [{:id "123" :type "users"}]}}}]
          result (parse-playlists included)]
      (is (= 1 (count result)))
      (is (= "My Mix" (:name (first result))))
      (is (= 42 (:numberOfItems (first result))))
      (is (= "USER" (:playlistType (first result))))
      (is (= "123" (:ownerId (first result))))))

  (testing "parses multiple playlists with different owners"
    (let [included [{:id "p1" :type "playlists"
                     :attributes {:name "Playlist A" :numberOfItems 10 :playlistType "USER"}
                     :relationships {:owners {:data [{:id "123" :type "users"}]}}}
                    {:id "p2" :type "playlists"
                     :attributes {:name "Playlist B" :numberOfItems 25 :playlistType "EDITORIAL"}
                     :relationships {:owners {:data [{:id "456" :type "users"}]}}}]
          result (parse-playlists included)]
      (is (= 2 (count result)))
      (is (= #{"Playlist A" "Playlist B"} (set (map :name result))))
      (is (= #{"123" "456"} (set (map :ownerId result))))))

  (testing "returns empty list for empty included"
    (is (empty? (parse-playlists [])))
    (is (empty? (parse-playlists nil))))

  (testing "handles missing attributes and relationships"
    (let [included [{:id "p1" :type "playlists" :attributes {}}]
          result (parse-playlists included)]
      (is (= 1 (count result)))
      (is (= "p1" (:id (first result))))
      (is (nil? (:name (first result))))
      (is (nil? (:numberOfItems (first result))))
      (is (nil? (:playlistType (first result))))
      (is (nil? (:ownerId (first result)))))))

;; --- format-duration ---

(deftest format-duration-test
  (testing "formats numeric seconds to M:SS"
    (is (= "3:45" (format-duration 225)))
    (is (= "0:00" (format-duration 0)))
    (is (= "1:05" (format-duration 65)))
    (is (= "10:00" (format-duration 600))))

  (testing "formats ISO 8601 duration (PT…M…S)"
    (is (= "5:11" (format-duration "PT5M11S")))
    (is (= "0:30" (format-duration "PT30S")))
    (is (= "3:00" (format-duration "PT3M")))
    (is (= "12:05" (format-duration "PT12M5S"))))

  (testing "formats string seconds"
    (is (= "3:45" (format-duration "225"))))

  (testing "returns nil for nil input"
    (is (nil? (format-duration nil)))))

;; --- parse-tracks ---

(deftest parse-tracks-test
  (testing "parses track with single artist"
    (let [included [{:id "a1" :type "artists"
                     :attributes {:name "Opeth"}}
                    {:id "t1" :type "tracks"
                     :attributes {:title "Sorceress" :duration 255}
                     :relationships {:artists {:data [{:id "a1" :type "artists"}]}}}]
          result (parse-tracks included)]
      (is (= 1 (count result)))
      (is (= "t1" (:id (first result))))
      (is (= "Sorceress" (:title (first result))))
      (is (= "Opeth" (:artist (first result))))
      (is (= "4:15" (:duration (first result))))))

  (testing "parses track with multiple artists"
    (let [included [{:id "a1" :type "artists" :attributes {:name "Artist A"}}
                    {:id "a2" :type "artists" :attributes {:name "Artist B"}}
                    {:id "t1" :type "tracks"
                     :attributes {:title "Collab" :duration 180}
                     :relationships {:artists {:data [{:id "a1" :type "artists"}
                                                      {:id "a2" :type "artists"}]}}}]
          result (parse-tracks included)]
      (is (= "Artist A, Artist B" (:artist (first result))))))

  (testing "handles track with no artist relationship"
    (let [included [{:id "t1" :type "tracks"
                     :attributes {:title "Mystery" :duration 120}
                     :relationships {:artists {:data []}}}]
          result (parse-tracks included)]
      (is (= "Unknown" (:artist (first result))))))

  (testing "handles nil duration"
    (let [included [{:id "a1" :type "artists" :attributes {:name "X"}}
                    {:id "t1" :type "tracks"
                     :attributes {:title "No Duration"}
                     :relationships {:artists {:data [{:id "a1" :type "artists"}]}}}]
          result (parse-tracks included)]
      (is (nil? (:duration (first result))))))

  (testing "returns empty for empty included"
    (is (empty? (parse-tracks [])))
    (is (empty? (parse-tracks nil))))

  (testing "multiple tracks are all parsed"
    (let [included [{:id "a1" :type "artists" :attributes {:name "A"}}
                    {:id "t1" :type "tracks"
                     :attributes {:title "Track 1" :duration 200}
                     :relationships {:artists {:data [{:id "a1" :type "artists"}]}}}
                    {:id "t2" :type "tracks"
                     :attributes {:title "Track 2" :duration 300}
                     :relationships {:artists {:data [{:id "a1" :type "artists"}]}}}]
          result (parse-tracks included)]
      (is (= 2 (count result)))
      (is (= #{"Track 1" "Track 2"} (set (map :title result)))))))

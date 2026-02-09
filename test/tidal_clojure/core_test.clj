(ns tidal-clojure.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [tidal-clojure.core :as core]))

;; --- sort-albums ---

(deftest sort-albums-test
  (testing "sorts by artist name case-insensitively, then by year"
    (let [albums [{:artist "Opeth" :title "Pale Communion" :year "2014"}
                  {:artist "Dream Theater" :title "Metropolis Pt. 2" :year "1999"}
                  {:artist "Dream Theater" :title "Images and Words" :year "1992"}
                  {:artist "opeth" :title "Blackwater Park" :year "2001"}]
          result (core/sort-albums albums)]
      (is (= ["Images and Words" "Metropolis Pt. 2" "Blackwater Park" "Pale Communion"]
             (mapv :title result)))))

  (testing "handles nil artist"
    (let [albums [{:artist "Zebra" :title "B" :year "2020"}
                  {:artist nil :title "A" :year "2019"}]
          result (core/sort-albums albums)]
      (is (= ["A" "B"] (mapv :title result)))))

  (testing "handles nil year — sorts before albums with a year for same artist"
    (let [albums [{:artist "Opeth" :title "With Year" :year "2014"}
                  {:artist "Opeth" :title "No Year" :year nil}]
          result (core/sort-albums albums)]
      (is (= ["No Year" "With Year"] (mapv :title result)))))

  (testing "returns empty for empty input"
    (is (empty? (core/sort-albums [])))
    (is (empty? (core/sort-albums nil)))))

;; --- sort-artists ---

(deftest sort-artists-test
  (testing "sorts by name case-insensitively"
    (let [artists [{:name "Opeth"}
                   {:name "dream theater"}
                   {:name "Amorphis"}]
          result (core/sort-artists artists)]
      (is (= ["Amorphis" "dream theater" "Opeth"]
             (mapv :name result)))))

  (testing "handles nil name"
    (let [artists [{:name "Zebra"} {:name nil}]
          result (core/sort-artists artists)]
      (is (= [nil "Zebra"] (mapv :name result)))))

  (testing "returns empty for empty input"
    (is (empty? (core/sort-artists [])))
    (is (empty? (core/sort-artists nil)))))

;; --- sort-playlists ---

(deftest sort-playlists-test
  (testing "sorts by name case-insensitively"
    (let [playlists [{:name "Zen Mix" :numberOfItems 10 :playlistType "USER"}
                     {:name "ambient chill" :numberOfItems 5 :playlistType "EDITORIAL"}
                     {:name "Metal" :numberOfItems 20 :playlistType "USER"}]
          result (core/sort-playlists playlists)]
      (is (= ["ambient chill" "Metal" "Zen Mix"]
             (mapv :name result)))))

  (testing "handles nil name"
    (let [playlists [{:name "Zebra" :numberOfItems 1 :playlistType "USER"}
                     {:name nil :numberOfItems 2 :playlistType "MIX"}]
          result (core/sort-playlists playlists)]
      (is (= [nil "Zebra"] (mapv :name result)))))

  (testing "returns empty for empty input"
    (is (empty? (core/sort-playlists [])))
    (is (empty? (core/sort-playlists nil)))))

;; --- filter-playlists ---

(deftest filter-playlists-test
  (let [playlists [{:name "My Mix" :ownerId "123" :playlistType "USER"}
                   {:name "Editorial" :ownerId "456" :playlistType "EDITORIAL"}
                   {:name "Another Mine" :ownerId "123" :playlistType "USER"}]]

    (testing "mine — filters to current user's playlists"
      (let [result (core/filter-playlists playlists 123 "mine")]
        (is (= 2 (count result)))
        (is (= #{"My Mix" "Another Mine"} (set (map :name result))))))

    (testing "saved — filters to other users' playlists"
      (let [result (core/filter-playlists playlists 123 "saved")]
        (is (= 1 (count result)))
        (is (= "Editorial" (:name (first result))))))

    (testing "nil subcmd — returns all playlists"
      (is (= 3 (count (core/filter-playlists playlists 123 nil)))))

    (testing "handles numeric user-id vs string ownerId"
      (is (= 2 (count (core/filter-playlists playlists 123 "mine"))))
      (is (= 2 (count (core/filter-playlists playlists "123" "mine")))))))

;; --- sanitize-filename ---

(deftest sanitize-filename-test
  (testing "passes through normal names"
    (is (= "My Playlist" (core/sanitize-filename "My Playlist"))))

  (testing "replaces unsafe characters with underscore"
    (is (= "AC_DC Mix" (core/sanitize-filename "AC/DC Mix")))
    (is (= "A_B_C" (core/sanitize-filename "A:B\\C")))
    (is (= "what_" (core/sanitize-filename "what?"))))

  (testing "trims whitespace"
    (is (= "trimmed" (core/sanitize-filename "  trimmed  "))))

  (testing "handles nil"
    (is (= "unnamed" (core/sanitize-filename nil)))))

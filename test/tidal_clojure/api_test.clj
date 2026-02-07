(ns tidal-clojure.api-test
  (:require [clojure.test :refer [deftest testing is]]
            [tidal-clojure.api]))

(def ^:private parse-albums #'tidal-clojure.api/parse-albums)

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

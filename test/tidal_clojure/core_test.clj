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

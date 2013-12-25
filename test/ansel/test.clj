(ns ansel.test
  (:refer-clojure :exclude [char])
  (:use [ansel.views]
        [clojure.test]))

(deftest test-pagination
  ;; regular
  (is (= (paginate 1 5 (range 12)) [0 4]))
  (is (= (paginate 2 5 (range 12)) [5 9]))
  (is (= (paginate 3 5 (range 12)) [10 12]))
  ;; over
  (is (= (paginate 4 5 (range 12)) nil))
  ;; under
  (is (= (paginate 0 5 (range 12)) nil))

  ;; vec shorter than single page
  (is (= (paginate 1 5 [1]) [0 1])))

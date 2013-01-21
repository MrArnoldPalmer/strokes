(ns mrhyde.test
    (:require [domina.tester :refer [add-test run-all-tests]]
              [mrhyde :refer [patch-known-arrayish-types
                              patch-known-mappish-types
                              patch-return-value-to-clj
                              patch-args-keyword-to-fn
                              patch-args-clj-to-js]]
      ))

(def DummyLib js/DummyLib)

; patch the dummy library
(if DummyLib (do
  ; patch all seqs to also be read-only arrays for javascript interop
  (patch-known-arrayish-types)

  ; patch maps to include key based accessors on js object
  (patch-known-mappish-types)

  ;;; start patching library function calls

  ; force params 0 and 2 to be js args
  (patch-args-clj-to-js DummyLib "wrapArgs0and2" 0 2)

  ; force these functions to return cljs objects
  (patch-return-value-to-clj DummyLib "wrapReturnArgsIntoArray")
  (patch-return-value-to-clj DummyLib "wrapReturnArgsIntoObject")

  ; coerce param into function if it's a keyword
  (patch-args-keyword-to-fn DummyLib "wrapCall0on1" 0)

  ; patch both ways (chaining)
  (patch-args-clj-to-js DummyLib "wrapArraysInAndOut")
  (patch-return-value-to-clj DummyLib "wrapArraysInAndOut")
))

; is js stupid? http://stackoverflow.com/a/5115066/1010653
(defn js-arrays-equal [a b]
  (not (or (< a b) (< b a))))

(defn ^:export launch []

  (add-test "js access cljs vector as array"
    (fn []
      (let [v [1 2 3 4 5]]
        (assert (= 5 v/length))
        (assert (= 1 (aget v 0)))
        (assert (= 3 (aget v "2")))
        (assert (= 5 (aget v 4)))
        (assert (= js/undefined (aget v 5)))
        (assert (= js/undefined (aget v -1)))
        )))

  (add-test "js access lazy seq elements as array"
    (fn []
      (let [l (for [x (range 1 100) :when (odd? x)] x)]
        ; (.log js/console (str l))
        (assert (= 1 (aget l 0)))
        (assert (= 21 (aget l 10)))
        (assert (= js/undefined (aget l 70)))
        (assert (= 50 l/length))
        )))

  (add-test "js access lazy seq of vectors as array of arrays"
    (fn []
      (let [l (for [x (range 3) y (range 3) :when (not= x y)] [x y])]
        ;; l is now: ([0 1] [0 2] [1 0] [1 2] [2 0] [2 1])
        (assert (= [0 1] (aget l 0)))
        (assert (= [0 2] (aget l 1)))
        (assert (= [2 1] (aget l 5)))
        (assert (= 2 (aget (aget l 3) 1)))
        ; (assert (js-arrays-equal (array 2 1) (aget l 5)))
        (assert (= js/undefined (aget l 7)))
        (assert (= js/undefined (aget (aget l 0) 3)))
        (assert (= 6 l/length))
        )))

  (add-test "js access maps as object fields"
    (fn []
      (let [m {:one 1 :two 2 :three 3}]
        (assert (= 1 m/one))
        (assert (= 2 (aget m "two")))
        (assert (= 3 (.-three m)))
        (assert (= js/undefined (.-four m)))
        )))

  (add-test "hokey js-arrays-equal helper function"
    (fn []
        (assert (js-arrays-equal (array 0 1) (array 0 1)))
        (assert (not (js-arrays-equal (array 0 1) (array 1 0))))))

  (add-test "patch-args-clj-to-js selectively"
    (fn []
      (let [v [1,2]
            m {:one 1, :two 2}
            [r0 r1 r2] (DummyLib/wrapArgs0and2 v m m)]

        ; (.log js/console r0)
        ; (.log js/console (array 1 2))

        ; check equality where you can
        (assert (js-arrays-equal (array 1 2) r0))
        (assert (= m r1))
        (assert (not= m r2))

        ; will work because object returned unchanged
        (assert (= 1 (aget r1 "one")))
        (assert (.hasOwnProperty r1 "one"))
        ; and is actually a cljs object
        (assert (satisfies? ILookup r1))
        (assert (= 1 (:one r1)))

        ; will work because of map to obj mapping
        (assert (= 1 (aget r2 "one")))
        (assert (.hasOwnProperty r2 "one"))
        ; and is actually now a js object
        (assert (not (satisfies? ILookup r2)))
        ; (assert (= 1 (:one r2))) ; <-- error, don't know how to catch right now
        )))

(add-test "patch-return-value-to-clj"
    (fn []
      (let [ra (DummyLib/wrapReturnArgsIntoArray 0 1 2)
            rav (DummyLib/wrapReturnArgsIntoArray (array 0 1) (array 2 3) (array 4 5))
            ro (DummyLib/wrapReturnArgsIntoObject 1 2 3)
            rov (DummyLib/wrapReturnArgsIntoObject (array 0 1) (array 2 3) (array 4 5))]

        ; (.log js/console (str rov))

        ; simple array case
        (assert (= ra [0 1 2]))
        ; but it goes deep
        (assert (= rav [[0 1] [2 3] [4 5]]))

        ; simple object case
        (assert (= ro {"a" 1 "b" 2 "c" 3}))
        ; also goes deep
        (assert (= rov {"a" [0 1] "b" [2 3] "c" [4 5]}))
        )))

(add-test "patch-args-keyword-to-fn selectively"
    (fn []
      (let [m {:one 1 :two 2 :three 3}]

        ; check equality where you can
        (assert (= 1 (DummyLib/wrapCall0on1 :one m)))
        (assert (= 3 (DummyLib/wrapCall0on1 :three m)))
        (assert (= nil (DummyLib/wrapCall0on1 :four m)))
        )))

(add-test "patch everything in to-js and out to-clj"
    (fn []
      (let [ra (DummyLib/wrapArraysInAndOut 1 2 3)
            rav (DummyLib/wrapArraysInAndOut [0 1] [2 3] [4 5])
            [r1 r2 r3] (DummyLib/wrapArraysInAndOut [:a :b :c] [#{:a, :b, :c}] ["a" "b" "c"])]

        ; (.log js/console r2)
        ; (.log js/console (str (r2 0)))
        ; only array js/array arguments are returned
        (assert (= ra []))
        ; translated into their cljs equivalents
        (assert (= rav [[0 1] [2 3] [4 5]]))
        ; lost in translation
        (assert (= r1 ["a" "b" "c"]))
        (assert (= r2 [["a" "b" "c"]]))
        (assert (= r3 ["a" "b" "c"]))
        )))


  (run-all-tests "mrhyde"))
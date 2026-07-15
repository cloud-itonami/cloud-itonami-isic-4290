(ns civilworks.facts-test
  (:require [clojure.test :refer [deftest is]]
            [civilworks.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:utility-survey-provenance (facts/spec-basis "JPN"))))
  (is (= :quantitative (:threshold-model (facts/spec-basis "JPN"))))
  (is (= 2.0 (:excavation-depth-trigger-m (facts/spec-basis "JPN")))))

(deftest usa-has-a-spec-basis-with-a-different-numeric-trigger
  (is (= :quantitative (:threshold-model (facts/spec-basis "USA"))))
  (is (= 1.5 (:excavation-depth-trigger-m (facts/spec-basis "USA")))))

(deftest deu-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "DEU"))))
  (is (nil? (:excavation-depth-trigger-m (facts/spec-basis "DEU")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "USA"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions report)))))

;; ----------------------------- excavation-shoring-noncompliant? -----------------------------

(deftest jpn-excavation-shoring-is-a-real-numeric-recheck
  (is (true? (facts/excavation-shoring-noncompliant? "JPN" {:excavation-depth-m 3.5 :shoring-installed? false})))
  (is (false? (facts/excavation-shoring-noncompliant? "JPN" {:excavation-depth-m 3.5 :shoring-installed? true})))
  (is (false? (facts/excavation-shoring-noncompliant? "JPN" {:excavation-depth-m 1.0 :shoring-installed? false}))))

(deftest usa-excavation-shoring-uses-its-own-different-numeric-trigger
  (is (true? (facts/excavation-shoring-noncompliant? "USA" {:excavation-depth-m 1.5 :shoring-installed? false})))
  (is (false? (facts/excavation-shoring-noncompliant? "USA" {:excavation-depth-m 1.5 :shoring-installed? true})))
  (is (false? (facts/excavation-shoring-noncompliant? "USA" {:excavation-depth-m 1.0 :shoring-installed? false}))))

(deftest deu-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/excavation-shoring-noncompliant? "DEU" {:excavation-depth-m 10 :shoring-installed? false})))
  (is (= :qualitative (facts/excavation-shoring-noncompliant? "DEU" {:excavation-depth-m 0 :shoring-installed? true}))))

(deftest unknown-jurisdiction-returns-nil-not-a-guess
  (is (nil? (facts/excavation-shoring-noncompliant? "ATL" {:excavation-depth-m 10 :shoring-installed? false}))))

(deftest non-numeric-actual-never-fires-a-quantitative-hold
  (is (false? (facts/excavation-shoring-noncompliant? "JPN" {:excavation-depth-m nil :shoring-installed? false}))))

;; ----------------------------- catalog citation honesty -----------------------------

(deftest jpn-cites-real-excavation-and-utility-survey-law
  (let [sb (facts/spec-basis "JPN")]
    (is (re-find #"労働安全衛生規則" (:utility-survey-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:utility-survey-provenance sb)))
    (is (re-find #"第356条|掘削面のこう配" (:excavation-shoring-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:excavation-shoring-provenance sb)))))

(deftest usa-cites-real-osha-excavation-law
  (let [sb (facts/spec-basis "USA")]
    (is (re-find #"1926\.651" (:utility-survey-basis sb)))
    (is (re-find #"osha\.gov" (:utility-survey-provenance sb)))
    (is (re-find #"1926\.652" (:excavation-shoring-basis sb)))
    (is (re-find #"osha\.gov" (:excavation-shoring-provenance sb)))))

(deftest deu-cites-real-dguv-technical-rules
  (let [sb (facts/spec-basis "DEU")]
    (is (re-find #"DGUV Vorschrift 38|DGUV Vorschrift 39" (:utility-survey-basis sb)))
    (is (re-find #"bgbau-medien\.de" (:utility-survey-provenance sb)))
    (is (re-find #"DIN 4124|DGUV Regel 101-604" (:excavation-shoring-basis sb)))
    (is (re-find #"dguv\.de" (:excavation-shoring-provenance sb)))))

(deftest uncovered-jurisdiction-has-no-fabricated-catalog-entry
  (is (nil? (facts/spec-basis "ATL"))))

(ns yosoku.advisor-test
  "The mock SD-Advisor is what every test in this suite drives — no real LLM
  calls anywhere (see README Follow-ups: a real `langchain.model`-backed
  advisor is intentionally deferred past v1). These tests only prove the
  mock produces well-shaped, structurally-sound proposals — the actual
  censoring is `yosoku.governor`'s job, exercised in governor_test.clj /
  operation_test.clj."
  (:require [clojure.test :refer [deftest is testing]]
            [xmile.validate :as validate]
            [yosoku.advisor :as advisor]
            [yosoku.store :as store]))

(deftest model-propose-produces-a-structurally-valid-proposal
  (let [p (advisor/infer (store/seed-db) {:op :model/propose :model-id "cg-1"})]
    (is (= :register-model (:effect p)))
    (is (= "cg-1" (:model-id p)))
    (is (map? (:model p)))
    (is (validate/valid? (validate/validate (:model p)))
        "the mock's default template is real, executable XMILE")
    (is (>= (:confidence p) 0.6))))

(deftest model-propose-broken-flag-produces-a-structurally-invalid-model
  (testing "the :broken? deliberate-failure fixture is actually broken (sanity
            check on the test fixture itself, not just the governor's reaction)"
    (let [p (advisor/infer (store/seed-db) {:op :model/propose :model-id "cg-2" :broken? true})]
      (is (not (validate/valid? (validate/validate (:model p))))))))

(deftest model-propose-implausible-flag-is-structurally-valid-but-absurd
  (let [p (advisor/infer (store/seed-db) {:op :model/propose :model-id "cg-3" :implausible? true})]
    (is (validate/valid? (validate/validate (:model p)))
        "implausible != invalid — it's a plausibility problem, not a structural one")))

(deftest scenario-propose-produces-a-patch-against-the-current-model
  (let [st (store/seed-db)
        p (advisor/infer st {:op :scenario/propose :model-id "community-growth"
                             :change {:var "GrowthRate" :eqn "0.05"}})]
    (is (= :apply-scenario (:effect p)))
    (is (= {:variables {"GrowthRate" {:xmile/eqn "0.05"}}} (:patch p)))
    (is (= ["GrowthRate"] (:cites p)))
    (is (>= (:confidence p) 0.6))))

(deftest unroutable-op-is-a-safe-noop
  (let [p (advisor/infer (store/seed-db) {:op :not-a-real-op})]
    (is (= :noop (:effect p)))
    (is (= 0.0 (:confidence p)))))

(deftest trace-is-a-decision-grounded-audit-record
  (let [req {:op :model/propose :model-id "cg-1"}
        p (advisor/infer (store/seed-db) req)
        tr (advisor/trace req p)]
    (is (= :advisor-proposal (:t tr)))
    (is (= :model/propose (:op tr)))
    (is (= "cg-1" (:model-id tr)))
    (is (= (:confidence p) (:confidence tr)))))

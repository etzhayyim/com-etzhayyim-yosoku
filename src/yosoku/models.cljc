(ns yosoku.models
  "Canned XMILE (kotoba-lang/org-oasis-open-xmile) models used to seed the
  demo store and to exercise the SD-LLM advisor / ScenarioGovernor end to end
  offline. These are demo/test fixtures, not a model catalog — a real
  deployment seeds models from an operator's own XMILE authoring tool (or a
  hand-built `.cljc` model) via `yosoku.store/with-model`."
  (:require [xmile.model :as m]))

(defn community-growth
  "A small forecast model: Members grow via Invitations (rate = GrowthRate)
  and shrink via Attrition (2%/tick). `ComplianceFloor` is a reference
  constant that stands in for a compliance/policy floor — a value the
  ScenarioGovernor's protected-variable check never lets a scenario patch
  touch, no matter who (or what) proposes the change."
  []
  (-> (m/model "community-growth"
               {:xmile/sim-specs (m/sim-specs 0.0 24.0 {:xmile/dt 1.0})})
      (m/add-variable (m/stock "Members" "500"
                                {:xmile/inflows #{"Invitations"}
                                 :xmile/outflows #{"Attrition"}}))
      (m/add-variable (m/flow "Invitations" "Members * GrowthRate"))
      (m/add-variable (m/flow "Attrition" "Members * 0.02"))
      (m/add-variable (m/aux "GrowthRate" "0.03"))
      (m/add-variable (m/aux "ComplianceFloor" "50"))))

(defn broken-dangling-ref
  "A structurally INVALID model (a flow's equation references a variable
  that does not exist) — used to exercise the ScenarioGovernor's hard
  structural-reject path. The yosoku analog of talent.hrllm's `:bias?`
  deliberate-failure injection."
  []
  (-> (m/model "broken"
               {:xmile/sim-specs (m/sim-specs 0.0 10.0 {:xmile/dt 1.0})})
      (m/add-variable (m/stock "Widget" "10" {:xmile/inflows #{"Production"}}))
      (m/add-variable (m/flow "Production" "NoSuchVariable * 2"))))

(defn implausible-seed
  "A structurally VALID but already-absurd model (a stock seeded at 2
  million) — used to exercise the ScenarioGovernor's implausible-output
  check on a brand-new `:model/propose` (no patch involved, so the
  parameter-bound check — which only applies to `:scenario/propose` patches
  — cannot mask what implausible-output is independently catching)."
  []
  (-> (m/model "implausible"
               {:xmile/sim-specs (m/sim-specs 0.0 10.0 {:xmile/dt 1.0})})
      (m/add-variable (m/stock "Hoard" "2000000" {:xmile/inflows #{"Growth"}}))
      (m/add-variable (m/flow "Growth" "Hoard * 0.5"))))

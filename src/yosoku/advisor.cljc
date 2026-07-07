(ns yosoku.advisor
  "SD-Advisor — the *contained intelligence node*. Drafts either a brand-new
  System-Dynamics model (`:model/propose`) or a scenario intervention/patch
  to an existing registered model (`:scenario/propose`), using
  `kotoba-lang/org-oasis-open-xmile`'s EDN model shape. CRITICAL: like
  gftd-talent-actor's HR-LLM, it is a smart-but-untrusted advisor — it
  returns a *proposal*, never a committed model. Every output is censored
  downstream by `yosoku.governor` before anything touches the SSoT.

  This is a deterministic mock so the actor graph runs offline end to end
  and the governor contract is exercised without any network/API dependency.
  A real advisor would call a real LLM (kotoba-llm / `langchain.model`,
  the same `Advisor` protocol seam gftd-talent-actor's `talent.hrllm/llm-advisor`
  uses) with the SAME proposal shape — deliberately NOT wired for v1, matching
  kotoba-lang/kessai's mock-adapter-only precedent (see README Follow-ups).

  Proposal shape:
    {:summary    str
     :rationale  str
     :cites      [str ..]                        ; variables/fields referenced
     :effect     :register-model | :apply-scenario | :noop
     :model-id   str
     :model      xmile-model-map                 ; only for :register-model
     :patch      {:variables {..} :sim-specs {..}} ; only for :apply-scenario
     :confidence 0..1}"
  (:require [xmile.model :as m]
            [yosoku.models :as models]
            [yosoku.store :as store]))

(defn- propose-model
  "Draft a brand-new model. `:broken?` deliberately injects a structurally
  INVALID model (dangling reference); `:implausible?` deliberately injects a
  structurally valid but already-absurd model. Both are the yosoku analog of
  talent.hrllm's `:bias?` deliberate-failure injection — real failure modes
  the ScenarioGovernor must have something concrete to defend against."
  [{:keys [model-id broken? implausible?]}]
  (let [model (cond broken? (models/broken-dangling-ref)
                    implausible? (models/implausible-seed)
                    :else (models/community-growth))]
    {:summary (str model-id " forecast model draft")
     :rationale (cond
                  broken? "モデル雛形（意図的に壊れた参照を含む — 構造検証テスト用）"
                  implausible? "モデル雛形（意図的に非現実的な初期値を含む — 妥当性検証テスト用）"
                  :else "コミュニティ成長の標準テンプレート（招待/離脱/成長率）")
     :cites ["yosoku.models"]
     :effect :register-model
     :model-id model-id
     :model model
     :confidence (if (or broken? implausible?) 0.9 0.85)}))

(defn- propose-scenario
  "Draft a scenario intervention: a patch to one variable's equation on an
  existing registered model. `change` is `{:var name :eqn eqn-string}`.
  Confidence is a simple, ADVISORY heuristic only (bigger requested swings
  read as less certain) — it is never what makes the swing safe; the
  ScenarioGovernor independently recomputes the actual bound violation from
  the base model and the patch, never from this self-report."
  [st {:keys [model-id change]}]
  (let [{:keys [var eqn]} change
        base (store/model-of st model-id)
        old-eqn (some-> (m/lookup base var) :xmile/eqn)]
    {:summary (str model-id "." var " を " (or old-eqn "?") " → " eqn " に変更提案")
     :rationale "シナリオ介入の提案（既存モデルへの1変数パッチ）。妥当性はシミュレーション結果で判断すること。"
     :cites [var]
     :effect :apply-scenario
     :model-id model-id
     :patch {:variables {var {:xmile/eqn eqn}}}
     :confidence 0.8}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :model-id id ...op-specific...}"
  [st {:keys [op] :as request}]
  (case op
    :model/propose (propose-model request)
    :scenario/propose (propose-scenario st request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :model-id (:model-id request) :confidence 0.0}))

;; ───────────────────────── Advisor protocol ─────────────────────────
;; The advisor is injected into the ScenarioActor, so the contained
;; intelligence node is a swap: a deterministic mock for dev/tests, or a real
;; LLM in production. Either way its output is a PROPOSAL the ScenarioGovernor
;; still censors — the single invariant never depends on which advisor ran.

(defprotocol Advisor
  (-advise [advisor store request] "store + request → proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(defn trace
  "Decision-grounded audit record — the advisor's interpretable rationale is
  a key asset (scenario review, audits). Persisted to the `:audit` channel."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :model-id (:model-id request)
   :summary (:summary proposal)
   :rationale (:rationale proposal)
   :cites (:cites proposal)
   :confidence (:confidence proposal)})

(ns civilworks.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-4290`: this
  repo previously had NO demo page and no generator at all. This
  namespace drives the REAL actor stack (`civilworks.operation` ->
  `civilworks.governor` -> `civilworks.phase` -> `civilworks.store`)
  through a scenario adapted from this repo's own `civilworks.sim` demo
  driver (`clojure -M:dev:run`, confirmed BEFORE writing this file to
  produce a sensible ledger against the real seeded site ids
  `site-1`..`site-8`), then renders that run's ACTUAL output.

  ## Nothing on the page is hand-typed

  Every id, number, status, rule name, violation detail, record id,
  legal citation, contact address and coverage count below is read back
  out of the real objects the run produced:

    - the run log             <- the langgraph final state of each
                                 `g/run*` (`:disposition` + the `:audit`
                                 channel)
    - the site table          <- `store/all-sites` AFTER the run (so the
                                 committed `:log-site-record` patches
                                 are visible)
    - the HARD-hold table     <- the `:violations` maps the governor
                                 itself built (`:rule` + `:detail`)
    - the governor contract   <- `governor/closed-op-allowlist`,
                                 `governor/high-stakes`,
                                 `governor/confidence-floor`,
                                 `governor/supply-order-cost-threshold-usd`
    - the phase gate          <- `phase/phases` / `phase/default-phase`
    - the audit ledger        <- `store/ledger`
    - the artifact registries <- the four `store/*-history` collections
    - the notice dispatch log <- `notify/sent-log` of the mock notifier
    - jurisdiction coverage   <- `facts/coverage`

  ## Determinism

  Deterministic by construction: the whole stack is pure + an in-memory
  checkpointer, no jurisdiction/registry function reads a clock, and
  `store/all-sites` sorts by id. Nothing in the page body is a
  timestamp or a random id, so two runs from the same seed are
  byte-identical (`clojure -M:dev:render-html a.html && clojure
  -M:dev:render-html b.html && cmp a.html b.html`). Should this actor
  ever grow a clock, inject an epoch-ms constant from the caller --
  do not read one here.

  ## Build-time invariant

  `-main` REFUSES to write the file when the resulting ledger contains
  zero `:governor-hold` facts. The whole point of this console is to
  show a governed actor holding proposals that never reach a human; a
  scenario that silently stopped producing HARD holds would render a
  page that lies about the actor's posture. Making that a throw turns
  the requirement into a build-time invariant rather than a convention.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [civilworks.facts :as facts]
            [civilworks.governor :as governor]
            [civilworks.notify :as notify]
            [civilworks.operation :as op]
            [civilworks.phase :as phase]
            [civilworks.store :as store]
            [langgraph.graph :as g]))

;; ----------------------------- the real run -----------------------------

(def ^:private operator
  "The site-supervisor operator context at the fully-rolled-out phase."
  {:actor-id "op-1" :actor-role :site-supervisor :phase 3})

(def ^:private early-rollout-operator
  "The SAME operator at rollout phase 1 (`assisted-logging`) -- used once
  below to show that `civilworks.phase`'s gate is a real, separate layer:
  a proposal the governor found completely clean is still held when its
  op is not yet enabled for the current phase."
  (assoc operator :phase 1))

(defn run-demo!
  "Runs a fresh seeded store through a scenario that reaches every
  disposition this actor can produce, and returns
  `{:db .. :notifier .. :runs [..]}`.

  Clean / human paths:
    1. `:log-site-record` on site-1 (AUTO-COMMITS at phase 3 -- data
       logging, no capital or safety risk).
    2. `:schedule-construction-operation` on site-1 (clean, verified,
       survey complete, JPN legal basis cited -- AUTO-COMMITS at phase
       3, deliberately unlike the sibling demolition/road-rail actors).
    3. `:flag-safety-concern` on site-1 -- ALWAYS escalates to a human
       at every phase; approved, committed, and the safety-concern
       notice actually fanned out over mail + phone to site-1's two
       `:safety-contacts`.
    4. `:log-site-record` recording the concern's resolution
       (AUTO-COMMITS) -- which is what re-opens scheduling for site-1.
    5. `:order-supplies` at 800 USD, below the cost threshold
       (AUTO-COMMITS).
    6. `:order-supplies` at 9000 USD, above the threshold (escalates;
       human approves).
    7. `:order-supplies` on site-7 at 25000 USD (escalates; human
       REJECTS -- an `:approval-rejected` hold, the one hold in this
       run that a human DID produce).
    8/9. site-7 (USA, at the 1.5 m OSHA trigger but compliant because
       shoring is installed) and site-8 (DEU, honestly `:qualitative` --
       no fabricated numeric trigger) schedule proposals AUTO-COMMIT,
       proving the jurisdiction split is real.

  HARD holds -- none of these ever reaches a human:
    - forbidden action class: a `:log-site-record` whose patch carries a
      `:heavy-equipment-control?` marker (defense-in-depth check 3 --
      this actor's own mock advisor never sets it, so this is what a
      COMPROMISED advisor would be stopped by).
    - no legal basis: site-2's jurisdiction (`ATL`) is not in
      `civilworks.facts/catalog`, so no requirements may be invented.
    - site not verified (+ utility survey incomplete): site-3.
    - utility survey incomplete: site-4.
    - excavation-shoring noncompliant: site-5, independently recomputed
      from the site's OWN recorded depth/shoring fields.
    - unresolved safety concern: site-6.
    - unknown op: `:direct-equipment-command` is outside the closed
      four-op allowlist.
    - phase-disabled: the SAME clean site-1 schedule proposal replayed
      at rollout phase 1."
  []
  (let [db       (store/seed-db)
        notifier (notify/mock-notifier)
        actor    (op/build db {:notifier notifier})
        runs     (atom [])
        exec!    (fn exec!
                   ([tid label request] (exec! tid label request operator))
                   ([tid label request ctx]
                    (let [st (:state (g/run* actor {:request request :context ctx}
                                             {:thread-id tid}))]
                      (swap! runs conj {:id tid :label label :request request
                                        :context ctx :state st})
                      st)))
        resume!  (fn [tid status by]
                   (let [st (:state (g/run* actor {:approval {:status status :by by}}
                                            {:thread-id tid :resume? true}))]
                     (swap! runs (fn [rs] (mapv #(cond-> % (= tid (:id %)) (assoc :state st)) rs)))
                     st))]

    (exec! "t01" "現場記録更新 (progress log)"
           {:op :log-site-record :subject "site-1"
            :patch {:id "site-1" :utility-strike-detected? false}})

    (exec! "t02" "屋外走路排水路整地スケジュール提案"
           {:op :schedule-construction-operation :subject "site-1"
            :project-type :outdoor-sports-facility
            :window {:proposed-start-date "2026-08-01" :proposed-end-date "2026-08-10"}
            :notes "屋外走路排水路整地工事"})

    (exec! "t03" "埋設ケーブル疑い -- 安全性懸念フラグ"
           {:op :flag-safety-concern :subject "site-1"
            :concern-type :utility-strike
            :concern-description "排水路整地中に地中埋設ケーブルらしき物を確認、追加調査が必要。"})
    (resume! "t03" :approved "op-1")

    (exec! "t04" "現場記録更新 (concern resolved after inspection)"
           {:op :log-site-record :subject "site-1"
            :patch {:id "site-1" :safety-concern-unresolved? false}})

    (exec! "t05" "資材発注提案 (below cost threshold)"
           {:op :order-supplies :subject "site-1"
            :items ["drainage-pipe-100mm" "track-surface-aggregate"]
            :cost-usd 800 :vendor "Local Civil Supply Co."})

    (exec! "t06" "土留めボックス賃借提案 (above cost threshold)"
           {:op :order-supplies :subject "site-1"
            :items ["trench-shoring-box"] :cost-usd 9000 :vendor "Heavy Civil Rentals"})
    (resume! "t06" :approved "op-1")

    (exec! "t07" "大型調達提案 (above threshold, human rejects)"
           {:op :order-supplies :subject "site-7"
            :items ["tower-foundation-rebar-cage" "crane-mat-set"]
            :cost-usd 25000 :vendor "Riverside Heavy Civil"})
    (resume! "t07" :rejected "op-1")

    (exec! "t08" "重機直接操作マーカー付きの記録更新 (compromised advisor)"
           {:op :log-site-record :subject "site-1"
            :patch {:id "site-1" :heavy-equipment-control? true}})

    (exec! "t09" "未登録法域 (ATL) のスケジュール提案"
           {:op :schedule-construction-operation :subject "site-2"
            :project-type :industrial-plant :window {}})

    (exec! "t10" "現場未検証のスケジュール提案"
           {:op :schedule-construction-operation :subject "site-3"
            :project-type :pipeline :window {}})

    (exec! "t11" "事前調査未完了のスケジュール提案"
           {:op :schedule-construction-operation :subject "site-4"
            :project-type :power-line :window {}})

    (exec! "t12" "土止め支保工未設置のスケジュール提案"
           {:op :schedule-construction-operation :subject "site-5"
            :project-type :industrial-plant :window {}})

    (exec! "t13" "未解決の安全性懸念があるスケジュール提案"
           {:op :schedule-construction-operation :subject "site-6"
            :project-type :pipeline :window {}})

    (exec! "t14" "許可オペレーション外の直接指令"
           {:op :direct-equipment-command :subject "site-1"})

    (exec! "t15" "USA (1.5m トリガー、土留め設置済み) スケジュール提案"
           {:op :schedule-construction-operation :subject "site-7"
            :project-type :power-line
            :window {:proposed-start-date "2026-09-01" :proposed-end-date "2026-09-10"}})

    (exec! "t16" "DEU/EU (qualitative -- 数値トリガーを創作しない) スケジュール提案"
           {:op :schedule-construction-operation :subject "site-8"
            :project-type :pipeline
            :window {:proposed-start-date "2026-09-15" :proposed-end-date "2026-09-25"}})

    (exec! "t17" "同一のクリーンなスケジュール提案を rollout phase 1 で再実行"
           {:op :schedule-construction-operation :subject "site-1"
            :project-type :outdoor-sports-facility
            :window {:proposed-start-date "2026-10-01" :proposed-end-date "2026-10-10"}}
           early-rollout-operator)

    {:db db :notifier notifier :runs @runs}))

;; ----------------------------- run classification -----------------------------

(defn- audit-of [run] (get-in run [:state :audit] []))

(defn- audit-has? [run t] (boolean (some #(= t (:t %)) (audit-of run))))

(defn- audit-first [run t] (first (filter #(= t (:t %)) (audit-of run))))

(defn- run-path
  "Which of this actor's five real dispositions the run actually landed
  on -- derived from the langgraph final state, never asserted."
  [run]
  (let [d (get-in run [:state :disposition])]
    (cond
      (and (= :commit d) (audit-has? run :approval-granted)) :human-approved
      (= :commit d)                                          :auto-commit
      (audit-has? run :approval-rejected)                    :human-rejected
      (seq (:basis (audit-first run :governor-hold)))         :hard-hold
      (audit-has? run :governor-hold)                        :phase-hold
      :else                                                  :unknown)))

(def ^:private path-cell
  {:auto-commit    "<span class=\"ok\">auto-commit</span>"
   :human-approved "<span class=\"ok\">human approved</span>"
   :human-rejected "<span class=\"warn\">human rejected</span>"
   :hard-hold      "<span class=\"critical\">HARD hold &middot; never reaches a human</span>"
   :phase-hold     "<span class=\"critical\">phase hold &middot; never reaches a human</span>"
   :unknown        "<span class=\"muted\">unknown</span>"})

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- abbrev [s n]
  (let [s (str s)]
    (if (> (count s) n) (str (subs s 0 n) "…") s)))

(defn- nm [v] (if (keyword? v) (name v) (str v)))

(defn- kws [coll] (str/join ", " (sort (map nm coll))))

(defn- yes-no [v]
  (cond (true? v)  "<span class=\"ok\">yes</span>"
        (false? v) "<span class=\"warn\">no</span>"
        :else      "<span class=\"muted\">not recorded</span>"))

(defn- cite-cell
  "One cited fact. URLs stay clickable; long statute text is abbreviated
  in the cell but kept whole in `title=` so nothing is lost."
  [c]
  (let [s (nm c)]
    (if (str/starts-with? s "http")
      (str "<a href=\"" (esc s) "\">" (esc s) "</a>")
      (str "<span title=\"" (esc s) "\">" (esc (abbrev s 64)) "</span>"))))

(defn- code-list
  "Each item in its own <code>. NOTE: escape each item BEFORE inserting
  markup -- joining with a markup separator and escaping afterwards would
  escape the tags themselves and leak `</code>, <code>` as visible text."
  [coll]
  (str/join ", " (map #(str "<code>" (esc (nm %)) "</code>") coll)))

(defn- basis-cell [{:keys [t basis]}]
  (cond
    (empty? basis) "<span class=\"muted\">—</span>"
    (contains? #{:governor-hold :approval-rejected} t) (code-list basis)
    :else (str/join "<br>" (map cite-cell basis))))

(defn- row [& cells]
  (str "        <tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- rows [xs] (str/join "\n" xs))

(defn- table [headers body-rows]
  (str "    <table>\n"
       "      <thead><tr>" (apply str (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n" (rows body-rows) "\n      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

;; ----------------------------- sections -----------------------------

(defn- run-rows [runs]
  (for [{:keys [id label request] :as r} runs
        :let [path (run-path r)
              hold (audit-first r :governor-hold)
              req  (audit-first r :approval-requested)
              prop (audit-first r :advisor-proposal)]]
    (row (str "<code>" (esc id) "</code>")
         (str "<code>" (esc (nm (:op request))) "</code>")
         (esc (:subject request))
         (esc label)
         (str "phase " (esc (get-in r [:context :phase])))
         (esc (format "%.2f" (double (or (:confidence prop) 0.0))))
         (get path-cell path)
         (cond
           (seq (:basis hold)) (code-list (:basis hold))
           (:phase-reason hold) (str "<code>" (esc (nm (:phase-reason hold))) "</code>")
           (audit-has? r :approval-rejected) "<code>approver-rejected</code>"
           req (str "<code>" (esc (nm (:reason req))) "</code>")
           :else "<span class=\"muted\">—</span>"))))

(defn- site-rows [db ledger]
  (for [{:keys [id name jurisdiction project-type site-verified?
                utility-survey-completed? utility-strike-detected?
                safety-concern-unresolved? excavation-depth-m shoring-installed?
                status safety-contacts]} (store/all-sites db)
        :let [last-fact (last (filter #(= id (:subject %)) ledger))
              sb        (facts/spec-basis jurisdiction)
              trigger   (:excavation-depth-trigger-m sb)
              noncomp   (facts/excavation-shoring-noncompliant? jurisdiction
                                                                {:excavation-depth-m excavation-depth-m
                                                                 :shoring-installed? shoring-installed?})]]
    (row (str "<code>" (esc id) "</code>")
         (esc name)
         (str "<code>" (esc jurisdiction) "</code>")
         (str "<code>" (esc (nm project-type)) "</code>")
         (yes-no site-verified?)
         (yes-no utility-survey-completed?)
         (if (number? excavation-depth-m)
           (str "<span class=\"num\">" (esc excavation-depth-m) " m</span>"
                (cond
                  (number? trigger) (str " <span class=\"muted\">/ trigger " (esc trigger) " m</span>")
                  (nil? sb)         " <span class=\"muted\">/ no spec-basis on file</span>"
                  :else             " <span class=\"muted\">/ no numeric trigger</span>"))
           "<span class=\"muted\">not recorded</span>")
         (yes-no shoring-installed?)
         ;; The exact three-valued return of `facts/excavation-shoring-
         ;; noncompliant?`. `false` means "no bright-line violation" -- it
         ;; does NOT assert the site is affirmatively compliant (a site
         ;; with no recorded depth also returns false), so do not label it
         ;; "compliant" and overstate what the actor actually verified.
         (cond
           (true? noncomp)          "<span class=\"critical\">noncompliant</span>"
           (= :qualitative noncomp) "<span class=\"muted\">qualitative (no bright line)</span>"
           (false? noncomp)         "<span class=\"ok\">no violation</span>"
           :else                    "<span class=\"muted\">no spec-basis</span>")
         (yes-no utility-strike-detected?)
         (yes-no safety-concern-unresolved?)
         (str "<code>" (esc (nm status)) "</code>")
         (str "<span class=\"num\">" (count safety-contacts) "</span>")
         (if last-fact
           (str "<code>" (esc (nm (:t last-fact))) "</code>")
           "<span class=\"muted\">no activity</span>"))))

(defn- hold-rows [ledger]
  (for [{:keys [op subject violations confidence phase-reason]}
        (filter #(= :governor-hold (:t %)) ledger)
        :let [vs (if (seq violations)
                   violations
                   [{:rule phase-reason
                     :detail "rollout phase gate: このオペレーションは現在の phase では書き込みが有効化されていない"}])]
        {:keys [rule detail]} vs]
    (row (str "<code>" (esc (nm op)) "</code>")
         (esc subject)
         (str "<code class=\"critical\">" (esc (nm rule)) "</code>")
         (esc detail)
         (esc (format "%.2f" (double (or confidence 0.0)))))))

(defn- governor-contract-rows []
  (let [phase3 (get phase/phases phase/default-phase)]
    [(row "closed op allowlist (HARD check 1)"
          (str "<code>" (esc (kws governor/closed-op-allowlist)) "</code>"))
     (row "always escalates to a human, at every phase (<code>high-stakes</code>)"
          (str "<code>" (esc (kws governor/high-stakes)) "</code>"))
     (row (str "may auto-commit at phase " phase/default-phase " when the governor is clean")
          (str "<code>" (esc (kws (:auto phase3))) "</code>"))
     (row "confidence floor (below this, escalate)"
          (str "<span class=\"num\">" (esc governor/confidence-floor) "</span>"))
     (row "supply-order cost threshold (above this, escalate regardless of confidence)"
          (str "<span class=\"num\">" (esc governor/supply-order-cost-threshold-usd) " USD</span>"))]))

(defn- phase-rows []
  (for [p (sort (keys phase/phases))
        :let [{:keys [label writes auto]} (get phase/phases p)]]
    (row (str "<span class=\"num\">" (esc p) "</span>"
              (when (= p phase/default-phase) " <span class=\"badge\">default</span>"))
         (str "<code>" (esc label) "</code>")
         (if (seq writes) (str "<code>" (esc (kws writes)) "</code>") "<span class=\"muted\">none</span>")
         (if (seq auto) (str "<code>" (esc (kws auto)) "</code>") "<span class=\"muted\">none</span>"))))

(defn- ledger-rows [ledger]
  (for [{:keys [t op subject disposition summary] :as f} ledger]
    (row (str "<code class=\"" (case t
                                 :committed "ok"
                                 :governor-hold "critical"
                                 :approval-rejected "warn"
                                 "muted") "\">" (esc (nm t)) "</code>")
         (str "<code>" (esc (nm op)) "</code>")
         (esc subject)
         (str "<code>" (esc (nm disposition)) "</code>")
         (if summary
           (str "<span title=\"" (esc summary) "\">" (esc (abbrev summary 72)) "</span>")
           "<span class=\"muted\">—</span>")
         (basis-cell f))))

(defn- artifact-rows [kind records]
  (for [r records]
    (row (str "<code>" (esc kind) "</code>")
         (str "<code>" (esc (get r "record_id")) "</code>")
         (esc (get r "kind"))
         (str "<code>" (esc (get r "site_id")) "</code>")
         (str "<code>" (esc (get r "jurisdiction")) "</code>")
         (yes-no (get r "immutable")))))

(defn- notice-rows [notifier]
  (for [{:keys [status channel to subject message body]} (notify/sent-log notifier)]
    (row (str "<code>" (esc (nm channel)) "</code>")
         (str "<code>" (esc to) "</code>")
         (if (= :sent status)
           (str "<span class=\"ok\">" (esc (nm status)) "</span>")
           (str "<span class=\"critical\">" (esc (nm status)) "</span>"))
         (let [txt (or subject message)]
           (str "<span title=\"" (esc txt) "\">" (esc (abbrev txt 64)) "</span>"))
         (if body
           (str "<span title=\"" (esc body) "\">" (esc (abbrev body 72)) "</span>")
           "<span class=\"muted\">— (phone channel)</span>"))))

(defn- jurisdiction-rows []
  (for [iso3 (sort (keys facts/catalog))
        :let [{:keys [name owner-authority threshold-model excavation-depth-trigger-m
                      utility-survey-provenance excavation-shoring-provenance]}
              (facts/spec-basis iso3)]]
    (row (str "<code>" (esc iso3) "</code>")
         (esc name)
         (esc owner-authority)
         (str "<code>" (esc (nm threshold-model)) "</code>")
         (if (number? excavation-depth-trigger-m)
           (str "<span class=\"num\">" (esc excavation-depth-trigger-m) " m</span>")
           "<span class=\"muted\">none — never fabricated</span>")
         (str (cite-cell utility-survey-provenance) "<br>" (cite-cell excavation-shoring-provenance)))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the whole operator-console document from the result of
  `run-demo!` (or any other real run of this actor)."
  [{:keys [db notifier runs]}]
  (let [ledger    (vec (store/ledger db))
        holds     (filter #(= :governor-hold (:t %)) ledger)
        commits   (filter #(= :committed (:t %)) ledger)
        approved  (filter #(audit-has? % :approval-granted) runs)
        rejected  (filter #(audit-has? % :approval-rejected) runs)
        cov       (facts/coverage)]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"ja\">\n<head>\n"
     "<meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
     "<meta name=\"color-scheme\" content=\"light\">\n"
     "<title>cloud-itonami-isic-4290 &middot; その他の土木工事 Operator Console</title>\n"
     "<meta name=\"description\" content=\"Construction of other civil engineering projects (ISIC 4290) — governed coordination actor operator console, generated at build time from a real actor run.\">\n"
     "<style>" (jp-go-dds.skin/dds+skin) "</style>\n"
     "</head>\n<body>\n"

     "<header class=\"bar\">\n"
     "  <h1>その他の土木工事プロジェクト (ISIC 4290) — Operator Console</h1>\n"
     "</header>\n"
     "<p class=\"subtitle\">産業プラント土木 / パイプライン / 送電線 / 屋外スポーツ施設。"
     "<strong>調整専用アクター</strong> — 重機の直接操作も構造物完成サインオフも行わない"
     "（現場監督・建築主事の専権事項）。すべての提案は <code>:effect :propose</code> のみ。</p>\n"
     "<p><span class=\"badge\">read-only sample</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">" (count runs) " operations</span> "
     "<span class=\"badge\">" (count commits) " committed</span> "
     "<span class=\"badge\">" (count holds) " HARD/phase holds</span> "
     "<span class=\"badge\">" (count approved) " human-approved</span> "
     "<span class=\"badge\">" (count rejected) " human-rejected</span></p>\n"

     "<main>\n"

     (section
      "この run で実行された全オペレーション"
      (str "1 run = 1 langgraph StateGraph 実行（intake → advise → govern → decide → "
           "commit | hold | request-approval）。下の disposition と reason は、"
           "実行後の最終 state と <code>:audit</code> チャネルから読み出したもので、"
           "このページに手で書いた値は一つもない。"
           "<code>clojure -M:dev:render-html</code> で再生成される。")
      (table ["thread" "op" "site" "内容" "rollout phase" "advisor confidence" "disposition" "reason"]
             (run-rows runs)))

     (section
      (str "HARD holds — 人間に一度も到達しない拒否 (" (count holds) " 件)")
      (str "governor の 8 つの HARD チェックはすべて un-overridable — 承認者が居ても覆せない。"
           "<code>rule</code> と <code>detail</code> は <code>civilworks.governor</code> 自身が"
           "組み立てた <code>:violations</code> マップそのもの。最後の 1 行は governor が"
           "完全にクリーンだったにもかかわらず <code>civilworks.phase</code> の rollout gate が"
           "止めたもので、2 層が独立に効いていることを示す。")
      (table ["op" "site" "rule" "detail" "advisor confidence"]
             (hold-rows ledger)))

     (section
      "現場台帳 (run 実行後の SSoT)"
      (str "コミットされた <code>:log-site-record</code> の patch が反映された後の "
           "<code>civilworks.store/all-sites</code>。"
           "「土止め判定」列は <code>civilworks.facts/excavation-shoring-noncompliant?</code> を"
           "この場で独立に再計算したもので、提案の自己申告は一切見ていない。")
      (table ["site" "名称" "法域" "工種" "現場検証済" "事前調査完了" "掘削深さ" "土止め設置"
              "土止め判定" "埋設物損壊" "未解決懸念" "status" "安全連絡先" "最終 ledger fact"]
             (site-rows db ledger)))

     (section
      "Civil Works Governor の契約"
      (str "以下はすべて <code>civilworks.governor</code> / <code>civilworks.phase</code> の"
           "実際の var を読み出して表示している（ドキュメントの再入力ではない）。")
      (table ["項目" "値"] (governor-contract-rows)))

     (section
      "Rollout phase gate"
      (str "<code>civilworks.phase/phases</code> そのもの。"
           "<code>:flag-safety-concern</code> はどの phase の <code>:auto</code> 集合にも"
           "入っていない — これは将来解除される rollout マイルストーンではなく恒久的な構造。")
      (table ["phase" "label" "書き込み可能な op" "auto-commit 可能な op"] (phase-rows)))

     (section
      (str "監査台帳 (" (count ledger) " facts)")
      (str "append-only の決定事実ログ。commit / hold のすべてがここに残る。"
           "<code>basis</code> 列は hold では governor の rule 名、commit では advisor が"
           "実際に引用した法令原文と出典 URL（セル内は省略表示、全文は title 属性に保持）。")
      (table ["fact" "op" "site" "disposition" "summary" "basis"] (ledger-rows ledger)))

     (section
      "生成された調整成果物 (coordination artifacts)"
      (str "この actor が実際に SSoT へ書いた append-only レコード。"
           "record id は <code>civilworks.registry</code> が法域スコープの連番から生成したもので、"
           "どれも「重機を動かした」記録ではなく提案・記録・フラグの控えである。")
      (table ["履歴" "record id" "kind" "site" "法域" "immutable"]
             (concat (artifact-rows "site-record-log" (store/site-record-log-history db))
                     (artifact-rows "schedule-proposal" (store/schedule-proposal-history db))
                     (artifact-rows "safety-concern-flag" (store/safety-concern-flag-history db))
                     (artifact-rows "supply-order-proposal" (store/supply-order-proposal-history db)))))

     (section
      "安全性懸念通知の実送信ログ"
      (str "<code>:flag-safety-concern</code> が人間に承認された後にだけ発火する。"
           "site-1 の <code>:safety-contacts</code> 名簿全員へ mail と 電話 の両方に fan-out され、"
           "1 件の失敗が他の宛先を止めない。ここでは mock notifier の実際の送信ログ。")
      (table ["channel" "宛先" "status" "件名 / 音声メッセージ" "本文"] (notice-rows notifier)))

     (section
      (str "法域カバレッジ (" (:covered cov) " / " (:requested cov) ")")
      (str (esc (:note cov))
           " 未登録法域には spec-basis が<strong>無い</strong> — advisor は要件を創作できず、"
           "governor は <code>:no-legal-basis</code> で HARD hold する（上の site-2 / ATL が実例）。")
      (table ["法域" "名称" "所管当局" "threshold model" "掘削深さトリガー" "出典"]
             (jurisdiction-rows)))

     "</main>\n"
     "<footer>\n"
     "  <p class=\"muted\">Generated at build time by <code>civilworks.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) from a real "
     "<code>civilworks.operation</code> actor run against <code>civilworks.store/seed-db</code>. "
     "Deterministic: no timestamps, no random ids — two runs from the same seed are byte-identical.</p>\n"
     "</footer>\n"
     "</body>\n</html>\n")))

(defn -main [& args]
  (let [out    (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        ledger (vec (store/ledger (:db result)))
        holds  (filter #(= :governor-hold (:t %)) ledger)]
    ;; Build-time invariant, not a convention: this console exists to show
    ;; a governed actor refusing proposals that never reach a human. A
    ;; scenario that stopped producing HARD holds would render a page that
    ;; misrepresents the actor's posture, so refuse to write it at all.
    (when (empty? holds)
      (throw (ex-info (str "REFUSING to write " out
                           ": the run produced ZERO :governor-hold ledger facts. "
                           "The operator console must demonstrate at least one HARD hold "
                           "that never reaches a human -- see civilworks.render-html ns "
                           "docstring 'Build-time invariant'. Fix the scenario in "
                           "`run-demo!` (or the governor) rather than relaxing this check.")
                      {:out out
                       :ledger-facts (count ledger)
                       :fact-types (frequencies (map :t ledger))})))
    (let [parent (.getParentFile (java.io.File. ^String out))]
      (when parent (.mkdirs parent)))
    (spit out (render result))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count holds) " governor holds, "
                  (count (filter #(= :committed (:t %)) ledger)) " commits, "
                  (count (:runs result)) " operations)"))))

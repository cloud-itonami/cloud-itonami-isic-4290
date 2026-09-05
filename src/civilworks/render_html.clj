(ns civilworks.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-4290`: this
  repo previously had NO operator console and no generator at all. This
  namespace drives the REAL actor stack (`civilworks.operation` ->
  `civilworks.governor` -> `civilworks.store`, compiled by
  `civilworks.operation/build` and driven with `langgraph.graph/run*`
  EXACTLY as this repo's own demo driver `civilworks.sim` does -- run
  `clojure -M:dev:run` to see the same episode printed), against the
  REAL seeded site ids `site-1`..`site-8` from
  `civilworks.store/demo-data` (verified BEFORE this file was written by
  running `clojure -M:dev:run` and reading its audit ledger -- the sim's
  own ids DO match the seed, so the scenario below is an adaptation of a
  known-good episode rather than a fresh guess).

  Every row, count and status in the generated page is derived from
  real store/ledger/notifier output. The ONLY hand-written content is
  `op-gate-rows`, a static description of this actor's fixed four-op
  gate contract (see the comment there).

  Deterministic: no clock, no randomness, no network, no timestamps in
  the page content -- two consecutive runs are byte-identical (verified
  with `cmp`).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [civilworks.advisor :as advisor]
            [civilworks.facts :as facts]
            [civilworks.governor :as governor]
            [civilworks.notify :as notify]
            [civilworks.operation :as op]
            [civilworks.store :as store]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :site-supervisor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- rogue-advisor
  "A DELIBERATELY malfunctioning advisor stub, injected through
  `civilworks.operation/build`'s own `:advisor` seam.

  Governor checks 2 (`:effect-not-propose`) and 3
  (`:forbidden-action-class`) are defense-in-depth rules: this repo's
  real `civilworks.advisor/mock-advisor` can never emit a proposal that
  trips them, precisely because it is well-behaved. They exist so that a
  compromised or malfunctioning advisor gains nothing by trying, and the
  only honest way to SHOW them firing is to actually hand the governor a
  bad proposal and let it reject it. Nothing is hand-appended to the
  ledger -- the same `civilworks.governor/check` runs, and the same
  `:hold` node writes the fact.

  Routed by a `:rogue` key on the request (the request map is passed
  straight through to `advisor/-advise`)."
  []
  (reify advisor/Advisor
    (-advise [_ _st {:keys [subject rogue]}]
      (case rogue
        :effect-not-propose
        {:summary    (str subject " 向け資材発注（不正な:effect）")
         :rationale  "故障/侵害されたadvisorのスタブ -- :effect が :propose 以外"
         :cites      [subject]
         :effect     :actuate
         :value      {:site-id subject :items ["excavator-rental"] :cost-usd 1200 :vendor "Rogue Rentals"}
         :stake      :order-supplies
         :confidence 0.95}

        :forbidden-action-class
        {:summary    (str subject " 現場記録更新（重機直接操作マーカー付き）")
         :rationale  "故障/侵害されたadvisorのスタブ -- :heavy-equipment-control? マーカーを立てた提案"
         :cites      [subject]
         :effect     :propose
         :value      {:id subject :heavy-equipment-control? true
                      :command "excavator-boom-down"}
         :stake      nil
         :confidence 0.95}))))

(defn run-demo!
  "Runs a fresh seeded store through a scenario that reaches every
  disposition this actor can produce AND fires all EIGHT of the Civil
  Works Governor's HARD checks.

  Clean path (site-1, JPN, outdoor-sports-facility drainage/grading):
  a `:log-site-record` progress update (AUTO-COMMITS at phase 3), a
  `:schedule-construction-operation` proposal (AUTO-COMMITS -- this
  actor deliberately does NOT make schedule ops permanent `high-stakes`
  members, unlike the sibling demolition/road-rail actors), a
  `:flag-safety-concern` (ALWAYS escalates at every phase, human
  approves, and the safety-concern notice is then actually dispatched
  to the site's contact roster through the injected notifier), a
  `:log-site-record` recording the concern's resolution, an
  `:order-supplies` below the cost threshold (AUTO-COMMITS) and one
  above it (escalates on cost alone, human approves). Cross-jurisdiction
  clean paths: site-7 (USA, excavation AT the 1.5 m trigger but
  compliant because shoring is recorded installed) and site-8 (DEU/EU,
  honestly `:qualitative` -- no numeric trigger is ever fabricated).

  HARD holds -- none of these ever reaches a human:
    site-2  `:no-legal-basis`                 (ATL, jurisdiction absent from `civilworks.facts/catalog`)
    site-3  `:site-not-verified` + `:utility-survey-incomplete`
    site-4  `:utility-survey-incomplete`
    site-5  `:excavation-shoring-noncompliant` (3.5 m >= JPN's 2.0 m trigger, no shoring on file)
    site-6  `:unresolved-safety-concern`
    site-1  `:unknown-op`                      (`:direct-equipment-command`, outside the closed 4-op allowlist)
    site-1  `:effect-not-propose`              (rogue advisor, see `rogue-advisor`)
    site-1  `:forbidden-action-class`          (rogue advisor, see `rogue-advisor`)

  Returns {:db .. :notifier .. :requests n} -- every field read by
  `render` below is real governor/store/notifier output."
  []
  (let [db       (store/seed-db)
        notifier (notify/mock-notifier)
        actor    (op/build db {:notifier notifier})
        ;; same graph, same governor, same store -- only the advisor seam differs
        rogue    (op/build db {:notifier notifier :advisor (rogue-advisor)})]

    (exec! actor "t1" {:op :log-site-record :subject "site-1"
                       :patch {:id "site-1" :utility-strike-detected? false}})

    (exec! actor "t2" {:op :schedule-construction-operation :subject "site-1"
                       :project-type :outdoor-sports-facility
                       :window {:proposed-start-date "2026-08-01" :proposed-end-date "2026-08-10"}
                       :notes "屋外走路排水路整地工事"})

    (exec! actor "t3" {:op :flag-safety-concern :subject "site-1"
                       :concern-type :utility-strike
                       :concern-description "排水路整地中に地中埋設ケーブルらしき物を確認、追加調査が必要。"})
    (approve! actor "t3")

    (exec! actor "t4" {:op :log-site-record :subject "site-1"
                       :patch {:id "site-1" :safety-concern-unresolved? false}})

    (exec! actor "t5" {:op :order-supplies :subject "site-1"
                       :items ["drainage-pipe-100mm" "track-surface-aggregate"]
                       :cost-usd 800 :vendor "Local Civil Supply Co."})

    (exec! actor "t6" {:op :order-supplies :subject "site-1"
                       :items ["trench-shoring-box"] :cost-usd 9000 :vendor "Heavy Civil Rentals"})
    (approve! actor "t6")

    (exec! actor "t7" {:op :schedule-construction-operation :subject "site-2"
                       :project-type :industrial-plant :window {}})
    (exec! actor "t8" {:op :schedule-construction-operation :subject "site-3"
                       :project-type :pipeline :window {}})
    (exec! actor "t9" {:op :schedule-construction-operation :subject "site-4"
                       :project-type :power-line :window {}})
    (exec! actor "t10" {:op :schedule-construction-operation :subject "site-5"
                        :project-type :industrial-plant :window {}})
    (exec! actor "t11" {:op :schedule-construction-operation :subject "site-6"
                        :project-type :pipeline :window {}})
    (exec! actor "t12" {:op :direct-equipment-command :subject "site-1"})

    (exec! rogue "t13" {:op :order-supplies :subject "site-1" :rogue :effect-not-propose})
    (exec! rogue "t14" {:op :log-site-record :subject "site-1" :rogue :forbidden-action-class})

    (exec! actor "t15" {:op :schedule-construction-operation :subject "site-7"
                        :project-type :power-line
                        :window {:proposed-start-date "2026-09-01" :proposed-end-date "2026-09-10"}})
    (exec! actor "t16" {:op :schedule-construction-operation :subject "site-8"
                        :project-type :pipeline
                        :window {:proposed-start-date "2026-09-15" :proposed-end-date "2026-09-25"}})

    {:db db :notifier notifier :requests 16}))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn holds
  "Every real `:governor-hold` fact on the ledger."
  [db]
  (filterv #(= :governor-hold (:t %)) (store/ledger db)))

(defn- last-fact-for [ledger site-id]
  (last (filter #(= (:subject %) site-id) ledger)))

(defn- status-cell [ledger site-id]
  (let [f (last-fact-for ledger site-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (str/join ", " (map name (:basis f)))) "</span>")
      (= :approval-rejected (:t f)) "<span class=\"critical\">approver rejected</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- bool-cell [v {:keys [true-class false-class true-label false-label]}]
  (if (true? v)
    (format "<span class=\"%s\">%s</span>" true-class true-label)
    (format "<span class=\"%s\">%s</span>" false-class false-label)))

(defn- shoring-cell
  "Independently recomputed, exactly as `civilworks.governor` check 7
  does it -- `civilworks.facts/excavation-shoring-noncompliant?` over
  the site's OWN recorded depth/shoring ground truth, three-valued."
  [{:keys [jurisdiction excavation-depth-m shoring-installed?] :as site}]
  (let [verdict (facts/excavation-shoring-noncompliant? jurisdiction site)
        trigger (:excavation-depth-trigger-m (facts/spec-basis jurisdiction))
        depth   (if (number? excavation-depth-m) (str excavation-depth-m " m") "n/a")
        trig    (if (number? trigger) (str "trigger " trigger " m") "no numeric trigger")]
    (str (esc depth) " <span class=\"muted\">(" (esc trig) ")</span><br>"
         (cond
           (true? verdict)
           "<span class=\"critical\">shoring NOT installed &middot; noncompliant</span>"
           (= :qualitative verdict)
           "<span class=\"muted\">qualitative jurisdiction &middot; no bright line</span>"
           (nil? verdict)
           "<span class=\"muted\">no spec-basis on file</span>"
           (true? shoring-installed?)
           "<span class=\"ok\">shoring installed</span>"
           :else
           "<span class=\"ok\">below trigger</span>"))))

(defn- site-row [ledger {:keys [id name jurisdiction project-type
                                site-verified? utility-survey-completed?
                                safety-concern-unresolved?] :as site}]
  (format "        <tr><td><code>%s</code><br>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc name) (esc jurisdiction)
          (esc (clojure.core/name (or project-type :unknown)))
          (bool-cell site-verified? {:true-class "ok" :true-label "verified"
                                     :false-class "critical" :false-label "NOT verified"})
          (bool-cell utility-survey-completed? {:true-class "ok" :true-label "complete"
                                                :false-class "critical" :false-label "incomplete"})
          (shoring-cell site)
          (bool-cell safety-concern-unresolved? {:true-class "critical" :true-label "unresolved concern"
                                                 :false-class "ok" :false-label "none open"})
          (status-cell ledger id)))

(defn- basis-label
  "One basis entry, rendered compactly. Rule keywords print as-is;
  provenance URLs print as-is; long statutory text is elided to its
  opening clause (the full text lives in `civilworks.facts`)."
  [b]
  (cond
    (keyword? b) (str "<code>" (esc (name b)) "</code>")
    (and (string? b) (str/starts-with? b "http")) (str "<code>" (esc b) "</code>")
    :else (let [s (str b)]
            (esc (if (> (count s) 56) (str (subs s 0 56) "…") s)))))

(defn- ledger-row [{:keys [t op subject basis confidence]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (case t
            :governor-hold "<span class=\"critical\">governor-hold</span>"
            :committed "<span class=\"ok\">committed</span>"
            :approval-rejected "<span class=\"critical\">approval-rejected</span>"
            (str "<span class=\"muted\">" (esc (name t)) "</span>"))
          (esc (name (or op :n-a)))
          (esc subject)
          (str/join " &middot; " (map basis-label basis))
          (if (number? confidence) (esc confidence) "<span class=\"muted\">&mdash;</span>")))

(defn- hold-rule-rows
  "Which HARD checks actually fired this run, grouped by rule -- derived
  entirely from the `:violations` the governor itself attached."
  [db]
  (let [violations (mapcat :violations (holds db))]
    (->> (group-by :rule violations)
         (sort-by (comp name key))
         (map (fn [[rule vs]]
                (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
                        (esc (name rule))
                        (count vs)
                        (esc (:detail (first vs)))))))))

(defn- artifact-rows [label history]
  (if (empty? history)
    [(format "        <tr><td>%s</td><td>0</td><td><span class=\"muted\">none this run</span></td></tr>"
             (esc label))]
    [(format "        <tr><td>%s</td><td>%s</td><td>%s</td></tr>"
             (esc label)
             (count history)
             (str/join " &middot; "
                       (map #(str "<code>" (esc (get % "record_id")) "</code>") history)))]))

(defn- notice-rows [notifier]
  (map (fn [{:keys [status channel to subject message]}]
         (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
                 (if (= :sent status)
                   "<span class=\"ok\">sent</span>"
                   (str "<span class=\"critical\">" (esc (name (or status :unknown))) "</span>"))
                 (esc (name (or channel :unknown)))
                 (esc to)
                 (esc (or subject message ""))))
       (notify/sent-log notifier)))

(defn- coverage-rows
  "Honest jurisdiction coverage, read straight out of
  `civilworks.facts/catalog` -- never a hand-typed count."
  []
  (map (fn [[iso3 {:keys [name owner-authority threshold-model excavation-depth-trigger-m]}]]
         (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
                 (esc iso3) (esc name) (esc owner-authority)
                 (esc (clojure.core/name (or threshold-model :unknown)))
                 (if (number? excavation-depth-trigger-m)
                   (str (esc excavation-depth-trigger-m) " m")
                   "<span class=\"muted\">no numeric trigger (never fabricated)</span>")))
       (sort-by key facts/catalog)))

(def ^:private op-gate-rows
  ;; The ONLY hand-written content on this page: a static description of
  ;; this actor's own CLOSED four-op gate contract (README `Ops`,
  ;; `civilworks.governor`/`civilworks.phase` ns docstrings). This is
  ;; documentation of fixed, structural behaviour -- not runtime
  ;; telemetry -- so it is legitimately hand-described rather than
  ;; derived from a live run. Everything else on this page is derived.
  ["        <tr><td><code>:log-site-record</code></td><td><span class=\"ok\">phase-3 auto-commit when governor-clean</span> &middot; data logging only, no capital or safety risk</td></tr>"
   "        <tr><td><code>:schedule-construction-operation</code></td><td><span class=\"ok\">phase-3 auto-commit when governor-clean</span> &middot; deliberately NOT a permanent <code>high-stakes</code> member &middot; site-verification, utility-survey, excavation-shoring and unresolved-concern all re-checked independently</td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at ANY phase</span> &middot; notice dispatched to the site contact roster only after approval</td></tr>"
   "        <tr><td><code>:order-supplies</code></td><td><span class=\"ok\">phase-3 auto-commit below the cost threshold</span> &middot; <span class=\"warn\">escalates above 5000 USD or below the confidence floor</span></td></tr>"
   "        <tr><td><code>anything else</code></td><td><span class=\"critical\">HARD hold &middot; <code>:unknown-op</code></span> &middot; the allowlist is closed; heavy-equipment control and structural-completion sign-off are permanently outside this actor's authority</td></tr>"])

(defn render
  "Renders the whole operator-console.html document from the result of
  `run-demo!` (or any other real scenario over this repo's actor)."
  [{:keys [db notifier requests]}]
  (let [ledger    (vec (store/ledger db))
        sites     (store/all-sites db)
        hs        (holds db)
        committed (filterv #(= :committed (:t %)) ledger)
        cov       (facts/coverage)]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4290 &middot; other-civil-engineering-project coordination</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Construction of other civil engineering projects (ISIC 4290) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · coordination-only · every proposal is <code>:effect :propose</code> · no heavy-equipment control, no structural-completion sign-off</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>This run</h2>\n"
     "    <p class=\"muted\">Build-time-generated from <code>civilworks.store</code> via <code>civilworks.render-html</code> (<code>clojure -M:dev:render-html</code>), driving the real <code>civilworks.operation</code> actor graph. No hand-written numbers.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Requests driven</th><th>Ledger facts</th><th>Committed</th><th>HARD holds</th><th>Distinct HARD rules fired</th></tr></thead>\n"
     "      <tbody>\n"
     (format "        <tr><td>%s</td><td>%s</td><td><span class=\"ok\">%s</span></td><td><span class=\"critical\">%s</span></td><td>%s of %s</td></tr>"
             requests (count ledger) (count committed) (count hs)
             (count (distinct (map :rule (mapcat :violations hs))))
             8)
     "\n      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Sites</h2>\n"
     "    <p class=\"muted\">Ground truth is the site's OWN recorded fields, never the current proposal's confidence. The excavation column is recomputed here the same way <code>civilworks.governor</code> check 7 recomputes it — via <code>civilworks.facts/excavation-shoring-noncompliant?</code>.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Site</th><th>Jurisdiction</th><th>Project type</th><th>Site verified</th><th>Utility survey</th><th>Excavation depth / shoring</th><th>Safety concern</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial site-row ledger) sites)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Op gate (Civil Works Governor + phase 3)</h2>\n"
     "    <p class=\"muted\">Eight HARD checks, none of them overridable by a human approver. The confidence / high-stakes / cost gate is SOFT — it asks a human to look, and the human may approve.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" op-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD checks that actually fired this run</h2>\n"
     "    <p class=\"muted\">Grouped from the <code>:violations</code> the governor itself attached to each <code>:governor-hold</code> fact. None of these ever reached a human.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Times fired</th><th>Detail (first occurrence)</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (hold-rule-rows db)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every commit and every hold this scenario produced, in order.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Site</th><th>Basis</th><th>Confidence</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Coordination artifacts committed</h2>\n"
     "    <p class=\"muted\">Every committed record is a coordination artifact — a log entry, a schedule PROPOSAL, a safety-concern flag, a supply-order PROPOSAL. Never a dispatched crew, never a sign-off.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Artifact</th><th>Count</th><th>Record ids</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (concat (artifact-rows "site-record-log" (store/site-record-log-history db))
                            (artifact-rows "schedule-proposal" (store/schedule-proposal-history db))
                            (artifact-rows "safety-concern-flag" (store/safety-concern-flag-history db))
                            (artifact-rows "supply-order-proposal" (store/supply-order-proposal-history db)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Safety-concern notices dispatched</h2>\n"
     "    <p class=\"muted\">Sent through the injected <code>civilworks.notify</code> transport (the deterministic mock here) to the site's supervisor / safety-officer roster — mail and phone, both channels, and only ever after a human approved the flag.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Status</th><th>Channel</th><th>To</th><th>Subject / message</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (notice-rows notifier)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Jurisdiction coverage</h2>\n"
     "    <p class=\"muted\">"
     (esc (:note cov))
     " Covered: " (esc (:covered cov)) " of " (esc (:requested cov))
     " requested.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>ISO-3</th><th>Jurisdiction</th><th>Authority</th><th>Threshold model</th><th>Excavation-shoring trigger</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (coverage-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        hs (holds db)]
    ;; Build-time invariant: a console that shows no real HARD hold is
    ;; not evidence of a governor. Do not weaken this.
    (when (empty? hs)
      (throw (ex-info "no :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))
                       :closed-op-allowlist governor/closed-op-allowlist})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds, "
                  (count (distinct (map :rule (mapcat :violations hs)))) " distinct HARD rules)"))))

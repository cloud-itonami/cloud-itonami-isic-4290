(ns civilworks.facts
  "Per-jurisdiction other-civil-engineering-project regulatory catalog --
  the spec-basis table the Civil Works Governor checks every
  `:schedule-construction-operation` proposal against ('did the advisor
  cite an OFFICIAL public source for this jurisdiction's pre-work buried-
  utility survey / excavation-shoring requirements, or did it invent
  one?'). Same honest-coverage discipline `installation.facts`
  (`cloud-itonami-isic-4329`) / `finishing.facts` (`cloud-itonami-isic-
  4330`) established for this fleet: a jurisdiction not in this table has
  NO spec-basis, full stop -- the advisor must not fabricate one, and the
  governor holds if it tries.

  Coverage is reported HONESTLY (see `coverage`); this is a STARTING
  catalog (JPN/USA/DEU), not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to `catalog`,
  cite a real source, done -- never invent a jurisdiction's requirements
  to make coverage look bigger.

  This actor's domain is the RESIDUAL civil-engineering category --
  industrial-plant civil works, pipeline construction, power-line/
  electric-transmission-line construction, and outdoor sports facilities
  -- work that is heavy on trenching/excavation adjacent to buried
  utilities (gas, water, electrical, telecom conduit), distinct from the
  fall-hazard/materials-hazard profile `installation.facts` established
  for building-installation trade work. Two independent legal bases, both
  citable and both real:

    1. `:utility-survey-basis` -- the PRE-WORK duty to investigate/locate
       buried utilities and ground conditions before excavation begins
       (industrial-plant foundation/piping trenches, pipeline trenching,
       power-line/transmission-tower foundation excavation and outdoor-
       sports-facility drainage/grading work all routinely disturb
       existing buried gas/water/electrical/telecom installations -- this
       is the SAME kind of pre-work-investigation duty
       `installation.facts`/`finishing.facts` cite for their own domains'
       hazmat surveys, applied to this domain's own real hazard: buried-
       utility strikes and ground-collapse from unknown subsurface
       conditions).
    2. `:excavation-shoring-basis` -- the trench/excavation-collapse
       protection duty (sloping, benching, shoring or shielding) that
       applies once a trench or excavation reaches a jurisdiction-
       specific trigger depth. This is this domain's OWN real,
       independently-recheckable numeric trigger, the SAME kind
       `installation.facts` establishes for its own at-height fall-
       protection duty -- the underlying occupational-safety-and-health
       law is a SEPARATE, well-established body of regulation this actor
       does not invent.

  `:threshold-model` mirrors the SAME honest quantitative/qualitative
  split `installation.facts`/`demolition.facts`/`finishing.facts`
  established:
    :quantitative -- the jurisdiction's OSH law states a fixed numeric
                     depth that triggers a shoring/protective-system
                     duty (Japan's 2m under the Industrial Safety and
                     Health Regulations' slope-gradient table for
                     ordinary ground; the USA's 5ft/1.5m under OSHA
                     1926.652). `excavation-shoring-noncompliant?` can
                     independently recompute a HARD hold from this.
    :qualitative  -- the jurisdiction imposes a documented soil-
                     classification / risk-assessment duty with NO
                     single fixed EU-wide/federal numeric trigger depth
                     (Germany/EU -- the permitted unshored depth varies
                     by soil class under DIN 4124 / DGUV technical
                     rules, not one fixed number). This actor does NOT
                     invent a depth to make this jurisdiction look
                     automatable -- `excavation-shoring-noncompliant?`
                     returns `:qualitative` and `:schedule-construction-
                     operation` for that jurisdiction is left to the
                     ordinary confidence-floor gate (see `civilworks.
                     governor` ns docstring) rather than a fabricated
                     HARD numeric rule.

  DEU is used as the EU-jurisdiction proxy, the SAME convention
  `installation.facts`/`demolition.facts`/`finishing.facts`/
  `construction.facts`/`aerospace.facts` established -- there is no
  ISO-3166 alpha-3 code for the EU itself, and excavation-safety
  technical rules are issued at the German national/accident-insurance
  level (DGUV, Deutsche Gesetzliche Unfallversicherung), so the citation
  lists the German source directly rather than inventing an EU country
  code. All citations below were independently verified against their
  official source before being written (laws.e-gov.go.jp / osha.gov /
  dguv.de / bgbau-medien.de). The JPN excavation-shoring citation
  deliberately uses the 'その他の地山' (ordinary/unclassified ground)
  row of the slope-gradient table -- the general-case default when a
  more specific soil classification is not otherwise on file for a
  site, the SAME kind of honest single-row simplification
  `installation.facts` applies to its own multi-factor law text -- not
  an invented number; see `catalog` for the full table's other rows.
  The USA utility-survey basis cites OSHA's own general excavation
  standard's underground-installation-location duty (29 CFR
  1926.651(b)) rather than a state-run 811/one-call statute, since
  there is no single federal one-call law -- the federal OSH duty is the
  one uniformly citable requirement across all US jurisdictions."
  )

(def catalog
  "iso3 -> requirement map. `:utility-survey-basis` / `:excavation-
  shoring-basis` / their `-provenance` pairs, plus `:owner-authority`,
  are the G2-style citation the governor requires before a `:schedule-
  construction-operation` proposal can ever commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省（労働基準監督署）"
          :utility-survey-basis "労働安全衛生規則（昭和47年労働省令第32号）第355条（明り掘削の作業を行う場合において、地山の崩壊又は埋設物等の損壊等により労働者に危険を及ぼすおそれのあるときは、あらかじめ、作業箇所及びその周辺の地山についてボーリングその他適当な方法により、形状・地質・地層の状態、き裂・含水・湧水及び凍結の有無並びに状態、埋設物等の有無及び状態を調査する義務 -- 産業プラント基礎/パイプライン/送電線基礎の掘削に伴う埋設ガス・水道・電気・通信管路の損壊防止を含む）"
          :utility-survey-provenance "https://laws.e-gov.go.jp/law/347M50002000032"
          :excavation-shoring-basis "労働安全衛生規則第356条（掘削面のこう配の基準）-- 手掘りにより垂直掘削を行う場合、その他の地山では掘削面の高さが2メートル未満のときのみこう配90度（垂直）が許容され、2メートル以上5メートル未満では75度以下、5メートル以上では60度以下とすることを義務付け、この基準を満たさずに垂直を維持する場合は土止め支保工等の防護措置が必要（岩盤・堅い粘土からなる地山や砂からなる地山は別基準）"
          :excavation-shoring-provenance "https://laws.e-gov.go.jp/law/347M50002000032"
          :threshold-model :quantitative
          :excavation-depth-trigger-m 2.0
          :threshold-note "その他の地山（一般的な区分が未確定な現場のデフォルト区分）について、掘削面の高さが2メートル以上になる垂直掘削は、こう配基準を満たすか土止め支保工等の防護措置を講じない限り労働安全衛生規則第356条違反（岩盤・砂等の特別区分は別途 `catalog` のnoteに記載の基準による）"}
   "USA" {:name "United States"
          :owner-authority "Occupational Safety and Health Administration (OSHA), U.S. Department of Labor"
          :utility-survey-basis "29 CFR 1926.651(b) (OSHA Excavations, General requirements -- before opening an excavation, the employer must determine the estimated location of utility installations (e.g. sewer, telephone, fuel, electric, water lines, or any other underground installations reasonably expected to be encountered) and contact the utility companies/owners to establish the actual location; while the excavation is open, underground installations must be protected, supported, or removed as necessary to safeguard employees)"
          :utility-survey-provenance "https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.651"
          :excavation-shoring-basis "29 CFR 1926.652 (OSHA -- Requirements for protective systems: excavations 5 feet (1.5 m) or deeper must be protected by a sloping/benching, shoring, or shielding protective system unless the excavation is made entirely in stable rock or a competent person determines there is no potential for a cave-in)"
          :excavation-shoring-provenance "https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.652"
          :threshold-model :quantitative
          :excavation-depth-trigger-m 1.5
          :threshold-note "5 feet (1.5 m) or deeper triggers the OSHA 1926.652 protective-system duty (sloping, benching, shoring, or shielding) for civil-engineering excavation/trenching work."}
   "DEU" {:name "Germany (EU jurisdiction proxy, see ns docstring)"
          :owner-authority "Deutsche Gesetzliche Unfallversicherung (DGUV) / Berufsgenossenschaft der Bauwirtschaft (BG BAU); EU level: European Agency for Safety and Health at Work (EU-OSHA)"
          :utility-survey-basis "§16 der Unfallverhütungsvorschrift 'Bauarbeiten' (DGUV Vorschrift 38 / DGUV Vorschrift 39) -- der Unternehmer hat sich vor Beginn von Erdarbeiten bei den zuständigen Stellen (Leitungsbetreibern) zu erkundigen, ob im Arbeitsbereich Kabel oder Leitungen vorhanden sind, einschließlich deren Verlauf und Tiefe; Durchführungshinweise in DGUV Information 203-017 (vormals BGI 759)"
          :utility-survey-provenance "https://www.bgbau-medien.de/handlungshilfen_gb/daten/dguv/203_017/5.htm"
          :framework-provenance "https://www.bgbau-medien.de/handlungshilfen_gb/daten/dguv/203_017/inhalt.htm"
          :excavation-shoring-basis "DGUV Regel 101-604 (Branche Tiefbau) / DIN 4124 (Baugruben und Gräben) -- Böschungswinkel bzw. Verbaupflicht sind nach Bodenklasse gestaffelt (keine einheitliche bundesweite/EU-weite Zahlenvorgabe wie in JPN/USA); die zulässige unverbaute Tiefe hängt von der Bodenklassifikation und einer dokumentierten Gefährdungsbeurteilung ab"
          :excavation-shoring-provenance "https://www.dguv.de/fb-bauwesen/sachgebiete/tiefbau/baugruben/regelwerk/index.jsp"
          :threshold-model :qualitative
          :excavation-depth-trigger-m nil
          :threshold-note "EU/ドイツの掘削・土留め規則（DIN 4124、DGUV技術規則群）は土質区分と文書化されたリスクアセスメント（Gefährdungsbeurteilung）に基づく措置義務を課すのみで、日本の2m・米国の5ft(1.5m)のような単一の固定数値トリガーはEU全域では法定されていない -- ここで数値を創作しない。"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any `:schedule-construction-operation`
  proposal that tries to cite one."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4290 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `civilworks.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn excavation-shoring-noncompliant?
  "Independently recompute whether `site`'s own recorded ground-truth
  fields -- `:excavation-depth-m` (how deep the current trench/excavation
  actually is -- industrial-plant foundation, pipeline trench, power-line
  foundation, outdoor-sports-facility drainage cut, etc.) and `:shoring-
  installed?` (whether a sloping/benching/shoring/shielding protective
  measure is actually in place) -- leave the site out of compliance with
  `iso3`'s excavation-shoring trigger.

  Three-valued, deliberately (the same shape `installation.facts/fall-
  protection-noncompliant?`/`demolition.facts/notification-lead-
  insufficient?` established):
    true         -- a :quantitative jurisdiction (Japan, USA) whose own
                    numeric trigger depth is independently confirmed MET
                    OR EXCEEDED by the site's own recorded actual
                    excavation depth, AND no shoring/protective measure
                    is recorded as installed -- a bright-line legal
                    violation. The Civil Works Governor turns this into a
                    HARD, un-overridable hold on `:schedule-construction-
                    operation`.
    false        -- either below the trigger depth, or at/above it with
                    a shoring/protective measure already recorded
                    installed.
    :qualitative -- a jurisdiction with NO fixed numeric trigger (DEU/EU).
                    This actor cannot independently confirm
                    'compliant'/'noncompliant' by arithmetic alone -- the
                    law itself requires a documented soil-classification/
                    risk-assessment judgment call. Never fabricate a
                    trigger depth here.
    nil          -- no spec-basis at all for `iso3` (a jurisdiction not in
                    `catalog`)."
  [iso3 {:keys [excavation-depth-m shoring-installed?]}]
  (when-let [{:keys [threshold-model excavation-depth-trigger-m]} (spec-basis iso3)]
    (case threshold-model
      :quantitative
      (boolean (and (number? excavation-depth-m)
                    (>= excavation-depth-m excavation-depth-trigger-m)
                    (not (true? shoring-installed?))))
      :qualitative
      :qualitative
      nil)))

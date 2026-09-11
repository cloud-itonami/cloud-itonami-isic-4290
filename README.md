# cloud-itonami-4290

Open Business Blueprint for **ISIC Rev.5 4290**: construction of other
civil engineering projects (industrial-plant civil works, pipeline
construction, power-line/electric-transmission-line construction, and
outdoor sports facilities -- the RESIDUAL civil-engineering category,
distinct from 4211 building construction, 4212 civil engineering for
roads and railways, and 4220 construction of utility projects).

This repository designs a forkable OSS business for other-civil-
engineering-project operations coordination: run by a qualified operator
so a community keeps its own operating records instead of renting a
closed SaaS.

## Scope -- this is a COORDINATION-ONLY actor, not equipment control

This is a safety-relevant domain: buried-utility-strike hazards (gas,
water, electrical, telecom conduit disturbed during excavation),
excavation-collapse hazards (trenching for pipelines, industrial-plant
foundations, power-line/transmission-tower foundations), structural-
completion sign-off. **This actor does NOT hold heavy-equipment-control
authority, and it does NOT hold structural-completion-sign-off
authority.** Both are the site supervisor / building official's
exclusive authority, always. The Civil Works Advisor (LLM) never issues
a heavy-equipment-control command and never finalizes a structural-
completion sign-off; the independent **Civil Works Governor** HARD-
blocks any proposal that even tries (un-overridable by any human
approval -- see `civilworks.governor` ns docstring). This actor
coordinates *potential* crew/equipment dispatch (a proposed schedule
window, a flagged concern, a supply-order proposal) -- it never directly
actuates.

Structurally, EVERY proposal this actor's advisor can produce carries
`:effect :propose`, and the Civil Works Governor HARD-holds any proposal
that doesn't -- this is a permanent invariant distinguishing this actor
from `cloud-itonami-isic-4211` (the robotics-premise reference this
actor follows structurally), whose sibling actuation ops DO commit
real-world effects. `cloud-itonami-isic-4211`'s README robotics-premise
framing therefore does NOT apply verbatim here: this actor is
deliberately narrower, following the same coordination-only pattern
`cloud-itonami-isic-4329` (other construction installation) and
`cloud-itonami-isic-4390` (other specialized construction activities)
established.

## Core Contract

```text
site/permit record + independent verification
        |
        v
Advisor -> Civil Works Governor -> proceed (log/schedule/flag/order proposal), hold, or human approval
        |
        v
coordination artifacts (schedule proposal, safety-concern flag,
supply-order proposal) + audit ledger -- NEVER heavy-equipment dispatch,
NEVER a structural-completion sign-off
```

No automated advice can propose a schedule the governor refuses, suppress
a safety-concern flag, or slip a heavy-equipment-control/structural-
completion-sign-off marker past the governor -- and a flagged safety
concern always needs a human sign-off (see `Actuation` below).

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `4290`). Required capabilities:

- `:identity`
- `:forms`
- `:audit-ledger`
- `:notifications`

## Implemented slice (`src/civilworks`)

`blueprint.edn` names the governor `:civilworks-governor` and is now
`:implemented`. This repo implements it end-to-end -- **Civil Works
Advisor ⊣ Civil Works Governor** -- following the SAME `.cljc` actor
pattern (langgraph-clj StateGraph, mock-by-default advisor, dual
MemStore/Datomic backend, 0→3 phase rollout) every prior
`cloud-itonami-isic-*` actor in this fleet uses, structured after
[`cloud-itonami-isic-4329`](https://github.com/cloud-itonami/cloud-itonami-isic-4329)
and [`cloud-itonami-isic-4390`](https://github.com/cloud-itonami/cloud-itonami-isic-4390)
(the coordination-only references), narrowed to coordination-only
authority as described above and adapted to the specific hazard profile
of other-civil-engineering work -- buried-utility strikes and
excavation collapse -- rather than the fall-hazard/materials-hazard
profile those siblings established for building-trade work (see
`Actuation` below for the one structural similarity shared with those
references).

The actor itself is fully portable `.cljc` with **no JVM interop in any
actor namespace** -- `civilworks.notify`'s real-transport seam
(`fn-notifier`) takes caller-injected plain functions instead of
embedding a `java.net.http` client, so the actor runs unmodified on JVM
Clojure, ClojureScript, `nbb`, and `kotoba wasm`/`clojurewasm`.

The one deliberate exception is `civilworks.render-html`
(`src/civilworks/render_html.cljk`), which is a **build-time tool, not
part of the actor**: it is JVM-only `.clj` because it writes a file and
reads the vendored `jp-go-dds` stylesheet off the classpath. Nothing in
the actor graph requires, calls or depends on it, so the portability
guarantee above is unaffected -- deleting it would not change a single
proposal, verdict or committed record.

### Closed op-allowlist (4 ops, all `:effect :propose`)

| Op | Ask | Implementation |
|---|---|---|
| `:log-site-record` | progress / material-usage / utility-survey data logging | Normalizes and commits a patch onto the site's ground-truth fields (`:site-verified?`, `:utility-survey-completed?`, `:excavation-depth-m`, `:shoring-installed?`, concern resolution, etc.) and appends an immutable site-record-log entry. No direct capital/safety risk -- MAY auto-commit at phase 3. |
| `:schedule-construction-operation` | industrial-plant / pipeline / power-line / outdoor-sports-facility construction scheduling proposal | Drafts a proposed work WINDOW (never a heavy-equipment-dispatch command or a structural-completion sign-off). Escalates when the governor is not clean or confidence is low; MAY auto-commit at phase 3 when clean and confident -- see `Actuation`. |
| `:flag-safety-concern` | surface a buried-utility-strike (gas/water/electrical/telecom conduit) / excavation-collapse / structural concern | Drafts a safety-concern flag; ALWAYS escalates to a human, unconditionally. Once approved, `civilworks.notify` sends the notice (mail + phone) to the site's supervisor/safety-officer contact roster. |
| `:order-supplies` | materials/equipment procurement proposal | Drafts a supply-order proposal. Escalates above a cost threshold or below the confidence floor; may auto-commit at phase 3 otherwise. |

**Legal basis is data, not code** -- `src/civilworks/facts.cljk`'s
`catalog` is the per-jurisdiction EDN source-of-truth the governor checks
every `:schedule-construction-operation` proposal against (JPN/USA/DEU
seeded; DEU stands in for the EU, the same convention
`installation.facts`/`demolition.facts`/`finishing.facts`/`construction.
facts`/`aerospace.facts` use for EASA). Every citation below was
independently verified against its official source before being
written:

| Jurisdiction | Pre-work buried-utility-survey legal basis | Excavation-shoring legal basis |
|---|---|---|
| 🇯🇵 Japan | 労働安全衛生規則（昭和47年労働省令第32号）第355条（明り掘削の作業を行う場合の事前調査義務 -- 地質・地層・埋設物等の調査）-- [e-Gov](https://laws.e-gov.go.jp/law/347M50002000032) | 労働安全衛生規則第356条（掘削面のこう配の基準 -- その他の地山では高さ2m以上の垂直掘削にこう配基準または土止め支保工が必要）-- [e-Gov](https://laws.e-gov.go.jp/law/347M50002000032) |
| 🇺🇸 USA | 29 CFR 1926.651(b) (OSHA -- before opening an excavation, determine the location of underground utility installations and protect/support them) -- [osha.gov](https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.651) | 29 CFR 1926.652 (OSHA, 5ft/1.5m protective-system trigger) -- [osha.gov](https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.652) |
| 🇪🇺 EU (DEU proxy) | §16 DGUV Vorschrift 38/39 "Bauarbeiten" (pre-work cable/utility inquiry) -- [DGUV Information 203-017](https://www.bgbau-medien.de/handlungshilfen_gb/daten/dguv/203_017/5.htm) | DGUV Regel 101-604 / DIN 4124 (qualitative -- soil-classification/risk-assessment-based, no fixed EU-wide numeric trigger) -- [DGUV](https://www.dguv.de/fb-bauwesen/sachgebiete/tiefbau/baugruben/regelwerk/index.jsp) |

Japan (2m, ordinary-ground row of the slope-gradient table) and the USA
(5ft/1.5m) have real numeric excavation-shoring trigger depths; the EU
deliberately does NOT -- `civilworks.facts/excavation-shoring-
noncompliant?` reports `:qualitative` there rather than fabricating a
number. See `civilworks.facts` ns docstring for the full honesty
discipline.

**Governor -- eight HARD checks, ALL un-overridable by human approval:**
unknown op (outside the closed 4-op allowlist), `:effect` not `:propose`,
forbidden action class (heavy-equipment-control / direct-actuation /
structural-completion-sign-off-finalization markers), site/permit not
independently verified/registered, legal-basis missing, pre-work
buried-utility survey incomplete, excavation-shoring noncompliant
(quantitative jurisdictions only), unresolved safety concern on file.
See `civilworks.governor` ns docstring for the full enumeration,
rationale and real-law citations behind each.

## Actuation

This actor performs **no real-world actuation** -- every committed
record carries `:effect :propose` (see `civilworks.governor` ns
docstring). `:flag-safety-concern` NEVER auto-commits at any phase -- it
always needs a human sign-off, even when the governor is completely
clean (`civilworks.phase` ns docstring 'Actuation' section, `civilworks.
governor`'s `high-stakes` set).

**Like `cloud-itonami-isic-4329`/`cloud-itonami-isic-4390` and UNLIKE
`cloud-itonami-isic-4311`/`cloud-itonami-isic-4210`:** `:schedule-
construction-operation` here is NOT a permanent `high-stakes` member.
`:log-site-record`, `:schedule-construction-operation` and `:order-
supplies` BELOW the cost threshold (`civilworks.governor/supply-order-
cost-threshold-usd`) MAY all auto-commit at phase 3 when the governor is
clean and confidence is high, once every HARD check clears (site
verified, buried-utility survey complete, no excavation-shoring
violation, no unresolved concern, legal basis cited). The eight HARD
governor checks still apply UNCONDITIONALLY regardless of phase; only
the routing to a human vs. auto-commit changes.

```bash
kbb -M:dev:run          # demo: full coordination episode + every HARD hold
kbb -M:dev:test         # test suite
kbb -M:lint             # clj-kondo, errors fail
kbb -M:dev:render-html  # regenerate docs/samples/operator-console.html
```

### Operator console (`docs/samples/operator-console.html`)

A read-only sample console **generated at build time from a real actor
run** -- `civilworks.render-html` seeds the store, drives 17 operations
through the real graph (`intake → advise → govern → decide → commit |
hold | request-approval`), and renders that run's actual output. Every
id, number, rule name, violation detail, record id and citation on the
page is read back out of the resulting store/ledger; none of it is
hand-written HTML. The run reaches all five dispositions the actor can
produce -- auto-commit, human-approved, human-rejected, HARD governor
hold, and rollout-phase hold -- including 8 holds that never reach a
human at all.

It is deterministic: the stack is pure with an in-memory checkpointer
and nothing reads a clock, so two runs from the same seed are
byte-identical (`cmp` them to check). It is also self-policing --
`-main` **refuses to write the file** if the run produced zero
`:governor-hold` ledger facts, so a scenario that quietly stopped
demonstrating HARD holds fails the build instead of publishing a page
that misrepresents the actor's posture.

## License

AGPL-3.0-or-later.

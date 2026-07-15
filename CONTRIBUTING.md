# Contributing

`cloud-itonami-4290` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

The capability layer lives in `kotoba-lang/*` libraries. This repo holds
the business blueprint, the Civil Works Advisor/Governor actor and
operator contracts.

```bash
clojure -M:dev:test   # run the full test suite
clojure -M:lint       # clj-kondo, errors fail
```

## Rules

- Do not commit real site, utility-survey, permit, personal or
  credential data.
- Keep every proposal behind the Civil Works Governor, and keep every
  proposal's `:effect` `:propose` -- never introduce a real-world
  actuation effect into this actor.
- Never extend this actor's op-allowlist, advisor or governor to permit
  heavy-equipment control or structural-completion-sign-off authority.
  That is a hard scope boundary, not a rollout milestone -- proposals to
  relax it should be rejected, not merged.
- Treat workflows as high-risk: add tests for governor hard-checks
  (unknown op, non-`:propose` effect, forbidden action class, site/
  permit verification, pre-work buried-utility-survey completeness,
  excavation-shoring compliance, unresolved safety concerns), phase
  gating and audit-ledger integrity.
- Extending `civilworks.facts` coverage to a new jurisdiction requires a
  real, citable official source -- never fabricate a jurisdiction's
  requirements.
- Keep `src/` fully portable `.cljc` with no JVM-only interop -- real
  external transports (mail/phone/etc.) must stay behind a caller-
  injected function seam (see `civilworks.notify`), not embedded
  platform clients.
- Document any new business-model or operator assumption in `docs/` or
  this README.

## Pull Requests

PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.

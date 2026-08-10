# Ambiguities and implementation questions

These are not claimed errors in the rulebook. They are places where an implementation needs component data, a product decision, or a more explicit formalization.

## Component-data dependencies

1. **Card powers and exact costs.** The catalog contains stable printed IDs, reviewed text, and handler keys, but most handlers have no executable Scala behavior. Many actions can be modified or replaced by these powers (CR pp. 28-30, 42).
2. **Foundation faces.** The catalog contains the six normal/altered records, but their setup effects still require typed handlers verified against CR pp. 10-11.
3. **Player-board tables/icons.** Supply refresh thresholds and some costs are
   graphically encoded on player boards. Trade yield is resolved by the current
   combined rulebook prose: favor is 1 + matches; secrets equal matches (CR
   pp. 18, 24).
4. **Banner ribbons and non-default faces.** CR p. 32 explains ribbon semantics, but machine execution needs exact component-side icon sequences and initial values.
5. **Component behavior coverage.** Printed identities and the runtime catalog are complete, but legacy powers, battle plans, banner ribbons, and many component-specific effects still require typed executable handlers.

## Formalization decisions

1. **Action boundary.** Several automatic checks happen "after any action" (bandit refill, Oathkeeper transfer), while powers can introduce actions and nested effects. Define a transaction boundary and when state-based checks reach a fixed point (CR pp. 15-16, 42).
2. **Simultaneous effects.** Turn player chooses order during a turn; current Chancellor chooses during Chronicle. Define who chooses outside both contexts and whether ordering choices are logged (CR p. 42).
3. **Consent timeout/forgetting.** "Enemies cannot" effects fail if not enforced at the moment. A digital implementation needs an explicit prompt/auto-consent policy so silence is deterministic (CR p. 43).
4. **Private information.** The rules permit owners to view/share their private data. Multiplayer UI needs visibility and reveal/audit policies for facedown advisers, relics, drawn cards, and secret selections (CR p. 42).
5. **Unlimited dice vs limited pieces.** Dice are unlimited; other components are limited and extra placement is ignored. Decide whether digital inventory should preserve physical limits exactly (CR p. 42).
6. **Random winner/coin flips.** No-Empire exhaustion can choose randomly, and unclear Tribunal promise breach uses a coin flip. Specify RNG seed and event log behavior (CR pp. 19, 30).
7. **Ties by role/context.** Oathkeeper, legacy, Vision exhaustion, era, and other ties use different rules. Do not generalize a single tie resolver (CR pp. 16, 19, 36-37).
8. **First game exception.** CR recommends ignoring "become Citizen" powers in the first game, wording this as a recommendation rather than a strict prohibition. Product rules need a configurable or explicit interpretation (CR p. 30; CR p. 34 uses similar guidance).
9. **Campaign target legality under Pass and zero-capacity sites.** Target generation must combine campaign type, ruler, region Pass consent, and special no-capacity cases (CR pp. 21-22, 30-31).
10. **Chronicle actor availability.** Banner task assignment can target "anyone"; player attendance may change between games. Define whether absent/stored lineages can be assigned tasks or whether "anyone" means current participants (CR p. 36).

## Source relationship cautions

- NF is intentionally a change overview. When its simplification omits a detail, use CR, not inference.
- The PDFs are dated May/June 2026. No errata, FAQ, card database snapshot, or base-edition rulebook was provided, so this layer cannot validate later corrections or reconstruct every old-vs-new sentence.
- Printed glyphs/icons do not always survive text extraction semantically. Numeric prose was cross-checked visually, but icon-only component details should be ingested from structured component data before implementation.

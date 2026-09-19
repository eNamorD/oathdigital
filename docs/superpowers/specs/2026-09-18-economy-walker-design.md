# Muster and Trade on the Procedure Walker

> Status: design, amended after review. Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and the [declarative decisions design](2026-09-10-declarative-walker-decisions-design.md), and follows the recipe of the Forge, Travel and Search ports. No implementation is authorized by this document alone.

## Goal and scope

Move Muster and Trade (the legacy `Economy` object) onto the walker as two registry entries, `ActionRef.Muster` and `ActionRef.Trade`, and delete the legacy Economy path in the same slice, only after parity and replay proof. Choosing the card to draw on becomes a walker `Decide`, and the walker gains a generic preview of a parked `ChooseOne` so the client still sees each option's cost and gain.

Out of scope: the 24 Muster/Trade powers in `MusterPowers` stay diagnostic-only (`ReviewedHandler`) and keep working through the registry's `fallbackKind`. Golem Legions ("You may muster by placing favor on Golem Legions. It counts itself as a matching adviser.") is the first power that will extend Muster. This slice does not implement it, and it names the two places the Muster procedure itself must open up when Golem lands (see Extension by powers). The walker, the contribution kinds and the registry are not expected to change for it. One deliberate rule change: the legacy first-game gates (exile-only roles and the fixed unaltered Foundation profile) do not exist in the walker version, as they do not in Travel, so Muster and Trade work for every role and Foundation profile. Everything else is preserved: costs, gains, other gates, candidate order, labels and control strings. The same gates in Minor Actions, Negotiation and Campaign stay until those migrate. Any other rule defect found on the way is a separate change.

## Procedure composition

Each action has its own procedure object beside `TravelProcedure` and `ForgeProcedure`. Muster starts with no selection. Trade starts with its resource as a `DecisionOptionRef.Button` start argument (`favor` or `secret`, the resource gained), as Take Wealth's resource is. This avoids a nested resource decision. The control strings `trade-favor` and `trade-secret` survive as two start controls that send the matching resource (see Start flow); only their board-target selection is removed. A wrong arity or kind is a typed rejection, with its own test.

```
Sequence(                                            // window = <Action>ActionEligibility
  Decide("muster.source", actor,                     // window = <Action>SourceSelection (new)
    ChooseOne(token-free cards at the pawn site, in site order)),
  Branch { (state, pending) =>                       // as CardPlayProcedure does after its Decide
    source = MusterSource.resolve(answered ref, state)
    Vector(
      Sequence(window = <Action>Cost,
        PayCost(actor, OnCard(source.card), cost),
        SpendSupply(actor, 1)),
      Sequence(window = <Action>Gain,              // new window
        Gain.Warbands(actor, kind, 1 + matching))) })
```

`MusterSource` is a value resolved from the answered reference each command and never persisted: the card the cost is placed on, its suit, and its `RuleSourceRef`. Provenance is enforced by the existing answer validation, because only a reference in the folded query can be answered; in this slice `resolve` accepts a token-free card at the actor's pawn site, expressed as one acceptance function so a later slice widens it in one place. The token-free rule applies to every source, whatever added it: `resolve` rejects a source carrying tokens, and the preview therefore drops a power-added option that carries tokens. This slice has no exception mechanism. A rule that lifts the check for a kind of source (Battle plans are the main example in the game) adds one with its first user. `matching` is the actor's faceup advisers of `source.suit`, including the source if it is one.

Costs match the legacy operations: Muster pays 1 Favor on the card; Trade for Favor pays 1 Secret; Trade for Secrets pays 2 Favor, one of them burnt. Gains are requested at their unclamped amount and the best-effort `Gain` takes as much as the supply or bank has: Muster `Gain.Warbands` of `1 + matching` in the actor's own force kind (`ForceKind.Imperial` for a Citizen or Chancellor, `ForceKind.Exile(lineage)` otherwise), Trade for Favor `Gain.Favor` of `1 + matching`, Trade for Secrets `Gain.Secrets` of `matching`. There is no manual `min` against available warbands or bank favor. The role-to-force-kind mapping already exists twice as a private copy (`OperationStateAdapter.playerForceKind` and inside `OperationExecutor`); the plan exposes one shared helper for the tree and does not add a third copy. The gain is a concrete operation inside a windowed node, not an opaque `BuildOps`, so a power can see and rewrite the requested amount. Legacy computes the gain from pre-cost state and no cost feeds a gain input, so the gain is built from state at the `Branch`. A zero gain runs nothing and records nothing. The parity scenarios with a warband or bank shortage (Verification) prove the shrink reproduces the legacy `min`.

Start gates stay in `build`, because they are facts about state and not costs: the act-phase gate, a pawn site, and `PowerRuntime.requireAudited`. The exile-only and Foundation-profile checks are dropped. The legacy check that the actor's force kind has a bounded warband supply is dropped as redundant: `OperationExecutor.validateWarbands` already fails with `UnknownWarbandSupply` for any player's force kind without a `warbandSupply` entry, and the bank it computes (supply minus every board and site warband of that kind) is the quantity legacy called `availableWarbands`. The plan decides whether the legacy site-ruler mapping check in `validateSupportedState` survives: it does only if nothing else already rejects an invalid mapping. Supply and resource affordability are not build gates: the transformed `SpendSupply` and `PayCost` own them.

Registry entries: `rollDecisionId = None`; `continuationFor` maps `muster.source` / `trade.source` to the generic walker decision continuation; `fallbackKind` is `MajorActionKind.Muster` / `Trade`; `modifierWindow` is `MusterModifierSelection` / `TradeModifierSelection`. New windows: `MusterSourceSelection`, `TradeSourceSelection`, `MusterGain`, `TradeGain`.

## Start flow

Two steps, matching what the player already does (choose the action, choose the card, confirm): the action control sends `StartWalker("muster")` or `StartWalker("trade", [resource])`, the walk parks at the source decision, and the answer is the chosen card. The three board-target actions `muster`, `trade-favor` and `trade-secret` are removed; the actions become start controls like `recover` and `forge`, and the parked decision is rendered by the generic walker-decision UI. Control strings and option labels are preserved.

## Preview of a parked decision

`WalkerSimulation.run` rejects a tree that parks. It gains a preview that walks to the parked `Decide`, resolves it once per `ChooseOne` option against the actual `PendingTree`, runs each to the end, and returns each option's recorded operations, or the failure that dropped it. Restricted to `ChooseOne` in this slice; other query shapes are not previewed. It is the command path minus the journal, so an option it accepts is an option the answer accepts.

It is used at three points:
1. **Projection.** A start control is offered only if at least one option survives. The parked decision shows only surviving options, each annotated with cost and gain read off its recorded operations (`1 Supply`, `+N warbands`, `+N favor`, `+N secrets`), as `TravelProcedure.candidates` does for Travel.
2. **Start.** After the walk parks at the first decision, `StartWalker` runs the same preview and rejects with a typed violation, before anything is persisted, if no option survives. This is opt-in per registry entry; Recover, Forge and Search are unchanged.
3. **Answer.** Unchanged: an option that slipped through fails during execution, atomically, and the park is unchanged.

Preview targets for `muster` and `trade` come from the same preview in `GameApplicationService.walkerTargets`, costed with the selected invocations the client sends to `GameApplicationService` in the preview request.

## Vocabulary

- `DecisionOptionRef.Edifice(EdificeId)`, wire kind `edifice`: a target can be an edifice at the site (`EconomyTargetRef.Edifice`), whichever side is up, because the edifice's side does not matter to Muster or Trade, and the vocabulary has no edifice variant. Economy is the first procedure to offer one. It is added to `fromWire`, the start-argument wire form, the projected-option and journal codecs, and the frontend, each with a round-trip test.
- A per-option annotation on the projected `DecisionOption` (`details: Vector[String]`) through the protocol DTO, codec and frontend, so any previewed decision can show its options' consequences.

## Extension by powers (not in this slice)

Powers hook the decision through its window: `Decide.window` lets a `Transform` replace the query, so Golem Legions adds its own reference to the `SourceSelection` query and the gain window carries its matching rule. No new contribution kind and no registry or walker change is expected. Golem Legions nevertheless needs two changes inside the Muster procedure, and both are known extension points, not surprises:

1. **A per-source token-free exception.** Golem collects favor from its first muster, so from its second muster it carries tokens and the token-free rule would reject it. The exception mechanism this slice defers is therefore needed by Golem itself, not only by Battle plans.
2. **A wider accepted-card rule.** Golem is one of the actor's advisers, not a card at the pawn site. `MusterSource.resolve`'s acceptance function, and the base query if the power does not add the option itself, must allow the actor's advisers as sources.

A test in this slice proves the query hook with a test-only transform that adds an option and asserts it appears, annotated, in the preview. It does not cover the two points above, which land with Golem.

## Cutover and deletion

Order, as with Search: (1) add the preview, the windows, the `Edifice` reference and the option annotation; (2) add the procedures and registry entries; (3) add a differential parity test against the legacy result; (4) delete the legacy path.

Deleted in step 4: `Economy.handle`, `evolve`, `legalMuster`, `legalTrades`, `EconomyCommand`, `MusterResult` and `TradeResult`; the `OathRules` Economy dispatch and `Mustered`/`Traded` evolve cases; `GameCommand.Muster/Trade`, their `Authorization` helpers and their `GameApplicationService` dispatch and action-kind lines; `Intent.Muster/Trade` with their decoders, codec cases and mapper entries; `Mustered` and `Traded` with their wire and journal codecs and protocol entries; the `muster`, `trade-favor` and `trade-secret` board-target actions; the `legalMusters/legalTrades` DTOs and codecs, if nothing else reads them; the frontend `LegalMuster`/`LegalTrade` aliases and the `ServerUiSupport` intents for them; the `GameRoutes` preview cases; and any Economy violations left without a user. `ModifierWorkflow` adds `muster` and `trade` to its walker actions. Kept: `PowerRuntime`'s `Muster`/`Trade` fallback kinds and the 24 diagnostic powers. Pre-release history compatibility is not required by the approved walker design.

## Verification

Before deletion, compare legacy and walker results for the same legal scenarios. Parity is possible only in exile-only, fixed-unaltered-Foundation states, because legacy rejects every other state, so the parity fixtures are limited to those. Compare complete authoritative state, `CardIndex`, public projection and player-scoped projection, for these scenarios: Muster with 0 to n matching advisers and with a warband shortage; Trade for Favor with and without a bank shortage; Trade for Secrets; an edifice target; and each illegal state (no Supply, no Favor or Secrets, and no token-free card at the site), which must offer no control and reject a forced start with no state change. The removed gates have no legacy behaviour to compare, so they get explicit expected-state tests: a Citizen and a Chancellor Muster gain Imperial warbands, and an altered Foundation profile no longer blocks Muster or Trade.

Also test: the preview per option, including a dropped option and an all-dropped start; journal round trip and replay through the recorded-operations path; the option set and order equal to the legacy candidate set; preview costing with a selected modifier; wrong start arity or kind; the `Edifice` reference and the option annotation through every codec; and the test-only transform above. Existing Muster/Trade projection, protocol and frontend tests are migrated to the walker forms, not deleted.

## Settled by review and by reading the code

- The best-effort `Gain` takes as many warbands from the supply as it can, so the tree requests `1 + matching` and carries no manual `min` (see Procedure composition).
- The token-free check applies to every source unless a rule states otherwise; this slice has no exception (see Procedure composition).
- A rejected start leaves no journal entry and no pending state. The plan still adds a test for the all-dropped start.
- Generic rendering: the frontend's `WalkerPanelSupport` already renders any choose-one decision no action-specific panel claims and builds the answer from the option's `kind` and `id`, so Denizen and Edifice options need no bespoke panel. Showing the new per-option `details` there is new work in this slice.
- The bounded warband supply check is dropped as redundant (see Procedure composition).
- Journal shape: the answer is the generic `ChooseOneAnswer` carrying a `DecisionOptionRef` as its `kind` and `wireId` pair (`DecisionAnswerCodec`), so it round-trips once the `Edifice` variant is in `fromWire`; replay applies the recorded operations only.

## Left for the plan

- The exact state that makes a start reject when no option survives (the registry opt-in and its violation).
- Whether the legacy site-ruler mapping check survives (see Procedure composition).

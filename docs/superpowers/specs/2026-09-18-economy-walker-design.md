# Muster and Trade on the Procedure Walker

> Status: approved design. Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the recipe of the Travel and Take Wealth ports in its Migration status. No implementation is authorized by this document alone.

## Goal and scope

Move Muster and Trade (the legacy `Economy` object) onto the walker as two registry entries, `ActionRef.Muster` and `ActionRef.Trade`, and delete the legacy Economy path in the same slice, only after parity and replay proof.

Out of scope: the 24 Muster/Trade powers in `MusterPowers` stay diagnostic-only (`ReviewedHandler`) and keep working through the registry's `fallbackKind`; migrating them onto `ContributingPower` is a later step. No rule changes: costs, gains, gates, candidate order, labels, and projected control strings are preserved. Any rule defect found on the way is a separate change.

## Procedure composition

Each action has its own procedure object beside `TravelProcedure` and `ForgeProcedure`. Neither declares a `Decide`: the player's choices ride `StartWalker` as the start selection, as Travel's destination and Take Wealth's resource do. Muster takes `[card ref]`. Trade takes `[card ref, resource Button]`, where the button key is `favor` or `secret`. A wrong arity or kind is a typed rejection, with its own test.

```
Sequence(                                        // window = <Action>ActionEligibility
  Sequence(                                      // window = <Action>Cost
    PayCost(actor, OnCard(card), cost),
    SpendSupply(actor, 1)),
  BuildOps(gain))                                // gain read from state after the cost
```

The cost node is a composite so a future power transform sees both children. Costs match the legacy operations: Muster pays 1 Favor on the card; Trade for Favor pays 1 Secret; Trade for Secrets pays 2 Favor, one of them burnt. Gains: Muster `Gain.Warbands` of `min(1 + matching faceup advisers, available warbands)`; Trade for Favor `Gain.Favor` of `min(1 + matching advisers, bank favor of the card's suit)`; Trade for Secrets `Gain.Secrets` of the matching-adviser count. A zero gain runs nothing and records nothing. Whether the best-effort `Gain` shrink can replace the manual `min` is settled in the plan: `availableWarbands` subtracts the player's board and site warbands from the lineage supply, which may differ from the bank shrink.

Start gates stay in `build`, because they are facts about the state and not costs: the act-phase gate, a pawn site, the card being at that site with no tokens and having a catalog suit, the first-game exile-only and Foundation-profile checks, a bounded warband supply for the lineage, and `PowerRuntime.requireAudited`. Supply and resource affordability are not build gates: the transformed `SpendSupply` and `PayCost` own them, so a rejection leaves no pending state. Fresh start and resume build the same tree.

Registry entries: `rollDecisionId = None`, `continuationFor = (_, _, _) => None`, `fallbackKind` `MajorActionKind.Muster` / `Trade`, `modifierWindow` `MusterModifierSelection` / `TradeModifierSelection`.

## Vocabulary: Edifice option reference

A target can be an intact site edifice (`EconomyTargetRef.Edifice`), and `DecisionOptionRef` has no edifice variant. Economy is the first procedure to offer one, so it adds `DecisionOptionRef.Edifice(EdificeId)` with wire kind `edifice` and the id as `wireId`. It is added to `fromWire`, the start-argument wire form (`WalkerStartArgWire`), the projected-option and journal codecs, and the frontend, each with a round-trip test. Do not add an Economy-specific reference.

## Candidates and projection

`MusterProcedure.candidates` and `TradeProcedure.candidates` follow `TravelProcedure.candidates`: dry-run the declared tree per candidate card with `WalkerSimulation` and read the Supply cost and the gain from the recorded operations. They replace `Economy.legalMuster` and `legalTrades`, which are a second copy of the gain formula. The candidate set is the cards at the actor's pawn site with no tokens and a catalog suit, in site order.

`LegalActionProjector` keeps the three board-target actions `muster`, `trade-favor` and `trade-secret` with the same labels and detail strings (`1 Supply`, `+N warbands`, `+N favor`, `+N secrets`). Preview targets for `muster` and `trade` move from `projection.legalMusters/legalTrades` to `GameApplicationService.walkerTargets`, next to Travel's case, costed with the modifiers the preview selected. The `legalMusters` and `legalTrades` projection fields are removed if nothing else reads them; the plan confirms this.

## Cutover and deletion

Order, as with Search:

1. Add the procedures, the registry entries and the `Edifice` reference.
2. Add a differential parity test against the legacy result.
3. Delete the legacy path.

Deleted in step 3: `Economy.handle`, `evolve`, `legalMuster`, `legalTrades`, `EconomyCommand`, `MusterResult` and `TradeResult`; the `OathRules` Economy dispatch and `Mustered`/`Traded` evolve cases; `GameCommand.Muster/Trade`, their `Authorization` helpers and their `GameApplicationService` dispatch and action-kind lines; `Intent.Muster/Trade` with their decoders, codec cases and mapper entries; `Mustered` and `Traded` with their wire and journal codecs and protocol entries; the `legalMusters/legalTrades` DTOs and codecs; the frontend `LegalMuster`/`LegalTrade` aliases; the `GameRoutes` preview cases; and any Economy violations left without a user.

The frontend `ServerUiSupport` submits `StartWalker("muster" | "trade", …)` with the target as a start argument (and the resource for Trade) instead of the removed intents. `ModifierWorkflow` already maps `muster`, `trade-favor` and `trade-secret` to their actions. Kept: `PowerRuntime`'s `Muster`/`Trade` fallback kinds and the 24 diagnostic powers. Pre-release history compatibility is not required by the approved walker design.

## Verification

Before deletion, compare legacy and walker results for the same legal scenarios: complete authoritative state, `CardIndex`, public projection, and player-scoped projection. Scenarios: Muster with 0 to n matching advisers and with a warband shortage; Trade for Favor with and without a bank shortage; Trade for Secrets; an edifice target; and each illegal start (no Supply, no Favor or Secrets, a token-bearing card, a card not at the pawn site, and the exile-only and Foundation gates), which must be rejected with no state change.

Also test: journal round trip and replay through the walker's recorded-operations path (replay applies recorded operations only); candidates equal the legacy candidate set and order; preview costing with a selected modifier; a start with a wrong arity or kind rejects; and the `Edifice` reference round-trips through every codec. Existing Muster/Trade projection, protocol and frontend tests are migrated to the walker forms, not deleted.

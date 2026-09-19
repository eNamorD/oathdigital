# Negotiation on the Procedure Walker

> Status: draft for review. Extends the [procedure walker design](2026-09-05-procedure-walker-design.md), the [walker ownership and phases design](2026-09-12-walker-ownership-and-phases-design.md) (its open item "concurrent multi-owner decisions") and the [Challenge walker design](2026-09-19-challenge-walker-design.md), whose `ChooseMany` this slice widens. Rules content stays as in [All-Exile Negotiation](../../architecture/all-exile-negotiation.md), which this slice rewrites.

## Goal and scope

Move Negotiation, a 0-Supply minor action, onto the walker as one registry entry, `ActionRef.Negotiation`, and delete the legacy start, the retired events and the fail-closed rule block in the same slice, only after a differential parity test against the legacy result.

The deal itself stays a conversation. Any participant may replace their own terms, accept or decline, in any order, and replacing terms clears every acceptance. The walker cannot express that with a single-owner `Decide`, and per-author validation, per-viewer privacy and the terms editor already work. So the conversation stays as `PendingProcedure.Negotiation`, and the walker owns everything around it: the start gate, the choice of negotiators, settlement, and the action boundary.

The slice also renames `MajorActionKind` to `ActionKind` (its own first commit) and widens `DecisionQuery.ChooseMany` from an exact count to a range.

Out of scope, each unchanged from today:
- Citizenship offers and the Grand Scepter, transfers of secrets, advisers, sites and banners, remote Negotiation, private-room Negotiation, and binding future-action promises.
- Every Negotiation power. The eight reviewed handlers (Council Arbiter, Deed Writer, Traveling Negotiator, Tribunal faces E19, Festival District E21, Grand Scepter, High Priest) and the two Negotiation starters (The Gathering and Whispering Stone) stay diagnostic-only.
- The identical first-game gate on the other minor actions (`MinorActions.validateAct`, relic peek and warband move) and on Campaign. Negotiation becomes the first minor action without it, so the inconsistency is recorded here and left for a later change.

Rule changes, all deliberate:
- **No first-game gate.** Legacy `validateBegin` required the fixed-unaltered profile, an all-Exile table, and Normal unaltered Foundations. The walker version checks none of them, as every other walker action already does ("Altered Foundations are not checked at all, by decision", Visions and Conspiracy design). Imperial and Citizen players can negotiate. The rules docs place no role restriction on Negotiation.
- **Unsupported Negotiation rules are ignored and recorded, not blocking.** Legacy `NegotiationPowerSupport` rejected a deal whenever a participant could reach an unimplemented `WHEN NEGOTIATING` handler. The walker version records `IgnoredRulesRecorded` and proceeds. All eight are permissive, so ignoring one removes an option and never permits an illegal deal.

Everything else is preserved: co-location as the base eligibility, 0 Supply, actor-first canonical participant order, terms and acceptance semantics, disclosure privacy, atomic settlement, and the action boundary after a close.

## Rename: `ActionKind`

`MajorActionKind` already labels Wake, Rest, `WhenPlayed` and `ActionBoundary`, and Negotiation is a minor action, so the name is wrong. The type becomes `ActionKind` with `ActionKind.Negotiation` (key `"negotiation"`) added. Its `key` strings are the only thing on the wire, so the rename is wire-safe. `MajorActionType` (the real major actions) and `PowerWindow.associatedMajorAction` keep their names. Frozen historical plans and specs keep the old name.

## Where the conversation lives

`CurrentGameState` keeps `pending` (legacy) beside `walkerPending`, and a comment calls it dual pending, but `startWalker`, `startTriggered` and `walkerResumeContext` all reject a walker step while `pending` is non-empty. Nothing uses both today.

This slice makes one exception and states it as an invariant:

> `pending` is a `PendingProcedure.Negotiation` if and only if `walkerProcedure` is `ActionRef.Negotiation` and `walkerPending` is parked at its `Negotiate` node.

The deal therefore has no life outside the walker. `walkerResumeContext` accepts a non-empty `pending` only in that state. The `PendingWalkerInvariantSuite` sample of every `GameCommand` is updated: `BeginNegotiation` is gone, and the other three conversation commands are rejected unless the invariant's state holds.

## Procedure

Decision ids: `negotiation.negotiators` and `negotiation.deal`.

```
Sequence(
  Decide("negotiation.negotiators", actor,            // NegotiationEligibility
    ChooseMany(1, candidates.size, candidates)),      // omitted when there is one candidate
  Negotiate("negotiation.deal", participants),        // parks while the deal is open
  Branch { (state, pending) =>                        // outcome read from the answer
    if (agreed(pending))
      Vector(BuildOps(settle, window = NegotiationSettlement))
    else Vector.empty })
```

**Candidates.** `Negotiation.eligible(state, actor)` returns the players the actor may deal with: today, the other players whose pawn is at the actor's pawn site. It is the only place that rule lives. The `Decide` builds its options from it, and the window `NegotiationEligibility` lets a later power rewrite them.

**Forced choice.** When there is one candidate the tree omits the `Decide` and `Negotiate` takes that player, as Oathkeeper does for a single leader. The tree is rebuilt on every command, and nothing that moves a pawn can run while the walker is parked before `Negotiate`, so the candidate set cannot change under the park.

**Participants.** `Negotiate` takes its participants from the negotiator answer or the forced candidate, actor first, then in table order. It takes an explicit list so a later starter such as The Gathering can embed the same node with a participant set of its own.

**Start gate.** The start control `beginNegotiation` is offered, and `StartWalker` accepts, when the actor may act in the Act phase and `eligible` is non-empty. `startable` uses the same dry run as the other start controls (`WalkerSimulation.starts`). With no candidate, `StartWalker` rejects with `NegotiationUnavailable` before anything is persisted.

**Settlement.** The `BuildOps` reads the deal from `pending` when it is reached. That is safe under the walk rule (a `Branch` selection reads only answered values or state no earlier step changed), because the `BuildOps` runs at that point rather than being selected. It emits the disclosure `Peek`s first and then the favor and relic `Give`s, so a disclosure records knowledge about a card that is still where it was disclosed, and the whole deal is one `WalkerStepRecorded`.

## The `Negotiate` node

`Negotiate(decisionId, participants)` is a new `PrimitiveOperation` with a window-free leaf, parked and resumed like a `Decide` but answered by the conversation, not by a client.

- **Open.** The first time the walk parks on it, the transition appends `NegotiationStarted(actor, decision, site, participants)` before `WalkerParked`, and `evolve` sets `pending`. The decision id is the fixed `negotiation.deal`, not a minted `negotiation-<sequence>`.
- **Close.** The three conversation commands stay `GameCommand`s (see below). When one of them ends the deal, the same transition resumes the walker with an answer built from the deal's own state, never from client input: `ChooseOneAnswer(Button("agreed"))` when the last participant accepts, `ChooseOneAnswer(Button("declined"))` when anyone declines. `Answered.by` records who closed it. Both reuse the existing `ChooseOne` answer and `Button` option, so no answer kind is added.
- **Client resume is rejected.** `ResolveWalker` at a `Negotiate` park is a typed rejection. Otherwise a client could answer `agreed` and settle a deal nobody accepted.
- **Completion.** `WalkerCompleted(ActionRef.Negotiation)` clears `pending` after an agreed deal. `NegotiationDeclined` already clears it for a decline. `runsActionBoundary` is true for `ActionRef.Negotiation`, so a close returns to action selection through the usual boundary.
- **Awaited player.** `awaitedPlayer` returns the actor at a `Negotiate` park. This only names who a continuation is addressed to. Authorization for the conversation commands is participant-based, as it is now.

## Conversation commands

`ReplaceNegotiationTerms`, `AcceptNegotiation` and `DeclineNegotiation` keep their commands, intents, codecs, HTTP mapping and frontend panel. `BeginNegotiation` is replaced by `StartWalker(ActionRef.Negotiation)`.

- Replace changes `pending` only. The walker stays parked.
- Accept validates as today (participant, no empty deal, revalidation of eligibility, quantities, unique relic allocation and disclosure authority), records `NegotiationAccepted`, and when every participant has accepted resumes the walker with `agreed`. If the resumed walk fails (settlement is revalidated against live state), the whole Accept is rejected and the deal stays open, as legacy `Completed` did.
- Decline records `NegotiationDeclined`, clears `pending`, and resumes the walker with `declined`, whose `Branch` selects nothing.

**One eligibility function for revalidation.** Legacy `validateAll` re-checked "all participants remain co-located". The walker version re-derives the eligible set through the same function and window the `Decide` used (the candidate set after the `NegotiationEligibility` window fold, whether or not the `Decide` is shown) and requires every participant to be in it. A future power that widens eligibility then applies to the start and to revalidation without editing two places.

## Powers and diagnostics

`ActionKind.Negotiation` maps to `PowerWindow.NegotiationOffer` in `PowerRuntime.window`, and the registry entry has `fallbackKind = Some(ActionKind.Negotiation)`. `startWalker` therefore records `IgnoredRulesRecorded(actor, Negotiation, diagnostics)` at the start, as every other action does. The record covers the reviewed Negotiation rules reachable from the actor's facts, not only the eventual participants' rules. That is acceptable for an audit record, and per-participant recording would need new code to enumerate each participant's accessible sources.

`NegotiationOffer` stays the diagnostic window that the eight reviewed handlers register on. `NegotiationEligibility` (on the negotiator `Decide`) and `NegotiationSettlement` (on the settle `BuildOps`) are new windows and no power hooks them yet. The registry entry has no modifier window, because none of these powers is player-selected.

## Decision vocabulary: `ChooseMany` range

`ChooseMany(count, options, ...)` becomes `ChooseMany(min, max, options, ...)`. The Challenge call sites pass `min == max`. `DecisionQueries.wellFormed` requires `1 <= min <= max <= options.size` and rejects the forced case `min == max == options.size`, which is no decision. `accepts` requires a distinct subset of the options whose size is in `[min, max]`. The projection DTO and its codec carry `min` and `max`, and the generic frontend selection panel renders the range. Journals are forward-only, so no old-journal compatibility is needed.

## Journal, projection and frontend

- **Events kept:** `NegotiationStarted`, `NegotiationTermsReplaced`, `NegotiationAccepted`, `NegotiationDeclined`. **Deleted:** `NegotiationCompleted` (which applied the deal in `evolve`; the walker step's recorded `Give` and `Peek` operations replace it, and attribution of terms is already in `TermsReplaced` and `Accepted`).
- **Continuation:** a new `OathContinue.AwaitingNegotiation(playerId, decision)` covers both the negotiator choice and the open deal, replacing `ActActionSelection` while either is open. It is registered through `WalkerProcedureRegistry.continuationFor` for both decision ids.
- **Projection:** the `negotiation` and `negotiationWaiting` fields and their per-viewer redaction stay unchanged. `WalkerDecisionProjector` produces no decision at a `Negotiate` park, and the legacy "negotiation" selection in `LegalActionProjector` is deleted. While the deal is open the legal controls are the participant controls `replaceNegotiationTerms`, `acceptNegotiation` and `declineNegotiation`, as now. `beginNegotiation` is offered only through the dry-run start gate.
- **Frontend:** the generic `ChooseMany` panel handles the negotiator choice, a new `NegotiationControls` (mirroring `BannerControls`) renders the start control, and the existing deal panel is unchanged. The legacy "negotiation" start case in `ServerUiSupport` is deleted.

## Deleted

`BeginNegotiation` (command, intent, codec, `Authorization` helper, `GameIntentMapper` entry, frontend command); `NegotiationCommand.Begin` and the minted decision id; `NegotiationCompleted` (event, wire codec, `evolve` case); the `completeAction` special-casing for Negotiation in `OathRules`; `Negotiation.legalParticipants` (renamed `eligible` and kept as the one eligibility function); `NegotiationPowerSupport` with its `ExpectedInventory` fingerprint; the `RuntimeRuleRegistry` stub and its `RuleResolution` doc note; `RuleQueryContext.Negotiation`; the `UnsupportedNegotiationRule` and `UnsupportedNegotiationCatalogInventory` violations with their `GameRoutes` messages and tests; and the legacy tests that rejected non-Exile or altered tables or blocked deals. The Rest fingerprint stays.

Kept: `PendingProcedure.Negotiation`, `NegotiationTerms` and its wire types, the disclosure privacy rules, the HSQL reopen test.

## Verification

- **Parity, before deletion.** A differential suite runs the same scripted deals through the legacy path and the walker path on the same fixtures and compares final state and per-viewer projection: favor and relic transfers, disclosures with author-only visibility before the close and adviser, held-relic and site-relic knowledge after it, decline, a replaced term clearing acceptances, and the action boundary after Accept and after Decline. The deals are Exile-only. Blocked-deal cases are excluded, because that behaviour changed.
- **Walker.** Start gate with and without a candidate; the forced single-candidate tree and the multi-candidate `ChooseMany` range; the open deal parks and `NegotiationStarted` precedes `WalkerParked`; `ResolveWalker` at a `Negotiate` park is rejected; the last Accept and any Decline resume the walker; a failed settlement rejects the Accept and leaves the deal open; the invariant between `pending` and `walkerPending`; replay of `NegotiationStarted`, `WalkerParked`, `WalkerStepRecorded` and `WalkerCompleted` reproduces the state.
- **Non-Exile table.** A deal at a table with an Imperial or Citizen player succeeds, and an altered Foundation does not block it.
- **Diagnostics.** A table with a Council Arbiter records `IgnoredRulesRecorded(Negotiation, ...)` and the deal proceeds.
- **Protocol and frontend.** `ChooseMany` min and max through every codec; the start control; the negotiator panel; migrated `GameApplicationServiceSuite`, `PendingWalkerInvariantSuite`, route and frontend suites.
- **Gate.** The full `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` gate.

## Order

1. Rename `MajorActionKind` to `ActionKind`, mechanically, with no behaviour change.
2. Widen `ChooseMany` to a range through the model, `DecisionQueries`, codecs and panel.
3. Add `Negotiate`, its walker park, resume and replay support, `ActionRef.Negotiation`, `ActionKind.Negotiation`, the registry entry, the tree, the start gate and the invariant.
4. Make the three conversation commands resume the walker, and add `AwaitingNegotiation`.
5. Frontend start control and the projection changes.
6. The parity suite.
7. Delete the legacy path and the fail-closed block, migrate tests, and update the docs: rewrite `all-exile-negotiation.md` (keeping its filename so inbound links hold), and update the walker roadmap and the remaining-cases list. Only Campaign's two `PendingProcedure` cases remain.

## Deferred

- The term-kind registry (permitted kinds, validation and settlement per kind). The eight catalog powers all need it, and the first slice that adds a term kind introduces it. Until then `NegotiationTerms` stays a closed type.
- Eligibility powers, term-kind powers, and the two starters (The Gathering, Whispering Stone). The Gathering runs a pawn-placement phase for any players and then a Negotiation among those at the site. Whispering Stone starts a private remote Negotiation for a secret. Both embed `Negotiate` with their own participant list.
- The first-game gate on the other minor actions and on Campaign.
- A per-participant record of ignored Negotiation rules.

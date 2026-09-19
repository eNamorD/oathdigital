# Negotiation on the Procedure Walker

> Status: draft for review. Extends the [procedure walker design](2026-09-05-procedure-walker-design.md), closes the "concurrent multi-owner decisions" open item of the [walker ownership and phases design](2026-09-12-walker-ownership-and-phases-design.md), and follows the [Challenge walker design](2026-09-19-challenge-walker-design.md), whose `ChooseMany` this slice widens. Rules content stays as in [All-Exile Negotiation](../../architecture/all-exile-negotiation.md), which this slice rewrites. This revision replaces the earlier draft that kept the deal in `PendingProcedure.Negotiation`.

## Goal and scope

Move Negotiation, a 0-Supply minor action, onto the walker as one registry entry, `ActionRef.Negotiation`, and delete the whole legacy path in the same slice, only after a differential parity test against the legacy result. Negotiation is the first walker procedure whose decision is answered by several players, so the slice also adds co-owned decisions to the engine.

The deal is a conversation. Any participant may replace their own terms, accept or decline, in any order, and replacing terms clears every acceptance. It is modelled as a `Repeat` loop around one co-owned `Decide`. The deal state is not stored anywhere: it is a pure fold over `PendingTree.answered`, like every other walker decision history. Nothing touches the legacy `pending` slot, so Negotiation adds no new dependence on it and removes one `PendingProcedure` case. After this slice only Campaign's two cases remain.

The slice also renames `MajorActionKind` to `ActionKind` (its own first commit) and widens `DecisionQuery.ChooseMany` from an exact count to a range.

Out of scope, each unchanged from today:
- Citizenship offers and the Grand Scepter, transfers of secrets, advisers, sites and banners, remote and private-room Negotiation, and binding future-action promises.
- Every Negotiation power. The eight reviewed handlers (Council Arbiter, Deed Writer, Traveling Negotiator, Tribunal faces E19, Festival District E21, Grand Scepter, High Priest) and the two Negotiation starters (The Gathering, Whispering Stone) stay diagnostic-only.
- The identical first-game gate on the other minor actions (`MinorActions.validateAct`: relic peek, warband move) and on Campaign. Negotiation becomes the first minor action without it, so the inconsistency is recorded and left for a later change.
- A `Simultaneous` node (see Simultaneous decisions).

Rule changes, all deliberate:
- **No first-game gate.** Legacy `validateBegin` required the fixed-unaltered profile, an all-Exile table, and Normal unaltered Foundations. The walker version checks none of them, as every other walker action already does ("Altered Foundations are not checked at all, by decision", Visions and Conspiracy design). Imperial and Citizen players can negotiate. The rules docs place no role restriction on Negotiation.
- **Unsupported Negotiation rules are ignored and recorded, not blocking.** Legacy `NegotiationPowerSupport` rejected a deal whenever a participant could reach an unimplemented `WHEN NEGOTIATING` handler. The walker version records `IgnoredRulesRecorded` and proceeds. All eight are permissive, so ignoring one removes an option and never permits an illegal deal.
- **Non-participants and the public view see the deal.** Legacy showed non-participants only a `negotiationWaiting` flag. They now see the participants, who has accepted, and the current terms at the same redaction level a non-author participant already gets (see Projection).

Everything else is preserved: co-location as the base eligibility, 0 Supply, actor-first canonical participant order, terms and acceptance semantics, disclosure privacy, atomic settlement, and the action boundary after a close.

## Rename: `ActionKind`

`MajorActionKind` already labels Wake, Rest, `WhenPlayed` and `ActionBoundary`, and Negotiation is a minor action, so the name is wrong. The type becomes `ActionKind` with `ActionKind.Negotiation` (key `"negotiation"`) added. Its `key` strings are the only thing on the wire, so the rename is wire-safe. `MajorActionType` (the real major actions) and `PowerWindow.associatedMajorAction` keep their names. Frozen historical plans and specs keep the old name.

## Procedure

Decision ids: `negotiation.negotiators` and `negotiation.deal`.

```
Sequence(
  Decide("negotiation.negotiators", actor,            // NegotiationEligibility
    ChooseMany(1, candidates.size, candidates)),      // omitted when there is one candidate
  Repeat(guard = (state, pending) => !closed(fold(pending)),
    Branch { (state, pending) =>
      Vector(Decide("negotiation.deal", owner = actor,
        coOwners = otherParticipants,
        DecisionQuery.Negotiate(snapshot(state, fold(pending))))) }),
  Branch { (state, pending) =>                        // outcome read from the answers
    if (agreed(fold(pending)))
      Vector(BuildOps(settle, window = NegotiationSettlement))
    else Vector.empty })
```

**Candidates.** `Negotiation.eligible(state, actor)` returns the players the actor may deal with: today, the other players whose pawn is at the actor's pawn site. It is the only place that rule lives. The negotiator `Decide` builds its options from it, and the window `NegotiationEligibility` lets a later power rewrite them. When there is one candidate the tree omits the `Decide` and uses that player, as Oathkeeper does for a single leader. The tree is rebuilt on every command, and nothing that moves a pawn can run while the walker is parked, so the candidate set cannot change under the park.

**Participants.** They come from the negotiator answer or the single candidate: the actor first, then the chosen players in table order.

**Start gate.** The start control `beginNegotiation` is offered, and `StartWalker` accepts, when the actor may act in the Act phase and `eligible` is non-empty. `startable` uses the same dry run as the other start controls (`WalkerSimulation.starts`). With no candidate, `StartWalker` rejects with `NegotiationUnavailable` before anything is persisted.

**The deal fold.** `fold` reads the answers whose decision id is `negotiation.deal`, in order:
- `ProposeTerms(terms)` by author *a* replaces *a*'s whole terms and clears every acceptance.
- `AcceptDeal` by *p* adds *p* to the accepted set.
- `DeclineDeal` marks the deal declined.

`closed` is true when the deal is declined or every participant has accepted. `agreed` is the second case and requires the deal to have substance (some favor, relic or disclosure). Both read only answered values, so they satisfy the rule that a `Branch` selection or `Repeat` guard reads nothing an earlier step changed. Each pass of the loop rebuilds its `Decide` from live state and the fold.

**The snapshot.** `DecisionQuery.Negotiate` is built from live state and the fold on every command. Nothing that changes a bound (favor, relic ownership, faceup or facedown state, knowledge of site relics) can run while the deal is open, so the bounds are stable, and legacy revalidation of co-location, quantities and disclosure authority becomes unnecessary. It carries:
- the participants;
- per-author bounds: eligible recipients (the other participants), maximum favor, ownable relics, and disclosable references (facedown advisers, facedown held relics, and site relics the author knows and that are still at the site);
- the set of players who may accept now (participants who have not accepted, when the deal has substance).

**Settlement.** The settle `BuildOps` reads the fold and emits the disclosure `Peek`s first and then the favor and relic `Give`s, so a disclosure records knowledge about a card that is still where it was disclosed. The whole deal is one `WalkerStepRecorded`. If settlement fails, the final `Accept` is rejected whole: its answer is not recorded and the deal stays open, as legacy `Completed` did. A decline reaches the settle `Branch`, selects nothing, and the action finishes.

**Action boundary.** `runsActionBoundary` is true for `ActionRef.Negotiation`, so any close returns to action selection through the usual boundary, whether agreed or declined.

## Engine: co-owned decisions

- **`Decide.coOwners: Vector[PlayerId] = Vector.empty`**, added after `window`. `owner` stays the primary owner: the addressee of continuations and the default of projection. Existing `Decide` call sites are unchanged.
- **Open decisions.** The park API returns `openDecisions(state, tree, pending, powers): Vector[Decide]`, which replaces `parkedDecide`. It has one element for a plain or co-owned `Decide` and none for a `Roll`. `awaitedPlayers` returns the union of the open decisions' owners and co-owners, and the active player for a `Roll`. It replaces `awaitedPlayer` (callers: `WalkerDecisionProjector`, `OathRulesWalker.parkedContinue`, `WalkerSimulation`).
- **Authorization.** `answerDecide` selects the open decision named by the answer's decision id and requires the answerer to be its owner or a co-owner. `DecisionQueries.accepts` gains the answerer, so the `Negotiate` shape can validate an answer against that author's bounds. The other shapes ignore it.
- **Answers already carry attribution.** `Answered.by` records who answered, each answer is a `WalkerStepRecorded(ChoicePayload)`, and `WalkerParked` records the answered list, so the journal needs no Negotiation-specific event.
- **Continuations.** `OathContinue.AwaitingNegotiation(playerId, decision)` addresses the primary owner, the actor, and covers both decisions. It is registered through `WalkerProcedureRegistry.continuationFor` for both ids.

### Simultaneous decisions (not built, kept possible)

Lineage setup will need players to decide at the same time. That is a different shape from a co-owned decision. A co-owned decision is one decision that any owner may answer once. A simultaneous step is N decisions, one per player, all open at once, answered in any order, and the walker continues when all are answered.

The engine changes above are the shape that step needs, and it needs no rework:
- `openDecisions` already returns a vector, and authorization already resolves the answer's decision by id.
- Answers are recorded per decision id, and guards key on ids, so arrival order does not matter.
- A future `Simultaneous(decides)` node would park at its own path, derive its unanswered children from `answered`, and complete when every child id has an answer. `PendingTree.at` is a single path, and that stays sufficient.
- Projection shows a viewer the open decisions they own and "waiting" for the rest. A continuation addressed to a set of players would join the single-player ones then.

## Decision vocabulary

- **`ChooseMany(min, max, options, ...)`** replaces `ChooseMany(count, options, ...)`. The Challenge call sites pass `min == max`. `DecisionQueries.wellFormed` requires `1 <= min <= max <= options.size` and rejects the forced case `min == max == options.size`, which is no decision. `accepts` requires a distinct subset of the options whose size is in `[min, max]`. The projection DTO and its codec carry `min` and `max`.
- **`DecisionQuery.Negotiate(participants, bounds, acceptors, heading)`**, with a `NegotiationBounds(recipients, maxFavor, relics, disclosures)` value per author. `wellFormed` requires at least two distinct participants, bounds for exactly the participants, and acceptors that are participants.
- **`DecisionAnswer.ProposeTerms(terms)`, `AcceptDeal` and `DeclineDeal`.** `accepts` for `ProposeTerms` requires the answerer to be a participant and every recipient, favor total, relic and disclosure in `terms` to be within that answerer's bounds. `AcceptDeal` requires the answerer to be an acceptor. `DeclineDeal` requires a participant.
- `NegotiationTerms`, `NegotiationTransfer` and `NegotiationDisclosure(Ref)` stay as the answer's payload. Codecs: `DecisionAnswerCodec` for the journal (reusing the existing terms encoding) and a `NegotiateWire` variant of `DecisionAnswerWire` for the client (reusing the existing terms wire type).

Journals are forward-only, so no old-journal compatibility is needed.

## Powers and diagnostics

`ActionKind.Negotiation` maps to `PowerWindow.NegotiationOffer` in `PowerRuntime.window`, and the registry entry has `fallbackKind = Some(ActionKind.Negotiation)`. `startWalker` therefore records `IgnoredRulesRecorded(actor, Negotiation, diagnostics)` at the start, as every other action does. The record covers the reviewed Negotiation rules reachable from the actor's facts, not only the eventual participants' rules. That is acceptable for an audit record, and per-participant recording would need new code to enumerate each participant's accessible sources.

`NegotiationOffer` stays the diagnostic window that the eight reviewed handlers register on. `NegotiationEligibility` (on the negotiator `Decide`) and `NegotiationSettlement` (on the settle `BuildOps`) are new windows and no power hooks them yet. The registry entry has no modifier window, because none of these powers is player-selected.

## Projection and visibility

The walker decision projection gains a `Negotiate` variant, and the `negotiation` and `negotiationWaiting` fields are deleted.

- **Owners** (the actor and every co-owner) get the full projection: participants, who has accepted, the current terms, and the editing inputs for the viewer: their favor, held relics, facedown advisers and relics they may disclose, and the site relics they know. The terms are redacted by viewer as legacy did. Any viewer sees favor amounts, the number of relics in a transfer and the details of faceup relics. Only an author sees the details of their own facedown relics, and only an author sees the identity behind their own disclosures.
- **Everyone else, including the public view**, gets the waiting projection extended with a read-only deal: participants, who has accepted, favor amounts, relic counts with faceup relic details, and the kind and recipient of each disclosure. It is exactly what a non-author participant sees, so it needs one more branch in the existing redaction and no new rule. It has no editing inputs, and it never shows facedown relic identities or disclosed identities.
- `WalkerWaitingProjection` names all awaited players, not one.
- `WalkerParked.answered` holds full terms including disclosure identities. The journal is server-only and never projected raw, and each projection goes through the redaction above.

## Frontend and protocol

- The negotiator choice uses the generic `ChooseMany` panel, now with a range.
- A `NegotiationControls` (mirroring `BannerControls`) renders the start control, gated by the dry run.
- The deal panel is rebuilt on a decision-state class in the pattern of `DistributeDecisionState` and `PartitionDecisionState`. It renders the deal for every viewer and shows the editing controls only to owners. It submits `ResolveWalker` with a `NegotiateWire` answer.
- Deleted from the protocol: the `BeginNegotiation`, `ReplaceNegotiationTerms`, `AcceptNegotiation` and `DeclineNegotiation` commands with their intents, decoders, codecs, `Authorization` helpers and `GameIntentMapper` entries, and the `negotiation` and `negotiationWaiting` projection DTOs with their codecs.

## Journal

Every answer is `WalkerStepRecorded(ChoicePayload(decisionId, answer, by))`, and the settlement is the walker step's recorded `Peek` and `Give` operations. `WalkerParked` re-records the answered list each time the deal re-parks, so a deal with k answers journals O(k²) entries. Terms are small, and the growth is accepted. All five Negotiation events (`NegotiationStarted`, `NegotiationTermsReplaced`, `NegotiationAccepted`, `NegotiationDeclined`, `NegotiationCompleted`) are deleted.

## Deleted

`PendingProcedure.Negotiation`; the five Negotiation events with their wire codecs, `evolve` cases and the `FirstGameSetup` event-match cases; `NegotiationCommand` and `Negotiation.handle`/`evolve` (the file becomes the deal fold, snapshot and settlement); the four commands, intents, codecs and projection above and `LegalActionProjector`'s negotiation controls and selection; the minted `negotiation-<sequence>` decision id; the `completeAction` special-casing for Negotiation in `OathRules`; the `NegotiationHttpCodec`, which nothing in main code calls; `NegotiationPowerSupport` with its `ExpectedInventory` fingerprint; the `RuntimeRuleRegistry` stub and its `RuleResolution` doc note; `RuleQueryContext.Negotiation`; the violations `NegotiationDecisionMismatch`, `NegotiationOutcomeMismatch`, `UnsupportedNegotiationRule` and `UnsupportedNegotiationCatalogInventory` with their `GameRoutes` messages and tests; the frontend legacy deal panel, commands and `ServerUiSupport` helpers; and the legacy tests that rejected non-Exile or altered tables or blocked deals. `NegotiationUnavailable` stays for the start gate. The Rest fingerprint stays.

Kept: the terms model types, and the HSQL reopen test, migrated to reopen a parked deal from the journal.

## Verification

- **Spike, first.** Before anything depends on it, a walker test proves that a `Branch` inside a `Repeat` body containing a co-owned `Decide` parks, resumes on answers from two different owners across several passes, exits on the guard, and replays to the same state. If it fails, fix the walker when the fix is local and comes with tests, and otherwise stop and bring the failure back for a decision.
- **Engine.** A co-owner and the owner may answer, and a non-owner may not. An answer for a decision id that is not open is rejected. `accepts` receives the answerer. `openDecisions` has length 1 for a `Decide` and 0 for a `Roll`. Existing single-owner tests pass unchanged.
- **Deal.** Fold semantics: a replaced term clears acceptances, an accept by an already-accepted player is rejected, an empty deal cannot be accepted, and any participant's decline closes it. A `ProposeTerms` outside the author's bounds (favor above balance, an unowned relic, a disclosure the author cannot make, a recipient outside the deal) is rejected. A failed settlement rejects the final accept and leaves the deal open. The forced single-candidate tree and the multi-candidate range. The start gate with and without a candidate.
- **Non-Exile table.** A deal at a table with an Imperial or Citizen player succeeds, and an altered Foundation does not block it.
- **Diagnostics.** A table with a Council Arbiter records `IgnoredRulesRecorded(Negotiation, ...)` and the deal proceeds.
- **Visibility.** Each viewer class (author, other participant, non-participant, public) sees exactly the redaction above, and the non-participant and public views never contain a facedown relic identity or a disclosed identity.
- **Parity, before deletion.** A differential suite runs the same scripted deals through the legacy commands and the walker on the same fixtures and compares final state (favor, relics, disclosure knowledge for adviser, held-relic and site-relic cases) and, for participants, which viewer can see which terms. The deals are Exile-only. Blocked-deal cases are excluded because that behaviour changed, and non-participant visibility is excluded because it is new and has its own test. The legacy path stays until the deletion commit.
- **Protocol and frontend.** `ChooseMany` min and max and the `Negotiate` query and answers through every codec, the start control, the negotiator panel, the deal panel for owners and spectators, the migrated `GameApplicationServiceSuite`, `PendingWalkerInvariantSuite` (its command sample loses the four Negotiation commands), route and frontend suites.
- **Gate.** The full `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` gate.

## Order

1. Rename `MajorActionKind` to `ActionKind`, mechanically, with no behaviour change.
2. Widen `ChooseMany` to a range through the model, `DecisionQueries`, codecs and panel.
3. Engine: `Decide.coOwners`, `openDecisions`, `awaitedPlayers`, `accepts` with the answerer, and the spike tests.
4. Vocabulary: the `Negotiate` query, the three answers, and their journal and wire codecs.
5. Procedure: the deal fold, snapshot and settlement, the tree, the start gate, `ActionKind.Negotiation`, the registry entry and `AwaitingNegotiation`.
6. Projection, protocol and frontend.
7. The parity suite.
8. Delete the legacy path and the fail-closed block, migrate tests, and update the docs: rewrite `all-exile-negotiation.md` (keeping its filename so inbound links hold), update the walker roadmap and remaining-cases list (only Campaign's two cases remain), and mark the ownership design's multi-owner item closed.

## Deferred

- The term-kind registry (permitted kinds, validation and settlement per kind). The eight catalog powers all need it, and the first slice that adds a term kind introduces it. Until then `NegotiationTerms` stays a closed type.
- Eligibility powers, term-kind powers, and the two starters (The Gathering, Whispering Stone). The Gathering runs a pawn-placement phase for any players and then a Negotiation among those at the site. Whispering Stone starts a private remote Negotiation for a secret. Both would embed the same Negotiation tree with their own participant set.
- The `Simultaneous` node for Lineage setup.
- The first-game gate on the other minor actions and on Campaign.
- A per-participant record of ignored Negotiation rules.

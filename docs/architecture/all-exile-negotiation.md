> **Note (2026-09-19): ported to the procedure walker.** Negotiation now runs as
> `ActionRef.Negotiation` on the generic walker. The legacy `PendingProcedure`
> path, its four commands and its five events are deleted. Design:
> `docs/superpowers/specs/2026-09-19-negotiation-walker-design.md`. The file name
> is kept so inbound links hold.

# Negotiation

Negotiation implements the Combined Rulebook page 27 and New Foundations page 15 procedure. It is a persistent 0-Supply minor action started by the active player with one or more co-located players, and it takes no start selection.

## Procedure

`NegotiationProcedure` (`gameplay/actions/negotiation/`) builds one tree:

```
Sequence(
  Decide(negotiators)              -- omitted when exactly one candidate exists
  Repeat(!closed) { Branch -> Decide(deal, co-owned) }
  Branch(agreed -> BuildOps(settle)))
```

- The negotiator choice is a `ChooseMany(1, n, ...)` over the other players at the actor's site. Its window is `NegotiationEligibility`.
- The deal is one `Decide` whose owner is the actor and whose co-owners are the other participants. Any owner may answer, in any order, and the `Repeat` re-asks it after each answer until the deal closes.
- A start is refused when no other player has a pawn at the actor's site (`NegotiationUnavailable`). There is no first-game gate: the action is available to any role and any Foundation face.

## The deal is a fold over the answers

Nothing about the deal is stored. `PendingTree.answered` is the only record, and `NegotiationDeal.fold` derives the deal from it on every command:

- `ProposeTerms(terms)` replaces that author's whole terms and clears every acceptance.
- `AcceptDeal` adds the answerer to the accepted set.
- `DeclineDeal` closes the deal.
- The deal is agreed when every participant has accepted and at least one term has substance (favor, a relic, or a disclosure). An empty deal cannot be accepted.

The `Negotiate` decision query is a snapshot rebuilt from live state and the fold on every command. It carries the participants, everyone's terms, who has accepted, each author's own bounds (recipients, favor held, relics held, information that may be promised) and who may accept now. `DecisionQueries.accepts` judges a proposal against the answerer's own bounds, which is why it receives the answerer.

## Settlement and disclosure

Settlement is one recorded step. It validates that every author can still afford their terms and still holds their offered relics, then emits disclosure `Peek`s first, while the cards are still where they were disclosed, and the favor and relic `Give`s second, in participant order. If the state changed so that a term is no longer affordable, the final accept is rejected with `InsufficientFavor` or `NegotiationUnavailable` and the deal stays open. A decline applies nothing. Either close runs the action boundary.

Disclosures are binding deal terms rather than an immediate sharing command. Completion grants selected recipients persistent adviser, held-relic, or site-relic knowledge. Adviser and held-relic knowledge follows card identity; site-relic knowledge remains site-scoped. Any future move into a shuffled or otherwise randomized opaque zone must explicitly clear applicable knowledge.

## Visibility

Owners get the full deal with editing inputs, computed from their own bounds. Every other viewer, including the public view, gets a read-only deal through the walker waiting projection: the participants, who has accepted, favor amounts, relic counts, faceup relic details, and each disclosure's kind and recipient. Only an author sees the identity of a facedown relic they offer or of information they promise.

## Unsupported Negotiation rules

`When Negotiating` handlers the engine does not support (Council Arbiter, Deed Writer, Traveling Negotiator, Tribunal faces, Festival District, the Grand Scepter, and High Priest) are ignored and recorded, not blocking. `StartWalker` records them through `IgnoredRulesRecorded` under `ActionKind.Negotiation`, the same way the other actions record theirs. Separate action powers such as Whispering Stone do not modify a base Negotiation merely by being held. The engine does not infer mechanics from rules text.

## Journal

Each answer is a `WalkerStepRecorded` carrying a `ChoicePayload`. The five legacy Negotiation events no longer exist, and journals are forward-only.

## Deferred

Citizenship and the Grand Scepter, secrets/adviser/site/banner transfers, remote and private Negotiation, future-action promises, and all component-specific Negotiation powers remain deferred. The `NegotiationEligibility` and `NegotiationSettlement` windows and the explicit `participants` of the `Negotiate` query are the hooks those powers will use.

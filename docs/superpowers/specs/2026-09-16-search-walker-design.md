# Search on the Procedure Walker

> Status: approved design. Extends the [procedure walker design](2026-09-05-procedure-walker-design.md), [declarative decisions design](2026-09-10-declarative-walker-decisions-design.md), and [Rest and phase powers design](2026-09-13-rest-walker-and-phase-powers-design.md). Requires the [best-effort core operations design](2026-09-16-best-effort-core-operations-design.md) before implementation. No implementation is authorized by this document alone.

## Goal and scope

Move Search and facedown-adviser card play onto the walker through one reusable card-play subtree. Activate Silver Tongue's adviser limit and implement Dazzle as the first executable `WHEN PLAYED` power. Remove Search's legacy pending procedure, commands, events, projection, and the retired Silver Tongue and Dazzle diagnostic handlers only after parity and replay proof.

Other `WHEN PLAYED` powers remain diagnostic-only. This slice does not implement Homeland, People's Favor, or other unimplemented permissions to discard site denizens; it does not make site denizens discardable by default. Card play outside Search and facedown-adviser play remains outside this cutover unless needed to share the same existing rule path.

## Procedure composition

`SearchProcedure` is one registered action. Its tree validates the supported Search state, source, actor, Supply cost, and temporary-hand availability; spends Supply; draws the authoritative source order; advances the Visions Drawn track with one zero-argument recorded `AdvanceVisionsDrawn` operation when a world-deck Vision stops the draw; asks the card-selection decision; and embeds `CardPlayProcedure` for the kept card. World deck uses head-as-top; regional discard uses end-as-top. World draws stop at the first Vision, with at most three cards. A one-card draw chooses that card without parking a forced selection.

`CardPlayProcedure` is a reusable subtree builder, not a nested walker invocation. The facedown-adviser entry wraps it in a thin registered walker procedure. Search and facedown-adviser play therefore share placement legality, replacement selection, card mutations, and the post-play hook. Each enclosing action completes its own action boundary once, after all placement and triggered effects finish. A parked decision is rebuilt from authoritative state on resume; client-supplied options and operations are never trusted.

### Search modifiers

Player-selected Search modifiers are offered and ordered before either procedure starts. Both the Search entry and the facedown-adviser wrapper declare `SearchModifierSelection` as their registry `modifierWindow`. The pre-start preview and `StartWalker` validation use the same offerability predicate; an unknown, inapplicable, or unselected player-selected power cannot enter the walk. The start command carries selected power IDs in `modifiers`, separate from Search source or facedown-adviser card selections in `startArgs`.

The selected powers participate during the walk, only at windows their contributions declare. Search can expose eligibility, cost, and before-draw windows; the shared card-play subtree exposes placement and played-card windows. A facedown-adviser play encounters only the latter subtree, so selecting a modifier cannot silently grant an effect at a Search-only window it never visits. The selected IDs are persisted as `walkerModifiers` on park, used to rebuild the same transformed tree on every resume, and cleared on completion. Automatic powers, including Silver Tongue and Dazzle, require no preselection. This slice does not invent a selectable Search power: if none is executable, the offered list is empty, while applicable unimplemented reviewed handlers retain their diagnostics.

## Card selection

Use existing `DecisionQuery.Partition` and `DecisionAnswer.PartitionAnswer`. Search declares `Keep` and `Discard` sections. Keep has minimum and maximum one; Discard has minimum zero. Add an optional `maxAllowed` field to generic `DecisionSection`, defaulting to no maximum, and validate section bounds and answer counts in `DecisionQueries`. Extend query projection and generic frontend partition controls so the maximum is represented and enforced. No Search-specific query or answer type is added.

The one Keep placement identifies the card to play or discard. The order of Discard placements in the answer vector is authoritative discard order; validators and codecs must preserve that vector order. Validation rejects duplicate, missing, extra, or over-capacity placements. Search moves the unkept cards to the next-region pile in submitted order, retaining the existing stack convention.

## Shared placement and capacity

The card-play subtree builds legal discard, site, and adviser choices from current state and catalog restrictions. A placement decision is projected from the same query the walker validates. Resuming against a changed state rebuilds the query, so a stale choice is rejected. Illegal direct placement returns a typed violation. A legal placement may require a subsequent replacement decision; that decision names only currently discardable candidates.

Adviser placement uses one window for both orientations, renamed `SearchPlayAdviser` from `SearchPlayFacedownAdviser`. With space below the effective limit, the card enters an empty adviser slot. At the limit, the player must choose a legally discardable adviser to replace. If none exists, adviser placement is unavailable and a direct attempt returns a typed violation. Silver Tongue's contribution reduces its holder's limit from three to two and guards against a completed tree leaving the holder above two. The limit applies to faceup and facedown advisers.

Site placement uses an empty denizen slot when available. A full site offers a replacement decision only when an already-supported rule explicitly grants permission and identifies legal discard candidates. Site denizens have no general discard permission. If no empty slot or permitted replacement exists, site placement is unavailable and a direct attempt returns a typed violation. Homeland, People's Favor, and other unimplemented powers grant no permission in this slice.

Replacement and placement operations retain existing card orientation, resource-return, edifice, favor-gain, and regional-discard semantics. The shared builder must not copy the old reducer's mutable state changes into a second authority.

## `WHEN PLAYED` hook and Dazzle

After faceup site or adviser placement, the subtree visits a semantic `CardPlayed(card, resultingSource)` composite at existing `PowerWindow.ActionCardPlayed`. This is a generic hook, not a new `PowerWindow`. It carries the just-played card identity and its resulting source so a contribution need not guess from state. The composite has no base children; a power may insert effect operations. Facedown placement and placement that discards the kept card do not visit the hook.

Register Dazzle as an automatic walker contribution. It matches only a faceup play of Dazzle, reads the staged post-placement state, and declares `Discard.Denizen` operations for Hearth and Order cards at sites in the actor's region in deterministic site/card order. These operations use the prerequisite's default best-effort policy. The generic staged pipeline skips a card only for a typed discard-immunity or other rule-impossibility reason; eligible cards are discarded even when another target is immune. An unexpected structural error still rejects the command transaction. Discard operations use existing semantic `Discard` behavior for card orientation and resource returns. Dazzle contains no special filtering algorithm.

Other reviewed `WHEN PLAYED` handlers remain diagnostic-only. Remove Dazzle's retired reviewed diagnostic handler and Silver Tongue's Search diagnostic handler only when their walker behavior is live on both relevant entry paths. Retain source-scoped durable diagnostics for unimplemented powers, without duplicating diagnostics for an implemented effect.

## Journal, replay, and cutover

Walker events record choices, final operation batches, contribution order, and parked/completed facts. Replay applies recorded operations; it does not recalculate Search, Dazzle, or Silver Tongue. Hidden card identities and decision options remain player-scoped in projection. The Search draw port remains a command-boundary preparation mechanism; authoritative source order must validate any prepared draw.

Before deletion, compare old and new Search results for the same legal scenarios: complete authoritative state, event intent, continuation, `CardIndex`, public projection, and player-scoped projection. Keep explicit expected-state tests for Dazzle because the old path recorded only a diagnostic, not its effect. Once parity and replay tests pass, remove `PendingProcedure.Search`, Search-specific start/complete commands and events/codecs, bespoke Search projection and frontend resolution, and the facedown-adviser bespoke placement path. Preserve unrelated legacy paths and diagnostics. Pre-release history compatibility is not required by the approved walker design.

## Verification

Focused tests cover world and regional draw order, Vision stop and track, Supply costs, ordered discards, one-card draw, site and adviser capacity, replacement permission, Silver Tongue at exactly two advisers, facedown versus faceup hook behavior, Dazzle on both entry paths, mixed immune and discardable targets, resource returns, and typed failures. Test Search modifier preview/command parity, rejection of unoffered IDs, both entry paths' modifier windows, and selected-ID preservation across park, reload, and resume. Include command-versus-replay parity, persisted parked-decision reload, stale-answer rejection, hidden-information projection, and a drift test comparing recorded operations with a rederived powered Search walk. Run the full backend test, frontend test, frontend link, architecture, and diff-check gates used by this repository.

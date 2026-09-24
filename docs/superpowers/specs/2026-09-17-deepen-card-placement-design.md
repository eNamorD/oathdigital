# Deepen Card Placement

Status: design approved in conversation on 2026-09-17; written spec awaiting review.

## Purpose

`CardPlay` validates a `SearchPlacement` and plans its core operations. `CardPlayProcedure` currently constructs the four placement intents, discovers replacement cards, and probes `CardPlay.plannedOperations` for each intent and replacement. The placement rules therefore span two modules, while `CardPlayProcedure` also owns walker decision wiring. Recent Vision and Silver Tongue fixes crossed both files.

Move legal-choice discovery beside validation and operation planning. Keep the reusable walker subtree and its power window.

## Ownership and interface

`CardPlay` owns domain-level legal placement choices for a held card. A choice identifies a base `SearchPlacement` and its ordered legal replacement card IDs. `CardPlay` receives the current `ReadyGame`, actor, card, origin, and effective faceup/facedown adviser limits. It checks candidate placements by using its existing validation and planning rules. It returns no `DecisionOption`, button key, query, or walker operation tree.

Preserve the existing intent order: discard, site, faceup adviser, facedown adviser. Preserve replacement order from the site denizens, revealed Vision, or advisers as applicable. If a direct placement is legal, its replacement list remains empty; only probe replacements when the direct placement is not legal. Omit a placement when neither direct nor any replacement is legal. The facedown card being played remains excluded from adviser replacement candidates.

`CardPlayProcedure` remains the walker adapter. It validates that the card is held at the selected origin; builds the `PlacementTree`; maps domain choices to existing decision buttons and card options; records and interprets the two answers; and constructs the `BuildOps` leaf and `CardPlayed` hook. Decision IDs, headings, option labels, answer types, ordering, and failure text remain unchanged. A selected replacement is converted to `SearchPlacement` for the existing `CardPlay.plannedOperations` call.

Keep `CardPlay.plannedOperations` available to `SearchProcedure` for discarded, unkept cards. That Search loop stays in Search: discarding those cards has no placement-choice limitations. `CardPlay.playedSource`, the Conspiracy handoff, and the played-card hook stay at their current ownership seams.

## Power and parked-decision behavior

`PlacementTree` retains `PowerWindow.SearchPlayAdviser` and its limit-transform methods. Silver Tongue continues to select an effective adviser limit and append its post-placement guard. `CardPlay` receives the effective limits as data; it does not know Silver Tongue or the power catalog. Faceup and facedown limits remain independently applicable, including when Silver Tongue itself is being played.

Legal choices are derived when the tree is built or rebuilt. Do not persist or reuse a pre-decision operation plan. At execution, `BuildOps` calls `CardPlay.plannedOperations` against the current state, preserving final validation after a parked decision. Preserve current walker replay and event semantics.

## Scope and preservation

This is a behavior-preserving ownership refactor. Do not change placement rules, Search card-selection or discard flow, facedown-adviser start gates, power timing, legacy Conspiracy continuation, operation requiredness, protocol DTOs/codecs, or frontend controls. Do not introduce a generic placement-policy module, new adapter interface, or persistent choice type. Any discovered rule defect is a separate change.

## Verification

Bracket the refactor with the existing `CardPlayProcedureSuite`, `SilverTongueSuite`, `SearchProcedureSuite`, `VisionsSuite`, and application replay tests. Strengthen tests through the public `CardPlayProcedure.build` and walker seams where a preservation case is missing: placement and replacement ordering, locked adviser exclusion, full-site Homeland behavior, Vision replacement, facedown origin, Silver Tongue limits, and final operation validation. Run the full Scala and frontend suites plus architecture and Markdown-link checks. Inspect the diff for unchanged decision strings, power window, Conspiracy handoff, and absence of protocol/frontend edits.

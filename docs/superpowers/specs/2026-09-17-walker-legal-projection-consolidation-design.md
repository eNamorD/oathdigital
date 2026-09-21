# Walker Legal-Action Projection Consolidation

Status: approved in conversation on 2026-09-17. This is a behavior-preserving architecture slice.

## Purpose

`LegalActionProjector` currently asks `TravelProcedure.candidates` twice for one active-player projection: once for `LegalProjection.travel`, and once for the Travel board-target action. Each call dry-runs every Travel destination through the walker and its automatic powers. Both views must use the same candidate facts.

## Ownership

All walker-backed procedures own their candidate checks. Travel, Search, and Take Wealth may use the existing walker simulation where powers affect legality or cost. Recover, Forge, Begin Rest, phase powers, and facedown-adviser play keep their procedure-owned start or usability checks. Application projection owns labels, DTO construction, and viewer disclosure. No universal candidate-discovery module or new adapter is introduced.

## Change

For each `LegalActionProjector.project` call, compute Travel candidates at most once when the active viewer is choosing an ordinary Act action. Reuse that immutable result for both the destination projection and the Travel board-target action. Do not compute it for inactive viewers or while a pending procedure is active. Keep existing order and Supply labels.

Survey the remaining walker-backed projection paths. Do not change a path already computed once per projection. In particular, keep Search source discovery, Take Wealth candidates, Recover/Forge start checks, Begin Rest validation, phase-power usability, and parked walker decisions in their current owning modules. Preserve `GameProjector` and wire DTO interfaces.

## Preservation boundary

No gameplay behavior, projected control strings, candidate order, labels, viewer visibility, error handling, protocol shape, or legacy pending-procedure branches change. Any discovered legality mismatch is a separate bug fix, not part of this refactor.

## Verification

Bracket the structural edit with the same existing application projection and frontend suites. Assert active Travel destination entries and Travel board-target candidates retain matching site IDs, order, and Supply cost; inactive and pending viewers retain no Travel candidates. Run the full repository test and architecture gates before completion. Confirm source has one `travelCandidates(context)` call in the active projection path.

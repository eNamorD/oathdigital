# Phase 9 — Enforce the core-operation boundary

This document is the implementation plan for phase 9 of the core-operations
migration (see `core-operations-migration.md` for the overall migration and its
phase structure). It records the approved scope, the audit findings that shaped
it, and the concrete steps and verification gates.

## Goal

Complete phase 9, "Enforce the boundary":

- remove obsolete direct physical mutation helpers;
- add a narrow architecture check preventing migrated action modules from
  directly editing owned material fields;
- keep direct updates legal for procedure, supply, track, title, and victory
  state;
- document any intentional executor bypass beside the owning rule and cover it
  with a regression test.

Ready-game execution stays semantically identical to the pre-phase-9 state. The
one accepted internal-state change is that a Search-kept Conspiracy card
physically remains in the actor's temporary hand from Search completion until
`ConspiracyCompleted`, where it is boxed; this is invisible to projections.

## Audit findings

Two independent read-only audits of the migrated gameplay modules established:

- There are **no obsolete or unused direct physical mutation helpers** in
  `actions/*` or `phases/*`: every direct-mutating helper has a caller, and the
  remaining helpers are operation-builders, pure validators, or sanctioned
  `GameStateUpdates` uses. Phase 9's "remove obsolete helpers" therefore has no
  dead code to delete; the doc records this audit result.
- The only live, un-documented direct material mutation is
  `LeagueTreatyPower.moveFavor` (`powers/rest/LeagueTreatyPower.scala`), which
  moves site-card favor to a suit bank with direct `.copy()` chains instead of
  core operations.
- One undocumented owned-zone write is `SearchRules.complete`'s
  `temporaryHands - event.playerId` key drop.
- Two documented Conspiracy-boxing bypasses exist (`Visions.scala` and
  `Campaign.scala`): the executor conserves card inventory across a batch, so a
  played Conspiracy leaves the game only after the batch validates.

## Decisions

- **Scope**: enforcement and cleanup only; no new operation vocabulary.
- **Check surface**: everything except setup — `actions/*`, `phases/*`,
  `StateBasedEvaluation.scala`, and `powers/**`. Setup builds state directly
  and is exempt.
- **League Treaty**: migrate to `OperationTransaction` with
  `OperationPolicy.exact`, mirroring `Rest.applyCompletion`; final state is
  byte-identical.
- **`temporaryHands` invariant**: every player always has a key; an empty
  vector means no cards. Applies to the ready-game `CurrentGameState`
  temporary hands and to the setup `InProgress` temporary hands. Nothing ever
  removes a hand key. Enforcement is **soft** (by construction and targeted
  tests, not a new `DomainProblem`).
- **Conspiracy boxing**: Search-path Conspiracy is boxed at
  `ConspiracyCompleted` (`Visions.applyCompletion` removes the source card from
  the actor's advisers or temporary hand), replacing the silent Search key
  drop. Campaign-raid boxing is unchanged. Both sites carry the strict
  sentinel token.
- **Architecture check**: a `BackendArchitectureSuite` source scan over the
  migrated module set flags a curated list of owned-material write markers
  unless the nearest preceding comment line is exactly
  `// executor bypass: <reason>`. No allow-list file; the two boxing sites are
  the only permitted occurrences.
- **Docs**: record the audit result and mark phase 9 complete in
  `core-operations-migration.md`.

## Implementation steps

### 1. Migrate League Treaty favor movement to operations

`src/main/scala/oathdigital/gameplay/powers/rest/LeagueTreatyPower.scala`

Replace `moveFavor` with an operation-backed resolution:

- Build one
  `CoreMove(Piece.Favor(amount), PositionedLocation(Location.OnCard(cardId)),
  PositionedLocation(Location.FavorBank(destination)))` per `FavorAllocation`,
  resolving each `SiteFavorSource` (Denizen/Edifice by id, Relic by slot) to
  its `CardId` from the current `ReadyGame`.
- Execute through `OperationTransaction.evolve` with
  `OperationPolicy.exact(operations, "League Treaty semantic root is not
  permitted")`; the direct update clears `pending = None`.
- Keep `clearPending` for the decline path and all existing validation.
- Existing `RestSuite` League Treaty flows must stay green unchanged as the
  byte-identical regression gate.

### 2. Always-key `temporaryHands` invariant (ready game)

- `OperationCardMutation.scala`: stop dropping empty hand keys on removal so a
  drained hand stays `player -> Vector.empty`.
- `FirstGameSetup.buildReady`: seed every player's empty hand key when the
  ready `CurrentGameState` is constructed.
- `SearchRules.complete`: delete the `temporaryHands - event.playerId` copy.
  Ordinary searches drain the hand through operations; the Search-kept
  Conspiracy stays in the hand until `ConspiracyCompleted` (step 4).

### 3. Always-key `temporaryHands` invariant (setup `InProgress`)

`src/main/scala/oathdigital/gameplay/setup/FirstGameSetup.scala`

- `StartingAdviserChosen`: keep the player's key and empty it instead of
  removing it.
- `FirstGameCompleted`: replace the `temporaryHands.isEmpty` guard with an
  all-values-empty check.

### 4. Box Search-kept Conspiracy at resolution

`src/main/scala/oathdigital/gameplay/actions/Visions.scala`

Extend `applyCompletion` so the Conspiracy source card is boxed from whichever
zone holds it at `ConspiracyCompleted` — the actor's temporary hand (Search
path) or the actor's advisers (direct facedown path) — after the batch
validates, leaving the hand key present but empty. Add the strict sentinel
comment above the removal. `Campaign.scala` raid boxing is unchanged but gets
the same sentinel wording.

### 5. Architecture check (strict sentinel scan)

`src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala`

Add a test walking the migrated module set and flagging each occurrence of a
curated material-write marker list (adviser-vector removals, hand-key drops,
token/card copy-write shapes such as `tokens.copy(favor = ...)`,
`banks.favor.updated`, `map.copy(sites = ...)`, empty-key drops) unless the
nearest preceding comment line is exactly `// executor bypass: <reason>`.
After steps 1-4 and 6 the only remaining occurrences are the two Conspiracy
boxing removals.

### 6. Refactor CardPlay's validation local

`src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`

Replace the local `player.copy(advisers = player.advisers.filterNot(...))`
planning value (a validation-only copy shaped like a material write) with a
non-write-shaped computation so the marker scan has no false positive.

### 7. Regression tests

Update the affected expectations (`temporaryHands.get(player) == None` becomes
`Some(Vector.empty)`), add Search-path Conspiracy hand-lifecycle assertions,
setup hand-key assertions, and an executor case proving a drained hand keeps
its empty key.

### 8. Docs

Mark phases 1-9 complete in `core-operations-migration.md` with completed
bullets for the audit result, the League Treaty migration, the always-key
temporary-hands invariant, Conspiracy boxing at resolution, the CardPlay
refactor, and the new sentinel architecture check.

## Verification gates

Full JVM suite, focused gameplay suites (Search, Visions, Rest, Campaign,
Challenge, setup, BackendArchitecture), frontend suite, `check-architecture.py`,
`check-markdown-links.py`, and `git diff --check`. No commit is made by this
task; the user owns commits.

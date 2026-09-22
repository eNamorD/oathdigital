> **OUTDATED — engine redesign superseded.** The forward architecture is the
> procedure-walker design: actions become `Operation` trees, a generic walker
> executes them, powers are contributors (`Transform`/`Restriction`), replay
> applies recorded ops only. This file is a historical record of the
> pre-walker design/code.

# Core operations migration

Status: phases 1-9 complete.

## Decision

Use `CoreOperation` as the gameplay engine's internal language for semantic
piece mutations. Commands continue to express player intent, gameplay modules
continue to decide legality and resolve choices, and `OathEvent` remains the
single durable history. Core operations are derived while an authoritative
event evolves; they are not commands, independently injectable events, or a
replacement for procedure state.

This boundary gives gameplay one implementation of physical operations without
turning the glossary into a complete rule engine. Supply, pending procedures,
turn and phase changes, tracks, Oathkeeper state, and victory remain explicit
game logic outside the operation algebra.

## Current state

`model/CoreOperations.scala` (data only; execution lives in
`gameplay/operations`) defines typed locations, pieces, primitive operations,
and glossary composites. Composite operations retain their semantic identity
and expose primitive mutations. For example, Swap contains two reciprocal
Moves, Draw contains ordered Takes, Exchange contains two Gives, and Sacrifice
contains a Kill.

The running engine now executes these values authoritatively for every migrated
action. `OathRules.evolve` routes each event to its owning gameplay module;
migrated modules translate accepted events into core operations and apply them
through the transactional executor, while un-migrated modules still rebuild
nested immutable state directly. The former `RecordedPowerOperation` mechanism
has been removed: power payments and relic placement now map to Give, Burn, and
Play operations.

Migration must preserve:

- authoritative domain events and deterministic replay;
- one legality path for live commands, replay, and projections;
- existing `OathViolation` behavior at trust boundaries;
- hidden-information and redaction boundaries;
- power timing, action completion, and state-based evaluation order; and
- exact piece, resource, card-order, and orientation semantics.

## Target evolution path

```text
command
  -> procedure validation and resolved domain event
  -> event-owned operation planner
  -> semantic restriction policy
  -> transactional operation executor
  -> module-owned non-operation updates
  -> post-state invariant checks
```

Event evolution remains one pure operation from prior `OathState` and
`OathEvent` to either an `OathViolation` or a complete next `OathState`.
The owning reducer applies operation results and directly updates non-operation
state in that pure evolution. Nothing commits unless the complete event
succeeds, and nothing writes mutable state during preflight.

Each gameplay module owns the translation from its events to core operations.
Do not add one central event-to-operation pattern match to `OathRules`.
`OathRules` remains the aggregate router, while the operation package owns only
generic policy, state access, and execution.

## Validation ownership

| Layer | Owns | Does not own |
| --- | --- | --- |
| Procedure module | actor, phase, consent, choices, costs, site capacity, rule outcomes, power windows | generic state mutation |
| Operation planner | exact semantic operations implied by an accepted event | accepting new player input |
| Restriction policy | locks, operation-specific prohibitions, typed rule exemptions | resolving choices or recalculating procedure outcomes |
| Executor | source existence, quantities, structural destination compatibility, stack behavior, atomic state updates | printed-rule legality or consent |
| Post-state validation | non-negative counts, card uniqueness, bounded-inventory conservation, valid orientations | silently repairing invalid state |

Replay must reject invalid event facts with `OathViolation`; it must not throw.
Several core-operation constructors use `require`, so planners must validate
untrusted event amounts and shapes before construction or use safe builders
that return `Either`. An `IllegalArgumentException` must never escape event
replay.

## Transaction and timing semantics

Transaction atomicity and printed simultaneity are different concepts:

- Every evolved event is transactional. Failure leaves its input state
  unchanged.
- Top-level operations in an event plan are ordered unless the owning rule says
  otherwise. Each operation sees the staged result of preceding operations,
  while failure still discards the complete event result.
- Every composite operation is preflighted against one coherent pre-operation
  snapshot and commits as one unit.
- `simultaneous` records printed timing for operations such as Swap and Replace;
  it is not the switch that provides rollback safety.

Do not flatten an operation before restriction checks or timing decisions.
Flattening would erase the difference between Bury and Move, Discard and Move,
Sacrifice and Kill, or Swap and two unrelated Moves. The executor should retain
the semantic root alongside its primitives and may return an internal receipt
containing both.

Primitive application cannot be a naïve left fold over already-mutated state.
Discard moves a card and then refers to resources on that card; Swap and Replace
temporarily conflict if their first Move commits before the second. Executor
preflight must resolve every source from the correct snapshot, calculate
deltas, validate the complete operation, and then apply them together.

Restriction policy evaluates the semantic operation and current rule context
before primitive execution. Bury remains a distinct primitive and therefore
bypasses restrictions that prohibit Move, while ordinary Move, Discard, Swap,
and Flip remain restrictable. Any exemption comes from a trusted typed rule
source, never from client data or an unrestricted boolean flag.

Operation execution does not automatically invoke powers or nested actions.
Owning procedures open power windows using the retained semantic operation or
its receipt. This prevents Reveal from triggering When Played and avoids
reentrant mutation inside the executor.

Typed power modules also own any operation replacement or modification. The
executor must not discover catalog handlers or rewrite operations. An owning
procedure resolves applicable modifiers in printed order, records any facts
needed for replay, and gives the planner the resolved result. Restriction order
can differ by printed rule, so the procedure must state and test whether a
restriction applies to the attempted operation or its replacement. Do not add a
universal operation-modifier DSL to cover these cases.

## State adapter

Current state stores material in several nested structures. Introduce one
gameplay-owned adapter that reads locations, preflights deltas, and rebuilds
state. Action modules must not each implement their own mapping.

The adapter must define these mappings exhaustively:

- cards in decks, regional discards, sites, player areas, Reliquary, Atlas, and
  Dispossessed;
- favor and secrets on player boards, cards, sites, banners, and banks;
- warbands on player boards, sites, and color-specific banks;
- pawns at sites;
- banner custody and resources; and
- public orientation and private knowledge.

`CardIndex` should provide identity and source lookup, but its container model
is more precise than core `Location`. Mapping must stay in one place and have
round-trip tests. `Location.PlayArea(player)` intentionally describes one
semantic area; adapter routes a piece to the existing board, adviser, relic,
revealed-Vision, or banner-holder field according to piece type and operation.
Likewise, `Location.Site(site)` routes cards, relics, forces, pawns, or loose
resources to their concrete site storage.

### State-model prerequisites

Phases 1-2 settled and implemented these state contracts:

1. Temporary hands are the single authoritative owner of cards awaiting a
   private choice. `CurrentGameState.temporaryHands` owns cards drawn during
   Search, while `OathState.InProgress.temporaryHands` owns first-game adviser
   candidates before ready-game state exists. Pending procedures and setup
   choices retain decision facts but do not duplicate those held card IDs.
   `CardIndex` includes ready-game temporary hands.
2. `CurrentGameState.setAsideRelics` owns relics discarded until Chronicle, and
   `CardIndex` includes this zone.
3. `MaterialBankState` and `CardKnowledge` are normal `ReadyGame` state.
   `FirstGameSupportState` now contains only setup-specific facts. Favor-bank
   readers and writers use `MaterialBankState`; private projections use
   `CardKnowledge`.
4. Treat secrets at `Location.SharedBank` as an inexhaustible virtual source
   and sink. Do not persist their count or include them in inventory
   conservation checks.
5. `MaterialBankState.warbandSupply` defines bounded inventory by `ForceKind`.
   `OperationStateAdapter` derives bank availability from board and site counts,
   validates the kind implied by lineage role, and rejects missing or impossible
   inventories with typed errors. First-game setup defines 14 warbands per
   Exile lineage and 24 bandits; future Imperial setup must supply its bound.

Card-state materialization also needs one rule. Decks store IDs while sites and
player areas store oriented card state. A Move without
`resultingOrientation` preserves an existing stateful card; moving an ID-only
card into a stateful container must either be followed by an operation that
supplies orientation or fail preflight. `Play` already supplies orientation.

### Stack conventions

Centralize stack access instead of letting each action manipulate vectors.
Current world, relic, and edifice decks use the vector head as top, while
regional discard piles use the vector end as top. `StackPosition.Top` and
`Bottom` must map correctly for each container. Tests must cover multiple cards
inserted or removed in one operation, because order can reverse even when every
single-card case passes.

`StackPosition.Unspecified` is valid only when identity uniquely determines a
source or destination order is immaterial. Executor should reject ambiguous
stack insertion instead of choosing an order implicitly.

### Secret orientation

Keep secret side separate from `Location`:

- a secret Move preserves the orientation resolved at its source;
- moving secrets into a player's PlayArea does not normalize their orientation;
- secrets on cards are faceup, so Discard naturally returns them faceup before
  applying FlipSecrets to make them facedown; and
- if a source contains both orientations and the owning rule does not identify
  which secrets move, planning fails instead of guessing.

These defaults must be executor contracts, not conventions repeated by action
modules.

### Knowledge

`Peek` is a real primitive and must not become a no-op. Execute it through a
knowledge adapter that records exactly the named card for the named viewer
without changing physical location or orientation. Promote knowledge storage
out of first-game support state before relying on this generically.

Operations and receipts remain internal privileged data. Never place raw
operations in public projections or ordinary logs: Draw, Peek, facedown Play,
and private exchanges can contain hidden card identities. Future action-history
formatting must consume scoped, redacted semantic receipts rather than expose
the executor trace directly.

## Event integration

Domain events remain authoritative and retain every resolved or random fact
needed to reconstruct the same operation plan. Core operations are not added as
separate events and are never accepted from a client. An event missing a fact
needed for deterministic planning must gain that fact, with matching codec and
replay tests, before its reducer migrates.

Live handling and replay must call the same event planner and executor path.
Command handling must not calculate one operation plan for the returned state
and a different plan for the emitted event.

Power event adapters now map their recorded `Payment` and `RelicPlacement`
facts to Give, Burn, or Play operations through `PowerOperationPlanner`; the
separate `RecordedPowerOperation` executor has been removed. Power-specific
events remain procedure-scoped.

Pre-release event format may change under the repository's existing
compatibility policy. After a public compatibility baseline exists, any change
that alters how an old event plans operations requires an explicit rules/event
version or a compatibility evolver.

## Migration phases

Phases 1 through 9 are complete. They add contracts, state/indexing, the
transactional executor, the proven shadow-cutover process, the first
operation-backed authoritative action and state-based reducers, resource and
power migration, authoritative card-flow placement, multi-party and combat
flows, and the phase-9 boundary enforcement of that migration.

### 1. Freeze behavior and settle contracts (complete)

- Keep existing command, replay, projection, and codec suites green.
- Add executor-independent tests for every composite's semantic root,
  primitive order, and simultaneity.
- Decide temporary hand, set-aside relic, general bank ownership, card
  materialization, and stack conventions.
- Introduce typed `OperationError` values and map them to stable
  `OathViolation` cases.

### 2. Prepare state and indexing (complete)

- Add missing material zones and generic knowledge/bank ownership.
- Extend `CardIndex` for every card-owning zone.
- Add exhaustive `Location`/`Piece` adapter tests.
- Preserve event serialization and public projections while internal state
  moves.

### 3. Build policy and executor (complete)

- Preflight primitive sources and destinations without mutation.
- Preserve top-level operation identity.
- Support ordered and simultaneous composites.
- Apply operation results and module-owned non-operation updates atomically.
- Validate card uniqueness, non-negative counts, legal orientation, and
  bounded-inventory conservation after every batch in tests.

`OperationExecutor` requires an `OperationPolicy`, checks each semantic root
before resolving its primitives, and returns the evolved `ReadyGame` directly
(the intermediate `OperationReceipt`/`OperationExecution` audit wrappers were
removed in Phase 1 of the engine redesign). Top-level operations stage in
order, while each composite resolves all
outbound pieces from one pre-operation snapshot and commits atomically.
`OperationTransaction.evolve` then applies an owning module's ordinary state
function and validates the complete result; it does not introduce an auxiliary
event or a second mutation language.

`OperationStateAdapter` is the sole physical mapping boundary. It owns card
container routing, stack conventions, resource orientation, derived warband
banks, pawn and banner custody, and private Peek knowledge. Executor invariants
compare the complete card identity set, run aggregate validation, enforce
Vision container orientation, and verify every current force kind against its
bounded supply. Executor results are internal state and are neither serialized
nor projected.

The operation engine is split three ways (Phase 3): `OperationValidator`
owns every pre-execution check (op shape, per-action `OperationPolicy`
allowlist, and a restriction registry filled in Phase 5) and reports
aggregated `OperationReason`s; `OperationExecutor` is raw mutation only —
it applies a validated batch and holds no policy or invariant; and
`OperationPipeline` is the sole orchestrator, assembling the validator per
run from the action's allowlist + per-query restrictions, running the
staged per-op validation fold, then the module `update` and the
`OperationStateInvariant` post-checks. `OperationTransaction` no longer
exists; replay behavior is unchanged.

Supply-capped "as much as possible" effects are resolved at plan time with
`LimitedResource.clamp` — the executor has no best-effort/requireExact mode.
Payments are typed with `Cost(favor, secret, favorBurnt, secretBurnt)` and
applied by the single `PayCost(player, placedAt, cost)` root (zero-cost
`Cost.free` is an inert no-op); `Costs.plan` is the pre-flight
affordability/placement validator. A placed cost goes onto an empty card only
(`PayCostRules`, enforced by the validator), unless `intoOccupied` is set, as
battle plans do. `matchingBank` names the paying card's suit bank. When the
payer is not the active player the pipeline settles the payment at once
(`PayCostSettlement`): placed favor goes to `matchingBank` and secrets flip
facedown, so nothing rests on the card. The recorded operation stays the
requested one, so replay derives the same settlement. Supply spending in executor-backed
operations is an `AdjustSupply(player, amount)` operation enforced exactly
against the track; procedural phase/module supply writes (Challenge, Forge,
Recover, Campaign, and Rest's refresh-to-value) remain module-authoritative
writes outside op batches.

No gameplay reducer, event codec, or projection uses the executor in phase 3.
`OperationPolicy.Permissive` exists for executor tests and already-validated
shadow paths; actor-, catalog-, and power-aware restrictions arrive with each
phase-4 planner. `RecordedPowerOperation` is retired in phase 6.

### 4. Run shadow evolution (complete)

For one event type at a time, run old reducer and proposed operation-backed
reducer from the same prior state. Compare complete authoritative state,
derived `CardIndex`, emitted events, continuation, and scoped projections.
Shadow execution must not append events, expose hidden traces, or mutate the
state returned to production.

Retain the old pure reducer until representative and adversarial parity tests
pass. Cut over and delete that event's old physical mutation in the same small
change; do not leave two permanent authorities.

`OwnedRelicRevealed`, `SiteRelicsPeeked`, and `WarbandsMoved` passed shadow
parity and now execute semantic `Reveal`, `Peek`, and `Move` operations
authoritatively through `OperationTransaction`; their legacy physical mutations
have been removed. `MinorActionOperationPolicy` restricts the executor to the
validated active actor, physical location, piece kind, and three migrated
semantic roots. Shadow comparisons proved complete ready-game and derived
`CardIndex` equality, while differential tests covered emitted events,
continuations, replay, public projection, and player-scoped projections. No
event, codec, or projection shape changed.

Phase 4 is complete because orientation, private knowledge, and counted-piece
movement each passed the expand-shadow-cutover cycle. Later migration phases
reuse the internal shadow comparison as a per-procedure safety gate without
reopening phase 4. Rollback restores each small legacy mutation without state or
history conversion.

### 5. Migrate simple operations (complete)

Start with narrow actions whose non-operation state changes are small:

- owned relic Reveal (complete);
- site relic Peek and knowledge (complete);
- player/site warband Move (complete);
- pawn Move during Travel, with supply updated directly by Travel (complete);
- Wake wealth Take (complete); and
- state-based bandit refill from the derived bandit bank (complete).

Travel now executes the pawn `Move` and applies its procedure-owned Supply
change as the transaction's direct update. Wake executes a semantic `Take` and
directly records only the used site power. State-based evaluation refills every
eligible empty site through an atomic batch of bandit-bank `Move` operations.
Each path has a procedure-scoped policy that accepts only its validated
semantic root and context; command/replay parity and exact surrounding-state
tests remain green. No event, codec, or projection shape changed.

### 6. Migrate resources and powers (complete)

- Muster: place favor, Gain warbands, and update supply (complete).
- Trade: place its payment, Gain its result, and update supply (complete).
- Forge and Recover: pay resources and move or Play relics (complete).
- Convert executable power payments and placements, then remove
  `RecordedPowerOperation` (complete).

`PowerOperationPlanner` converts canonical `Payment` and `RelicPlacement` facts
into Give, Burn, and Play operations, and the separate `RecordedPowerOperation`
executor is removed. `Economy` (Muster and Trade), `Forge`, `Recover`, and the
Catacombs power path each reconstruct their semantic roots from the accepted
event and apply them through `OperationTransaction`; supply and
pending-procedure changes are applied as the transaction's direct update.
Each path uses `OperationPolicy.exact` over the reconstructed roots, and
command/replay parity and surrounding-state tests remain green. No event,
codec, or projection shape changed.

`OperationPolicy.exact` is a marker, not a legality boundary: it pins the
executor to the module's reconstructed roots and rejects any divergent
operation, while printed-rule restrictions (locks, adviser capacity, placement
bans) remain enforced by the owning procedure. The richer restriction-policy
layer described under "Validation ownership" is realized per procedure; the
shared `OperationPolicy` surface does not yet carry lock or exemption logic.

### 7. Migrate card flows (complete)

Search, facedown-adviser actions, Vision reveal, replacements, and ordinary
card play now execute through Draw, Move, Play, and Bury operations:

- `SearchStarted` draws the recorded cards top-first from the world deck or the
  regional discard into the actor's temporary hand with a single `Draw`;
  Supply, the Visions Drawn track, and the pending Search procedure remain
  direct updates.
- `CardPlay` is the shared placement procedure behind Search completion and
  facedown-adviser play. It validates legality as before, then plans Move
  (temporary hand or advisers to a site or play area), `Gain.Favor` from the
  suit bank, ordered next-region discards, and `Bury` of a replaced edifice,
  and applies the batch through `OperationTransaction` with
  `OperationPolicy.exact`. Replaced advisers, site denizens, and revealed
  Visions leave in the recorded pile order; pending Conspiracy creation remains
  a direct procedure update. Phase 9 superseded the end-of-Search hand clear:
  every player now always holds a temporary-hand key, so completion simply
  leaves the drained hand as an empty vector (a Search-kept Conspiracy stays in
  the hand until `ConspiracyCompleted` boxes it).
- `VisionRevealed` moves the facedown Vision adviser to the revealed-Vision
  slot and discards the replaced Vision through operations.

`Move` now also accepts a card that changes role within one `PlayArea` when an
orientation is supplied (facedown adviser to faceup adviser or to the
revealed-Vision slot), matching the adapter's contract that `PlayArea` routes
by piece type and operation. Visions-drawn Conspiracy resolution stays in phase
8: its relic and banner effects use Give, Take, and banner vocabulary rather
than this phase's card-flow operations. No event, codec, or projection shape
changed.

### 8. Migrate multi-party and combat flows

Migrate Negotiation, banners, Campaign conquest and raids, Rest resource
returns, Kill, Sacrifice, Replace, Give, and Exchange. Preserve consent and
power windows in their owning procedures. Migrate setup and Chronicle material
movement only after ready-game execution is stable and their additional zones
are modeled.

Completed within this phase:

- only faceup secrets can be burned: a burnt secret returns to the untracked
  limitless SharedBank sink, and burning a facedown secret fails as if the
  source held no secrets at all;
- Negotiation settlement executes its deal through operations: favor and relic
  transfers as `Give`, disclosure knowledge as `Peek` (before any transfer
  relocates a disclosed card). This was the `NegotiationCompleted` event until
  Negotiation moved onto the walker (2026-09-19), where the same operations are
  the settle step of the deal tree;
- Conspiracy resolution executes through operations: the played Conspiracy card
  leaves the game entirely (removed after the batch validates, since the
  executor conserves card inventory), an enemy relic is taken by `Give`,
  People's Favor favor is distributed to the least-stocked banks with leftmost
  ties, and banner custody changes are `Move`s. Taking the Darkest Secret by
  Conspiracy burns every secret on the banner (a `Burn` to the untracked
  SharedBank sink) and transfers the empty banner; no site placement or
  return-to-holder occurs on a Conspiracy take. The Conspiracy secret-site
  choice machinery (`ChooseConspiracySecretSite`, `ConspiracySecretSiteChosen`,
  and the `secretSites`/`remainingSecretPlacements`/`automaticSecretSites`
  facts) was removed entirely with the corrected rule. Removing those events
  and fields is a wire-format change: any pre-release history containing a
  `ConspiracySecretSiteChosen` event would need an evolver/version bump rather
  than decoding as the current format (no persisted test stream contains one).
- Rest resource returns execute through operations: every in-play denizen,
  edifice, and relic card has its secrets returned to the resting player's
  stash and denizen/edifice favor returned to its printed suit bank, relic
  favor stays in place, and the resting player's facedown stash flips faceup.
  `RestCleanupPlan` retains per-card suit/token data so `RestCompleted` replay
  derives the same plan before validating and evolving it; Supply refresh and
  the turn/pending/tracks procedure state remain direct updates. RestSuite
  fixtures now inject only still-unplaced cards from the world, relic, and
  edifice decks (filtering them out of those decks), matching the executor's
  CardIndex uniqueness invariant.
- standalone banner flows execute through operations: a completed banner
  Challenge drains the old People's Favor favor one unit at a time to the
  recorded least-favor banks or places the old Darkest Secret secrets on the
  recorded least-stocked sites (returning any remainder to the previous
  holder), then pays the challenger's strictly greater replacement onto the
  banner and transfers custody as a banner `Move`. An unclaimed banner is
  claimed from the shared bank (`Move(Banner, SharedBank → PlayArea)`), which
  the executor supports by counting an unheld banner at `SharedBank`; resources
  on the unheld banner remain tracked at `OnBanner`. `BannerResourcePlaced` is
  a single favor/secret `Move` from the holder's play area onto the banner.
  `BannerChallengeStarted` and `BannerRibbonChoiceMade` stay direct (Supply
  spend and pending-procedure state only).
- Campaign conquests and raids execute through operations. *(Superseded
  2026-09-19: Campaign now runs on the procedure walker and the seven events
  named below no longer exist. See `docs/architecture/bounded-campaign.md`. The
  text is kept as history.)* The committed
  attacker force stays in the attacker's play area through the battle and
  leaves only at terminal events (it dies under a `Kill`, or the placed
  allocation moves under a `Move`), so a winning attacker keeps its board count
  until resolution and `pending.force` carries the battle arithmetic:
  - `CampaignStarted` only spends Supply and records the pending procedure;
  - `CampaignPlanChosen` pays attacker costs onto the source card (`Move` of
    favor/secret to `OnCard`) and flips a revealed facedown adviser with a
    same-play-area `Move` carrying `resultingOrientation = FaceUp`, or a
    revealed facedown site denizen with a `Reveal`/`Flip` in place at its site;
    the defender branch is latent (no registered defender plan has a cost),
    with the planned favor-to-suit-bank / secret-flip path documented beside it;
  - a victorious `CampaignSacrificed` only records the pending battle result;
    a defeat kills the committed warbands the attacker losing-force policy
    removes plus the warbands already lost to skulls and sacrifice, and
    relocates the committed survivors it relocates (returned survivors never
    left the board, so they need no operation), while the policy's effects must
    dispose of every surviving warband exactly once;
  - `CampaignConquered` kills the attacker warbands lost to skulls and
    sacrifice, kills defender losing forces at their sites, relocates or
    replaces them, moves returned-to-board defenders from their warband bank
    back into the defender play area, and moves only the placed allocation out
    of the attacker play area (unplaced survivors remain);
  - `CampaignRaided` kills the attacker warbands lost to skulls and sacrifice,
    transfers each targeted held relic and banner to the attacker with `Take`,
    returns People's Favor favor to the recorded suit banks and burns Darkest
    Secret secrets with `Burn`, discards facedown advisers to the next regional
    discard, sets facedown relics aside (SetAsideRelics enforces empty tokens
    on entry, so a token-carrying facedown relic rejects the batch), burns
    half the defender's favor, and kills the defender's board loss; Conspiracy
    leaves the game after the batch validates (the executor conserves card
    inventory), mirroring the Visions slice;
  - `CampaignRaidPawnRelocated` moves the defender pawn through operations.
  Losing-force site effects are validated as direct `SiteForces` arithmetic
  (`losingSiteEffects` previews each effect against the current sites) and then
  applied as a `killAt`/`warbandMove` op mapping, so existing board outcomes
  are preserved. CampaignSuite fixtures now filter injected catalog cards out
  of their source decks to stay CardIndex-consistent under operation
  preflight. No event, codec, or projection shape changed.

### 9. Enforce the boundary (complete)

- Remove obsolete direct physical mutation helpers: the read-only audit found
  none — every direct-mutating helper in `actions/*` and `phases/*` has a
  caller and is an operation-builder, a pure validator, or a sanctioned
  `GameStateUpdates` use. The one named material-mutation helper
  (`LeagueTreatyPower.moveFavor`) was migrated to core operations rather than
  deleted, so no dead helper remained to remove.
- `LeagueTreatyPower.moveFavor` now executes every validated favor allocation
  as a counted favor `CoreMove` from the source card's `OnCard` tokens to the
  chosen `FavorBank`, applied atomically through `OperationTransaction` with
  `OperationPolicy.exact`; clearing the pending decision is the direct update.
  Resolution is byte-identical to the previous direct `.copy()` rebuild
  (`RestSuite` League Treaty flows are unchanged).
- Add a narrow architecture check preventing migrated action modules from
  directly editing owned material fields. `BackendArchitectureSuite` now scans
  `actions/*`, `phases/*`, `StateBasedEvaluation.scala`, and `powers/**` for a
  curated list of owned-material write markers (adviser-vector removals, hand
  key writes, token/card copy shapes, bank-favor updates, site-map rebuilds,
  empty-key drops) and fails unless the nearest preceding comment line is the
  strict `// executor bypass:` sentinel. Setup builds state directly and is
  exempt.
- Keep direct updates legal for procedure, supply, track, title, and victory
  state: `pending`, `PlayerBoardState.supply`, `turn`, `tracks`, `title`, and
  `result` may still be written directly (usually through
  `GameStateUpdates.updateCurrent`), and the sentinel scan does not flag them.
- `temporaryHands` is now always keyed by every player in both the ready game
  and setup `InProgress` state; an empty vector means no cards await a private
  choice, and nothing removes a hand key. `buildReady` seeds every player's
  empty key, the executor keeps drained hands as empty vectors instead of
  dropping the key, `SearchRules.complete` no longer clears the actor's hand,
  and setup `StartingAdviserChosen` empties rather than removes the key (with
  `FirstGameCompleted` checking that every value is empty). Enforcement is by
  construction and targeted tests, not a new `DomainProblem`.
- Document any intentional executor bypass beside the owning rule and cover it
  with a regression test. A played Conspiracy leaves the game only after the
  operation batch validates (the executor conserves card inventory), so the two
  post-batch removals carry the strict sentinel comment and a regression test:
  `Visions.applyCompletion` boxes a Search-kept Conspiracy from the actor's
  temporary hand (a direct play is boxed from advisers), and `Campaign`'s Raid
  boxes the defender's Conspiracy from advisers. Search-kept Conspiracy stays
  in the actor's hand during the pending-target window and is removed at
  `ConspiracyCompleted`. `CardPlay`'s validation-local adviser computation was
  reshaped to avoid write-shaped copy text.
- A dedicated plan document records the audit, decisions, and this completed
  work: `docs/architecture/phase-9-enforce-boundary.md`.

## Verification gates

Every migrated slice requires:

- operation planner tests from authoritative event to semantic operations;
- policy tests for locks, Bury exemption, and trusted rule exemptions;
- executor tests for missing sources, insufficient quantities, incompatible
  destinations, stack order, secret orientation, and failed-batch rollback;
- exact old/new state differential tests;
- command-versus-replay parity;
- event codec and persisted-stream replay tests;
- `CardIndex` uniqueness and complete bounded-inventory checks;
- public and player-scoped hidden-information tests;
- focused gameplay suite, full JVM suite, architecture check, Markdown link
  check, and `git diff --check`.

Use generated operation sequences for bounded-inventory conservation and
atomicity properties, but keep rule-specific examples for glossary semantics
and ordering. A slice is complete only when its old physical mutation path is
removed and no unrelated event has changed behavior.

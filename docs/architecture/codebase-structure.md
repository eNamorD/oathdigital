# Codebase structure

Oath Digital is a modular monolith. Packages are ownership boundaries inside
one JVM/Scala.js repository, not deployment units.

## Ownership and dependency direction

```text
frontend -> shared protocol
server -> application + shared protocol
persistence/serialization -> application-owned ports + model
application -> gameplay + catalog + model + shared projection DTOs
gameplay -> catalog + model + engine
catalog -> model
model/engine -> no outer adapter packages
```

- `oathdigital.model` owns immutable domain values and structural invariants:
  identities, cards, world and campaign state, first-game setup data,
  `ReadyGame`/`OathState`, the `OathEvent` and `OathViolation` vocabularies,
  the operation algebra (`CoreOperation`, `Location`, `Piece`), power windows,
  and rule-source identities. It holds data only, never rules.
- `oathdigital.catalog` owns the typed card classes (`Cards.scala`) and the
  not-yet-implemented card objects in `holding/`; the full list and the
  production `ExecutableCatalog` are in `gameplay/cards/NewFoundations.scala`.
- `oathdigital.engine` owns generic event evolution and replay machinery.
- `oathdigital.gameplay` owns legality, procedures, and deterministic
  evolution over the model's events and operations. `walker` runs every
  procedure's `Operation` tree. `actions`, `phases` and `setup` hold the
  procedures, and `powers` holds the card powers that contribute to them.
- `oathdigital.application` owns orchestration and ports. It loads/replays,
  obtains server-owned random outcomes, appends events, authorizes scopes, maps
  intents, and builds redacted projections.
- `oathdigital.persistence`, `serialization`, and `server` are outward
  adapters. Inner packages never import them.
- `shared/oathdigital.protocol` owns actorless transport intents, bootstrap
  requests, projection DTOs, and their codecs. It compiles on JVM and Scala.js.
- `frontend/oathdigital.frontend` renders server projections and submits
  actorless intents. It never imports gameplay, application, or server rules.

## Grouping and size

Group code by one reason to change. Keep a procedure and its legality/evolution
together. Do not create one file per case class or event. Split orchestration,
codecs, projection, or rendering when distinct responsibilities emerge.

Most cards with an implemented power have their own file under
`gameplay/powers/`, grouped by timing (`action`, `rest`, `wake`, `economy` and
so on), with the typed card object and the power object side by side. Cards
with no implemented power share the `catalog/holding/` files by kind. A power
owns only applicability and power-specific mechanics: the contributions it
makes at power windows, or a phase power's `usable` check and `build` tree.
The walker owns procedure lifecycle, window folding and recording.

Production Scala files must remain at or below 800 lines. Around 500 meaningful
lines is a cohesion review point, not an automatic target. The deterministic
architecture check enforces the hard limit and dependency boundaries:

```sh
python3 scripts/check-architecture.py
```

## Sources of truth

- Domain events are durable game history; commands are transient.
- Gameplay APIs are the only source of legality. Projection and frontend code
  consume legal candidates produced by gameplay and do not recreate rules.
- Recorded random facts are prepared by application-owned ports and validated
  during replay; clients never provide dice, draws, or hidden order.
- `RuleSourceIndex` enumerates active factual sources.
  `CatalogHandlerInventory` lists the complete catalog handler
  vocabulary. Explicit Scala registries decide which handler IDs execute.
  Catalog `rulesText` is never interpreted.
- Player/public projection scope and redaction are decided in the application
  layer. Raw event history is a privileged loopback development view.

## Shared protocol and adapter ports

Shared protocol types are wire DTOs, not domain authority. The server derives
the actor from a development selector, an authenticated membership or a
trusted-alpha seat cookie, maps the actorless intent to
`application.GameCommand`, and invokes gameplay. Projection
DTOs are assembled by application collaborators and decoded by the frontend.

Application-owned interfaces include `EventStreamRepository`,
`IdentityRepository`, `GameEventCodec`, and random-outcome ports. HSQLDB,
JSON event codecs, HTTP routes, and other technologies implement those ports
outwardly. Adding an adapter must not move technology types into application or
gameplay.

## Adding behavior without duplication

### Action or phase

1. Add a case object to `ActionRef` in `model/ProcedureRef.scala` and list it
   in `ActionRef.all`. A phase change is a `PhaseTransitionRef` instead, and a
   procedure the engine starts on its own is a `TriggeredProcedureRef`.
2. Write the procedure object in its `actions` or `phases` package, as
   `ForgeProcedure` does. Its `build` and `rebuild` take the catalog, the
   state and the actor, and return the `Operation` tree (`Sequence`,
   `Decide`, `BuildOps`, `Branch`, `Repeat` and the other model nodes).
   `build` runs the start gates. `rebuild` reconstructs a started tree from
   state without them. Read targets from state, not from the tree's closure.
   Put a `PowerWindow` on each node where powers may contribute.
3. Register both functions in `WalkerProcedureRegistry.entries`. The `Entry`
   also names the `ActionKind` whose ignored rules the start records, the
   modifier window, and the optional roll, start-fact, roll-feedback and
   pricing hooks.
4. Offer it. An Act action joins `ActOffers.all` with its label, and
   `ActOffers.reason` words why it cannot start. `PhaseMenu` builds the menu
   from these offers and prices each option.
5. Add no command or event. The generic `ResolveWalker` answers every parked
   decision, an automatic `Roll` draws its faces from the walker's dice
   source as the walk runs, and each step is journaled as a
   `WalkerStepRecorded`.
6. Write the Game Log lines as notes. Declare the keys in a `NoteSet` named
   by `ProcedureNotes.id`, expose them as `noteKeys`, and list them in
   `ProcedureNotes`. Add the procedure's entry to `ProcedureLogs` in
   `application/gamelog/ProcedureLog.scala`: its tag,
   `ActionLineSource.Notes()`, and the decisions its lines already tell.
7. Add build, answer, replay, Game Log and projection tests as relevant. The
   projection needs no new code: `WalkerDecisionProjector` shows the parked
   decision.

### Power

1. Verify the catalog handler ID and its `RuleSourceIndex` source.
2. Move the card object out of `catalog/holding/` into the power's file. Keep
   its `PrintedPower`, and keep the card listed in
   `gameplay/cards/NewFoundations.scala`.
3. Write the power. A `ContributingPower` declares `contributions` by
   `PowerWindow`: `Transform`, `Restriction`, `OptionRestriction`, `Offer` or
   `OfferReroll`. A `PhasePower` is a WAKE, ACTION or REST power used as an
   action.
4. Register it in `WalkerPowerCatalog` or `PhasePowerCatalog`, or both.
   Registration marks the handler implemented (`PowerImplementationStatus`).
5. Declare its Game Log keys as `noteKeys`. `DeclaredNotes` refuses a note
   no power declared.
6. Never infer mechanics from labels or rules text. An applicable handler
   with no registered power is ignored and recorded through
   `IgnoredRulesRecorded`, not blocking.
7. Test activation, ordering, replay, and inactive/facedown behavior.

### Projection

Add shared DTO/codec fields only when the wire contract truly changes. Assemble
world/board/card presentation in `GamePresentationProjector`, and the walker
decision, its waiting notice and the phase in `PendingProjector`. What a player
may do is always a parked walker decision, the phase's menu included. Keep one
scope/redaction decision in `GameProjector`. Update JVM and Scala.js round-trip tests together.

### Event

Add the domain event in `model` (`GameEventProtocol.scala`), evolution in its owning module, and
one explicit discriminator/payload case in the appropriate split event codec
(`LifecycleEventCodec`, `EndingEventCodec`, or
another split codec). `GameEventWire` owns the single current envelope.
Update replay and malformed-wire tests; do not serialize Scala class names.

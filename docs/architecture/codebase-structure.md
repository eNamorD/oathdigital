> **PARTIALLY SUPERSEDED — module layout will change under the procedure-walker
> redesign** (`docs/superpowers/specs/2026-09-05-procedure-walker-design.md`): per-action
> procedures and power seams collapse into Operation trees + a walker. Ownership
> and dependency-direction principles remain valid.

# Codebase structure

Oath Digital is a modular monolith. Packages are ownership boundaries inside
one JVM/Scala.js repository, not deployment units.

## Ownership and dependency direction

```text
frontend -> shared protocol
server -> application + shared protocol
persistence/serialization -> application-owned ports + gameplay/model
application -> gameplay + catalog + model + shared projection DTOs
gameplay -> catalog + model + engine
catalog -> model
model/engine -> no outer adapter packages
```

- `oathdigital.model` owns immutable domain values and structural invariants.
- `oathdigital.catalog` owns typed, source-verified component definitions.
- `oathdigital.engine` owns generic event evolution and replay machinery.
- `oathdigital.gameplay` owns legality, procedures, events, and deterministic
  evolution. `actions`, `phases`, `setup`, and `gameplay/model` group
  cohesive behavior.
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
together; group small power handlers by action or timing window. Do not create
one file per case class, card, or event. Split orchestration, codecs, projection,
or rendering when distinct responsibilities emerge.

Individual powers declare focused inspectors and callbacks through shared
handler factories. Do not add per-power `PowerHandler` subclasses. Generic
factories own routing metadata and mismatch plumbing; action/phase modules own
procedure lifecycle, while power definitions own only applicability and
power-specific mechanics.

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
  `CatalogHandlerInventory` fingerprints the complete catalog handler
  vocabulary. Explicit Scala registries decide which handler IDs execute.
  Catalog `rulesText` is never interpreted.
- Player/public projection scope and redaction are decided in the application
  layer. Raw event history is a privileged loopback development view.

## Shared protocol and adapter ports

Shared protocol types are wire DTOs, not domain authority. The server derives
the actor from a development selector or authenticated membership, maps the
actorless intent to `application.GameCommand`, and invokes gameplay. Projection
DTOs are assembled by application collaborators and decoded by the frontend.

Application-owned interfaces include `EventStreamRepository`,
`IdentityRepository`, `GameEventCodec`, and random-outcome ports. HSQLDB,
JSON event codecs, HTTP routes, and other technologies implement those ports
outwardly. Adding an adapter must not move technology types into application or
gameplay.

## Adding behavior without duplication

### Action or phase

1. Add the domain command/event/pending state in the gameplay-owned vocabulary.
2. Implement legality and evolution in the cohesive `actions` or `phases`
   module and route it through `OathRules`.
3. If randomness is required, add or reuse an application-owned port and record
   the prepared result in an event.
4. Map the actorless shared intent in application code.
5. Project legal controls by calling gameplay APIs; render only those controls.
6. Add command, replay, redaction, codec, route, and Scala.js tests as relevant.

### Power

1. Verify the catalog handler ID and source.
2. Ensure `RuleSourceIndex` exposes the factual source and inventory coverage
   includes the handler.
3. Register explicit typed behavior in the owning action/timing registry.
4. Reject relevant unsupported handlers; never infer from labels or rules text.
5. Test activation, ordering, replay, and inactive/facedown behavior.

### Projection

Add shared DTO/codec fields only when the wire contract truly changes. Assemble
world/board/card presentation in `GamePresentationProjector`, legal targets in
`LegalActionProjector`, and pending procedures in
`PendingProcedureProjector`. Keep one scope/redaction decision in
`GameProjector`. Update JVM and Scala.js round-trip tests together.

### Event

Add the domain event in `gameplay/model`, evolution in its owning module, and
one explicit discriminator/payload case in the appropriate split event codec
(`LifecycleEventCodec`, `ActionEventCodec`, `CampaignEventCodec`, or
`EndingEventCodec`). `GameEventWire` owns the single current envelope.
Update replay and malformed-wire tests; do not serialize Scala class names.

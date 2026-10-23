# Gameplay modules

Gameplay is the deterministic inner layer. It owns legality, procedures,
power resolution, domain events and their evolution. It knows nothing of
HTTP, JSON, databases, projections or rendering.

Every action, phase step and triggered procedure is an `Operation` tree
(`model/Operation.scala`) that one generic engine, the procedure walker,
runs. Modules declare trees; they do not run them.

## Layout

```text
oathdigital/gameplay/
  OathRules.scala          aggregate: evolve, begin game, route commands
  OathRulesWalker.scala    walker commands: start, resolve, roll, consent
  OathDriver.scala         what runs between walks: queue, action boundary,
                           turn start, phase menus
  OathLifecycle.scala      GameplayTransition, the event-fold helper
  StateBasedEvaluation.scala  bandit refill, Wake checks, round end, victory
  RuleSourceIndex.scala, PowerAccess.scala, PowerRuntime.scala
                           power sources, access and ignored-rule diagnostics
  ...                      small shared rules (suits, resources, rulers)
  walker/                  the engine: ProcedureWalker, WalkerPowerGather,
                           WalkerReplay, WalkerSimulation,
                           WalkerProcedureRegistry, and walk helpers
  operations/              core operation execution: OperationPipeline,
                           OperationExecutor, OperationRestrictions,
                           printed rules
  powerresolver/           the power vocabulary: ContributingPower,
                           PhasePower, contributions, ContributionCollector,
                           OperationRewrite
  powers/                  every power, by family (search/, campaign/,
                           cardplay/, rest/, travel/, ...) and the catalogs
  actions/                 action trees by family: search/, travel/,
                           campaign/, economy/ (Muster, Trade), forge/,
                           recover/, challenge/, negotiation/, minor/,
                           cardplay/, dice/; shared rules beside them
                           (SearchRules, TravelRules, CardPlay, ...)
  phases/                  PhaseMenu, ActOffers, PhasePowerProcedure,
                           RestCleanup; wake/ and rest/ procedures
  setup/                   game start and the Setup procedure
  oathkeeper/              the Oathkeeper title change procedure
  consent/                 consent event evolution
  cards/                   NewFoundations, the production catalog
```

Group code by one reason to change. Keep an action's tree, its decisions and
its Game Log notes together. Put each power in the family package of the
action or timing it hooks; a power may have its own file there, and small
related powers share one.

## The walker

`WalkerProcedureRegistry` maps each `ProcedureRef` to its `build` and
`rebuild` functions, its modifier window, its ignored-rule kind, and the
optional roll, start-fact, roll-feedback and pricing hooks.
`ProcedureWalker` walks a tree from the parked position. It folds each
node's power window, runs leaf operations through `OperationPipeline`, and
parks at a `Decide` or a `Roll`. Each executed leaf becomes a
`WalkerStepRecorded` event; a park becomes `WalkerParked`. The tree is
rebuilt on every command, and the parked position is a path of child
indexes, so a tree must have the same shape each time it is built from the
same state and answers.

`OathDriver.next` runs between walks. It starts the queue's head when the
queue is not empty. Otherwise it runs, in order, the action boundary
(ignored-rule diagnostics, bandit refill, the Oathkeeper check), the turn's
start and the phase menu. See
[follow-up actions](follow-up-actions.md) for the queue.

Replay never walks a tree. `WalkerReplay` applies the recorded operations
and payloads only. `WalkerSimulation` runs a tree against a copy of the
state and discards it, so a projection or preview sees exactly what the
command would accept.

## Powers

Powers contribute at power windows. A power never imports the walker, and
the walker never names a power. Unimplemented powers are ignored and
recorded with an `IgnoredRulesRecorded` event; they never fail or block a
command. See
[power resolution](rule-resolution.md).

## One legality path

Commands, replay validation and projected choices use the same gameplay
APIs. What a player may do is always a parked walker decision, the phase
menu included. Application projectors (`GameProjector`,
`GamePresentationProjector`, `PendingProjector`, `WalkerDecisionProjector`)
label and redact that decision; they never rebuild legality. The Scala.js
client renders only projected controls.

Application-owned ports prepare random draws and dice; events record the
results, and replay checks them without drawing again. There is no stored
fact naming who a park waits on: `ProcedureWalker.awaitedPlayer` recomputes
the owner from live state.

## Boundaries

Dependencies point inward:

```text
server/frontend -> application/shared protocol -> gameplay
gameplay -> catalog/model/engine
persistence/serialization -> application-owned ports
```

These rules are enforced by `scripts/check-architecture.py` and
`BackendArchitectureSuite`:

- Gameplay imports no `application`, `persistence`, `presentation`,
  `protocol`, `serialization` or `server` package.
- Gameplay never reads catalog `rulesText` or printed power `.text`.
- `gameplay/walker` and `gameplay/operations` never name a
  `ContributingPower` or `PhasePower`.
- A file that declares a `ContributingPower` or `PhasePower` imports nothing
  from `gameplay.walker`.
- An individual `Power` uses a handler factory, not a `PowerHandler`
  subclass. A reviewed power inventory uses named objects, not raw `Set` or
  `Map` id tables.
- `actions`, `phases`, `powers` and `StateBasedEvaluation` change owned
  material (cards, site tokens, banks, hands) only through core operations.
  A direct write needs the `// executor bypass:` comment directly above it.
- No production Scala file exceeds 800 lines.

See [codebase structure](codebase-structure.md) for the packages outside
gameplay and [authoritative events](authoritative-events.md) for the event
vocabulary.

## Guardrails

- Keep aggregate routing small. Detail belongs in the owning module.
- Do not duplicate legality in projections or renderers.
- Do not interpret catalog prose or power-id fragments.
- Keep core operations typed and semantic, such as paying a resource or
  playing a card. Procedure modules own how they compose them.
- Introduce an abstraction only after several implemented rules need it.
- Preserve replay, deterministic ordering and hidden-information boundaries.

# Gameplay module architecture

This document defines the intended structure of the gameplay engine as Oath
Digital grows beyond its initial bounded slices. It is a living architecture
guide: the roadmap controls when migrations happen, while this document records
the boundaries and principles those migrations should preserve.

Wake, Travel, and Search originally accumulated in one transitional bucket while
those vertical slices established authoritative events, replay, server authority,
hidden decisions, and typed rule resolution. They now use the module boundaries
described below.

## Target structure

```text
oathdigital/gameplay/
  OathRules.scala
  RuleResolution.scala

  phases/
    Wake.scala
    Rest.scala

  actions/
    Travel.scala
    Search.scala
    Economy.scala
    Campaign.scala
    Recover.scala
```

Files should be added only when they own working behavior. The refactor must not
create empty Rest, Economy, Campaign, or Recover placeholders.

### Aggregate rules

`OathRules` in `gameplay/OathRules.scala` is the deterministic aggregate boundary. It validates the common
game lifecycle, routes commands and events to the appropriate phase or action,
and returns the next state, authoritative events, and continuation. It should
be deliberately boring: detailed costs, choices, card access, and action effects
belong to their action or phase module.

### Phase modules

Wake and Rest bookend every turn and therefore remain distinct from Act actions.
Each phase module owns its commands, base legality, phase-specific powers, event
evolution helpers, and continuation rules.

`Wake.scala` owns Take Wealth, Wake powers, Wake victory checks, and entry into
Act. `Rest.scala` will own Rest powers, resource return, secret reveal, Supply
refresh, per-turn cleanup, player/round advancement, and entry into the next
Wake.

### Action modules

An action module keeps its command vocabulary, base legality, cost calculation,
pending decision flow, and event evolution together. Travel and Search are
separate because their state transitions and choices are materially different.

Muster and Trade begin together in `Economy.scala`. Both spend Supply, choose an
accessible denizen, inspect suit and adviser context, and resolve an economic
yield. They should split only when their implementations acquire independent
decision flows or become difficult to navigate. File size is evidence, not a
rule; roughly 350-450 meaningful lines should prompt a cohesion review rather
than an automatic split.

Campaign and Recover should receive separate modules only when their implemented
procedures justify those boundaries.

### Shared rule resolution

`RuleResolution.scala` contains the typed runtime vocabulary described in
[rule-resolution.md](rule-resolution.md): stable sources, explicit handler
registration, action queries, deterministic outcomes, and decision boundaries.
It must not become an interpreted rules-text engine or a general JSON DSL.

Small power handlers should be grouped by the action or timing they modify, for
example Travel, Search, economy, Campaign, or Wake/victory handlers. Do not
create one source file per card. A card receives its own module only when its
procedure is independently complex.

## Dependency direction

Dependencies point inward toward deterministic domain behavior:

```text
HTTP / Scala.js UI
        |
application services and player-scoped projections
        |
OathRules -> phase/action modules -> typed rule resolution
        |
domain model, catalog definitions, and authoritative events
        |
generic replay and event-journal contracts
```

- Server routes derive the actor and translate transport intents; they do not
  implement rules.
- Application services load streams, replay state, invoke rules, and append
  accepted events with optimistic concurrency.
- Projections redact hidden information and present legal choices; they do not
  maintain a second rules implementation.
- The Scala.js client renders projected state and sends selected intents; it
  never supplies authoritative randomness or hidden deck state.
- Persistence stores versioned event bytes and has no knowledge of gameplay
  legality.

## One authoritative legality path

Commands, replay validation, and legal-choice projection must call the same
typed base rules and modifier resolution. A projection may transform a legal
result for display, but it must not recreate legality using parallel Boolean
conditions. This prevents the UI from offering commands the aggregate rejects
and prevents replay from accepting outcomes live commands could not produce.

Multi-step actions use an explicit `PendingProcedure`. Server-owned random
outcomes are recorded in authoritative events, then replay validates those
facts against the preceding state instead of drawing again. Player-scoped
projections expose pending private information only to its authorized actor.
The bounded Search design in [bounded-search.md](bounded-search.md) is the first
complete example of this pattern.

Events record durable game facts, not transport requests or derived view data.
Commands remain transient. Internal Scala names and file boundaries may change
freely. Before public release, event formats and fixtures may also change under
the policy in [authoritative-events.md](authoritative-events.md).

## Naming policy

“First game” remains a valid scenario and setup qualifier. Names such as
`FirstGameSetup`, its plan, and first-game fixtures may remain when they truly
describe the exile-only introductory setup.

Runtime names governing an ordinary game after setup use `Game*` or `Oath*`.
The R2 cleanup renamed the aggregate, application service, projection, mixed
event codec, HTTP adapters/routes, and Scala.js client vocabulary accordingly.

The mixed setup/gameplay aggregate vocabulary is `OathState`, `ReadyGame`,
`OathEvent`, `OathContinue`, `OathTransition`, and `OathViolation`. Genuine
introductory-scenario concepts retain `FirstGame`: the setup command, plan,
participants, rules, factory, fixtures, Foundation/support data, bootstrap
configuration, and the `FirstGameStarted`/`FirstGameCompleted` facts and their
setup discriminator constants.

The current mixed stream uses v1-v4 vocabulary, but those versions are not a
public compatibility promise. Before release, a refactor may update the codec
and checked-in fixtures directly when doing so simplifies the model.

## Evolution order

The structural extraction and runtime naming cleanup are complete. New work
should preserve those boundaries:

1. Separate common Act lifecycle validation from action-specific support checks.
2. Implement Rest and turn advancement before adding more Act actions.
3. Implement Muster and Trade as the first combined Economy slice.
4. Split Economy only if implemented decision flows create independent reasons
   to change.

## Guardrails

- Split modules by independent reasons to change, not by one type per file.
- Keep closely related command, legality, and evolution code together.
- Do not let the aggregate become an append-only action bucket.
- Do not duplicate legality in projections or clients.
- Do not infer executable behavior from catalog rules text.
- Do not silently ignore a relevant activated but unsupported power.
- Prefer table-driven registrations and tests for small modifiers.
- Add a generic abstraction only after multiple implemented rules demonstrate
  the shared behavior.
- Preserve authoritative replay and hidden-information boundaries through every
  refactor.

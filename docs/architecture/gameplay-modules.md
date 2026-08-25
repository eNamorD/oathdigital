# Gameplay modules

Gameplay is a deterministic inner layer. It owns commands, legality, pending
procedures, domain events, and event evolution; it does not know HTTP, JSON,
databases, projections, or DOM rendering.

## Current layout

```text
oathdigital/gameplay/
  OathRules.scala
  OathLifecycle.scala
  StateBasedEvaluation.scala
  RuleSourceIndex.scala
  RuleResolution.scala
  model/
    GameStateProtocol.scala
    GameProcedureProtocol.scala
    GameEventProtocol.scala
    GameViolation.scala
  setup/
    FirstGameSetup.scala
  phases/
    Wake.scala
    Rest.scala
  actions/
    Travel.scala
    Search.scala
    Economy.scala
    Recover.scala
    Forge.scala
    Challenge.scala
    Campaign*.scala
    MinorActions.scala
    Negotiation.scala
    Visions.scala
```

`OathRules` is the small aggregate router. Common lifecycle checks belong in
`OathLifecycle`; action and phase detail remains in the owning module.
`StateBasedEvaluation` is the shared path for title, victory, and round-end
facts.

Wake and Rest bookend turns and remain phase modules. Act actions own their base
legality, costs, decisions, and evolution. Economy keeps Muster and Trade
together because they share target and yield mechanics. Campaign is split by
cohesion: orchestration, legality/source classification, plan registration, and
resolution. Small handlers are grouped by action or timing, never one file per
card.

## Rule sources and handlers

`RuleSourceIndex` enumerates factual sources active in an authoritative
`ReadyGame`: site cards and faces, advisers, relics, banners, Foundations,
legacies, and other typed sources. It does not decide mechanics.

`CatalogHandlerInventory` collects and fingerprints the complete handler
vocabulary across catalog families. Bounded modules use that fingerprint to
detect unaudited catalog changes before execution. Explicit registries and
exact-ID classifications then map relevant handlers to typed Scala behavior.
Unknown relevant handlers fail with stable source/handler identity. Gameplay
never reads `rulesText`.

`RuleResolution` supplies shared source, activation, ordering, query, and
outcome vocabulary. Specialized registries such as Campaign plans remain in
their owning module when their windows/effects are action-specific.

## One legality path

Live command handling, replay validation, and legal-choice projection call the
same gameplay rule APIs. Projection may label and redact a legal result, but it
must not reconstruct legality. The frontend renders only projected controls and
candidates.

Application projection is split by responsibility: `GameProjector` chooses one
player/public scope and assembles the DTO; `GamePresentationProjector` owns
world, site, card, and player-board presentation; `LegalActionProjector` maps
gameplay legality and targets; and `PendingProcedureProjector` maps forced
decisions. Scala.js composes `ActionDecisionRenderer`, `WorldBoardRenderer`,
and `DevelopmentRenderer` through the small `ServerModeUi` controller. These
outer groups consume shared actorless intent and projection DTOs; none owns
rules.

Multi-step actions use typed `PendingProcedure` state. Application-owned ports
prepare random draws, dice, or fallback winners; events record those facts.
Replay revalidates them against prior state without drawing again. Private
pending data is exposed only by player-scoped application projections.

## Boundaries

Dependencies point inward:

```text
server/frontend -> application/shared protocol -> gameplay -> catalog/model/engine
persistence/serialization -> application-owned ports
```

The domain vocabulary is `OathState`, `ReadyGame`, `OathEvent`,
`OathContinue`, `GameplayTransition`, and `OathViolation`. `FirstGame`
names remain only for the introductory setup scenario and its fixtures.

Events record accepted game facts, not transport requests or view data.
Commands are transient. See [authoritative events](authoritative-events.md) and
[codebase structure](codebase-structure.md).

## Guardrails

- Keep aggregate routing small and detailed behavior cohesive.
- Do not duplicate legality in application projections or frontend renderers.
- Do not interpret catalog prose or handler-name fragments.
- Do not silently ignore a relevant unsupported power.
- Prefer table-driven explicit registrations for small modifiers.
- Introduce abstractions only after multiple implemented rules prove the seam.
- Preserve replay, deterministic ordering, and hidden-information boundaries.
- Keep every production Scala file at or below 800 lines.

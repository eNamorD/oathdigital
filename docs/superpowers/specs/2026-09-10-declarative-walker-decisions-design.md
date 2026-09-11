# Declarative Walker Decisions Design

## Goal

Make the transformed `Decide` operation the single authoritative source for
the answers a player may submit and the choices the UI presents. A power that
transforms a `Decide` must therefore change command legality and projection in
the same way, without action-specific candidate discovery in application
projectors.

This change migrates both Recover and Forge. It also replaces `OwnerQuery`
with the concrete `PlayerId` that the current walker protocol actually
supports.

## Current problem

`Decide` currently carries a marker payload, an `OwnerQuery`, and an optional
validation closure. Recover and Forge then duplicate parts of those closures
in projection code to obtain UI candidates. Power transforms operate on the
tree, but those separate projector expressions are outside the tree and can
drift from the transformed decision.

`OwnerQuery` also advertises dynamic or off-turn ownership that is not real:
`PendingTree`, command authorization, and private projection all identify the
walker's actor as the resolver.

## Decision model

Replace the marker and validation closure with concrete legal options:

```scala
final case class Decide(
    decisionId: String,
    owner: PlayerId,
    options: Vector[DecisionOption],
    window: Option[PowerWindow] = None
)

final case class DecisionOption(
    payload: DecisionPayload,
    targets: Vector[DecisionTarget]
)
```

`payload` is the complete answer recorded by `WalkerStepRecorded` when the
option is selected. `targets` describes the game objects involved in that
answer so a generic projector can present or highlight them. A command-like
choice uses a button target.

`DecisionTarget` is model-safe, presentation-neutral data with stable
identities:

```scala
sealed trait DecisionTarget
object DecisionTarget {
  final case class Button(key: String) extends DecisionTarget
  final case class Player(id: PlayerId) extends DecisionTarget
  final case class Site(id: SiteId) extends DecisionTarget
  final case class Denizen(site: SiteId, id: DenizenId) extends DecisionTarget
  final case class Relic(location: Location, id: RelicId) extends DecisionTarget
  final case class Deck(id: CardDeck) extends DecisionTarget
}
```

Only variants required by migrated production decisions need to be introduced
initially: `Button`, `Denizen`, and `Relic`. Further variants are added when a
real decision needs them.

An option is the complete legal answer, not one intermediate UI click. This
keeps the walker generic and makes legality a simple membership check.

## Resolution semantics

When resolving a parked `Decide`, `ProcedureWalker`:

1. Rebuilds and power-transforms the tree as it does today.
2. Confirms `decide.owner == pending.actor`.
3. Requires exactly one `decide.options` entry whose payload equals the
   submitted payload.
4. Records that payload unchanged.

An empty option list is invalid for a parked `Decide`; action trees must omit
the node when no answer is required. Duplicate payloads are invalid because
they make target metadata ambiguous. These structural checks return typed
`InvalidEventOrder` violations rather than throwing.

Because the tree is rebuilt against authoritative state for projection and
resolution, removed or altered options reject stale commands automatically.
No decision-specific validation closure remains.

## Projection and wire format

`WalkerDecisionProjector` projects the options found on the transformed parked
`Decide`; it does not branch on action or decision IDs to rediscover choices.
Each projected option contains:

- the existing wire representation of its complete `DecisionPayload`;
- ordered projected targets with stable keys, kinds, labels, and appropriate
  visible details.

The shared walker decision DTO replaces `relicCandidates` with generic
`options`. Roll-only fields and Recover roll feedback remain unchanged in this
change because they are not decision-option discovery.

Hidden information remains protected by the owner-private walker projection.
Target presentation uses the existing `GamePresentationProjector`; operation
and model layers carry no UI labels.

The frontend may retain action-specific renderers. Their inputs, however,
must come exclusively from projected decision options. Renderer code may
interpret a known payload shape to provide a richer interaction, but it may
not independently calculate legal candidates.

## Recover migration

The Continue/Stop decision contains two options:

- `RecoverChoicePayload(Continue)` targeting `Button("continue")`;
- `RecoverChoicePayload(Stop)` targeting `Button("stop")`.

The success decision contains one option per live facedown site relic:

- `RecoverRelicPayload(relicId)` targeting that site relic.

If no relic exists, the procedure omits the relic `Decide` and finishes as a
legal wasted action, preserving the current ruling.

Recover's `validateChoice` and `validateRelic` closures are deleted. The
projector's Recover-specific relic-candidate branch and shared
`relicCandidates` field are deleted. The frontend renders buttons and relics
from the generic options.

## Forge migration

Forge has exactly three eligible denizens and a printed cost totaling exactly
three resources. Its complete legal answer space is therefore small: at most
three distinct assignments for a mixed cost, and one when all resources have
the same type.

`ForgeProcedure` enumerates every complete legal
`ForgeAssignmentPayload` from the live eligible targets and printed resource
multiset. It filters assignments that require more favor from a suit bank than
is currently available. Each option targets its three denizens in canonical
target order.

This replaces `validateAssignment`. It does not introduce a generic
multi-selection language or payload factory.

The generic projector emits all complete legal Forge options. The existing
Forge assignment UI derives:

- the three denizen rows from the union of option targets;
- allowed resource assignments from `ForgeAssignmentPayload`s;
- confirmation eligibility from exact membership in the projected options.

Changing a row must remain possible only when at least one projected complete
option matches the resulting partial or complete assignment. The submitted
payload is one of the projected complete options verbatim.

`PendingProcedureProjector.forgeProjection` must stop calling
`ForgeProcedure.eligibleTargets` or `printedCost` for decision legality.
Forge-specific display state may be derived from the projected payloads and
targets, or the dedicated `ForgeProjection` may be removed if the generic
walker projection fully replaces it. Prefer deletion when it does not force
unrelated UI restructuring.

## Power transformations

Powers continue to transform `Operation` trees. A power affecting decision
choices transforms the `Decide` node's `options` before both walking and
projection. It may add, remove, or replace options, but resulting options must
have unique payloads and valid target metadata.

This design deliberately does not add a separate projection hook for powers.
The transformed executable decision is the projection source.

## Ownership

`Decide.owner` becomes `PlayerId`, and `OwnerQuery`/`WalkerCtx` are removed if
they have no remaining use. Recover and Forge construct decisions with their
actor directly.

This does not add off-turn walker decisions. Supporting those later requires
an explicit redesign of pending-state ownership, authorization, continuation,
and viewer scoping; reintroducing a query object alone is insufficient.

## Failure handling

- Unknown submitted payload: typed decision-option mismatch.
- Duplicate option payloads: typed malformed-tree rejection.
- Empty parked decision: typed malformed-tree rejection.
- Owner differing from walker actor: `WrongPlayer` or the existing equivalent.
- Target identity that cannot be presented: omit the entire malformed decision
  projection rather than exposing a partially described option. Procedure and
  power builders remain responsible for constructing targets that exist in
  their authoritative state.

## Testing

Add or update tests proving:

1. Generic `Decide` accepts exactly its option payloads.
2. Duplicate and empty options reject deterministically.
3. Concrete owner enforcement replaces `OwnerQuery` behavior.
4. A synthetic power that adds/removes an option changes projection and
   resolution identically.
5. Recover projects and accepts Continue, Stop, and live relic options solely
   from its transformed `Decide`.
6. Empty-site Recover finishes without parking.
7. Forge enumerates every valid complete assignment and excludes unaffordable
   suit-bank assignments.
8. Forge UI derives rows and confirmation from projected options and submits
   an option payload verbatim.
9. Stale Recover and Forge options reject after authoritative state changes.
10. Event codec and replay preserve selected payloads unchanged.
11. Backend, frontend runtime, Scala.js link, and architecture checks pass.

## Non-goals

- A universal form or workflow description language.
- Persisting decision options in game state or events.
- Moving presentation labels into gameplay/model code.
- Off-turn walker decision ownership.
- Generalizing Recover-specific roll feedback in this change.

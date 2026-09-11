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

Replace the marker and validation closure with a declarative query:

```scala
final case class Decide(
    decisionId: String,
    owner: PlayerId,
    query: DecisionQuery,
    window: Option[PowerWindow] = None
)

sealed trait DecisionQuery
```

The names reflect their roles:

- `DecisionQuery` is the complete question and selection contract carried by
  `Decide`;
- `DecisionOption` is a selectable button or game object;
- `DecisionAnswer` replaces `DecisionPayload` as the answer submitted and
  recorded after satisfying the query.

`DecisionQuery` initially has two forms:

```scala
object DecisionQuery {
  final case class ChooseOne(
      choices: Vector[DecisionChoice]
  ) extends DecisionQuery

  final case class Partition(
      sections: Vector[DecisionSection],
      arrangements: Vector[DecisionArrangement]
  ) extends DecisionQuery
}

final case class DecisionChoice(
    answer: DecisionAnswer,
    option: DecisionOption
)

final case class DecisionSection(
    key: String,
    label: String,
    required: Int
)

final case class DecisionArrangement(
    answer: DecisionAnswer,
    placements: Vector[DecisionPlacement]
)

final case class DecisionPlacement(
    option: DecisionOption,
    sectionKey: String
)
```

`ChooseOne` maps each selectable option directly to its complete answer.
`Partition` describes named sections, their exact required counts, and every
legal complete arrangement. The arrangement answer is recorded verbatim.
Intermediate dragging remains frontend-local state.

`DecisionOption` is model-safe, presentation-neutral data with stable
identities:

```scala
sealed trait DecisionOption
object DecisionOption {
  final case class Button(key: String, label: String) extends DecisionOption
  final case class Player(id: PlayerId) extends DecisionOption
  final case class Site(id: SiteId) extends DecisionOption
  final case class Denizen(id: DenizenId) extends DecisionOption
  final case class Relic(id: RelicId) extends DecisionOption
  final case class Vision(id: VisionId) extends DecisionOption
  final case class Deck(id: CardDeck) extends DecisionOption
}
```

Only variants required by migrated production decisions need behavior in this
change: `Button`, `Denizen`, and `Relic`; `Vision` is added and covered as a
supported projection target. Further variants are added when a real decision
needs them.

Denizen, Relic, and Vision IDs identify physical cards globally. Their option
does not carry a site, player, or other location. The projector locates the ID
in authoritative state to produce visible details, and the frontend highlights
the matching stable ID wherever it is rendered. Location is a legality fact
used while constructing a query, not part of card identity. Owner-private
projection and existing card-knowledge rules continue to govern disclosure.

An option is one selectable button or game object. A `DecisionChoice` or
`DecisionArrangement` associates the complete legal answer with those options,
keeping the walker generic and answer legality a membership check.

Rename the Scala model family and its concrete cases from `DecisionPayload` to
`DecisionAnswer`, including `Answered.answer` and corresponding command/wire
DTO type names. Persisted JSON field names and existing answer kind tags remain
unchanged, so recorded games require no migration.

## Resolution semantics

When resolving a parked `Decide`, `ProcedureWalker`:

1. Rebuilds and power-transforms the tree as it does today.
2. Confirms `decide.owner == pending.actor`.
3. Requires exactly one complete choice or arrangement whose answer equals the
   submitted answer.
4. Records that answer unchanged.

An empty query is invalid for a parked `Decide`; action trees must omit the
node when no answer is required. Duplicate answers are invalid because they
make option metadata ambiguous. Partition sections must have unique keys and
non-negative required counts; every arrangement must place every option once,
use only declared sections, and meet every required count. These checks return
typed `InvalidEventOrder` violations rather than throwing.

Because the tree is rebuilt against authoritative state for projection and
resolution, removed or altered options reject stale commands automatically.
No decision-specific validation closure remains.

## Projection and wire format

`WalkerDecisionProjector` projects the options found on the transformed parked
`Decide`; it does not branch on action or decision IDs to rediscover choices.
The projection mirrors the query shape. Each projected option contains:

- the existing wire representation of its complete `DecisionAnswer` where the
  query associates an answer with it;
- a stable kind and ID plus display details resolved from authoritative state.

A projected partition also contains ordered section keys, labels, required
counts, and its complete legal arrangements. The wire representation never
asks the frontend to reconstruct legality.

The shared walker decision DTO replaces `relicCandidates` with a generic
projected query. Roll-only fields and Recover roll feedback remain unchanged
in this change because they are not decision-option discovery.

Hidden information remains protected by the owner-private walker projection.
Card and board-object presentation uses the existing
`GamePresentationProjector`. Button labels and partition section labels are
declarative prompt copy carried by the query; game-object names and details do
not enter gameplay or model code.

The frontend may retain action-specific renderers. Their inputs, however,
must come exclusively from projected decision options. Renderer code may
interpret a known answer shape to provide a richer interaction, but it may
not independently calculate legal candidates.

## Recover migration

The Continue/Stop decision contains two options:

- `RecoverChoiceAnswer(Continue)` paired with
  `Button("continue", "Continue")`;
- `RecoverChoiceAnswer(Stop)` paired with `Button("stop", "Stop")`.

The success decision contains one option per live facedown site relic:

- `RecoverRelicAnswer(relicId)` paired with `Relic(relicId)`.

If no relic exists, the procedure omits the relic `Decide` and finishes as a
legal wasted action, preserving the current ruling.

Recover's `validateChoice` and `validateRelic` closures are deleted. The
projector's Recover-specific relic-candidate branch and shared
`relicCandidates` field are deleted. The frontend renders buttons and relics
from the generic options.

## Forge migration

Forge is a `DecisionQuery.Partition`. Its options are the three eligible
`Denizen` cards. Its sections are `"pay-favor"` and `"pay-secret"`, displayed
as “Pay Favor” and “Pay Secret”, with required counts taken from the printed
Forge cost. Every option must be placed in exactly one section.

`ForgeProcedure` enumerates every complete legal arrangement from the live
eligible targets and printed resource multiset. Each arrangement carries its
corresponding `ForgeAssignmentAnswer`, with assignments in canonical target
order. With three targets there are at most three arrangements for a mixed
cost and one when all resources have the same type.

Suit-bank availability does not filter the decision query. Whether or how suit
banks constrain the eventual resource placement is explicitly deferred to a
separate rules discussion after this specification is approved.

This replaces `validateAssignment`. It does not introduce a universal form
language or answer factory.

The generic projector emits the two sections, the denizen options, and all
complete arrangements. The frontend initializes the denizens between those
sections, allows drag/drop or accessible move controls, and enables confirmation
only when the placement matches a projected arrangement. It submits that
arrangement's answer verbatim.

Reuse the existing Keep/Discard interaction by extracting a generic two-section
partition state and renderer. `CardDecisionState` and Forge each adapt their
own projected data into it. Search/setup retain their existing semantics,
resolution stage, labels, and ordering behavior; Forge supplies “Pay Favor” and
“Pay Secret” labels, exact counts, and no ordering requirement. Do not route
Forge through `PendingCardDecisionProjection` or make walker queries depend on
Search/setup concepts.

Delete `PendingProcedureProjector.forgeProjection`, `ForgeProjection`, and
`ForgeAssignmentTargetProjection`; the generic walker partition projection
replaces them. Forge-specific frontend rendering derives its state from that
partition without independently calling gameplay rules.

## Power transformations

Powers continue to transform `Operation` trees. A power affecting decision
choices transforms the `Decide` node's `query` before both walking and
projection. It may add, remove, or replace choices, sections, arrangements, or
options, but the resulting query must satisfy its structural invariants.

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

- Unknown submitted answer: typed decision-option mismatch.
- Duplicate answers: typed malformed-tree rejection.
- Empty parked decision: typed malformed-tree rejection.
- Owner differing from walker actor: `WrongPlayer` or the existing equivalent.
- Target identity that cannot be presented: omit the entire malformed decision
  projection rather than exposing a partially described option. Procedure and
  power builders remain responsible for constructing targets that exist in
  their authoritative state.

## Testing

Add or update tests proving:

1. Generic `Decide` accepts exactly the answers declared by its query.
2. Duplicate answers and malformed/empty queries reject deterministically.
3. Concrete owner enforcement replaces `OwnerQuery` behavior.
4. A synthetic power that adds/removes an option changes projection and
   resolution identically.
5. Recover projects and accepts Continue, Stop, and live relic options solely
   from its transformed `Decide`.
6. Empty-site Recover finishes without parking.
7. Forge enumerates every printed-cost arrangement without consulting suit-bank
   availability.
8. Forge UI reuses the generic partition interaction, derives confirmation from
   projected arrangements, and submits an arrangement answer verbatim.
9. Stale Recover and Forge options reject after authoritative state changes.
10. Event codec and replay preserve selected answers unchanged.
11. Backend, frontend runtime, Scala.js link, and architecture checks pass.

## Non-goals

- A universal form or workflow description language.
- Persisting decision options in game state or events.
- Moving presentation labels into gameplay/model code.
- Off-turn walker decision ownership.
- Generalizing Recover-specific roll feedback in this change.

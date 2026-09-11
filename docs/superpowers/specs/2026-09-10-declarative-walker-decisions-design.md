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
      options: Vector[DecisionOption]
  ) extends DecisionQuery

  final case class Partition(
      sections: Vector[DecisionSection],
      options: Vector[DecisionOption]
  ) extends DecisionQuery
}

final case class DecisionSection(
    key: String,
    label: String,
    minRequired: Int
)

final case class DecisionPlacement(
    option: DecisionOptionRef,
    sectionKey: String
)
```

`ChooseOne` exposes independently selectable options. Its answer is generic:

```scala
final case class ChooseOneAnswer(
    selected: DecisionOptionRef
) extends DecisionAnswer
```

The selected reference must identify exactly one declared option. If a future
rule needs a predefined group to behave as one selectable thing, it requires
an explicit composite option rather than ambiguous multi-option semantics.

`Partition` describes named sections, their minimum required counts, and the
options to distribute. Its answer is generic:

```scala
final case class PartitionAnswer(
    placements: Vector[DecisionPlacement]
) extends DecisionAnswer
```

Each declared option reference must appear exactly once in the answer, in one
declared section. Each section must receive at least `minRequired` options. The
answer records placements in option order; intermediate dragging remains
frontend-local state.

`DecisionOption` is model-safe, presentation-neutral data with stable
identities. Its `ref` excludes display text and is what answers persist:

```scala
sealed trait DecisionOptionRef
object DecisionOptionRef {
  final case class Button(key: String) extends DecisionOptionRef
  final case class Player(id: PlayerId) extends DecisionOptionRef
  final case class Site(id: SiteId) extends DecisionOptionRef
  final case class Denizen(id: DenizenId) extends DecisionOptionRef
  final case class Relic(id: RelicId) extends DecisionOptionRef
  final case class Vision(id: VisionId) extends DecisionOptionRef
  final case class Deck(id: CardDeck) extends DecisionOptionRef
}

sealed trait DecisionOption {
  def ref: DecisionOptionRef
}
object DecisionOption {
  final case class Button(
      ref: DecisionOptionRef.Button,
      label: String
  ) extends DecisionOption
  final case class Player(ref: DecisionOptionRef.Player) extends DecisionOption
  final case class Site(ref: DecisionOptionRef.Site) extends DecisionOption
  final case class Denizen(ref: DecisionOptionRef.Denizen) extends DecisionOption
  final case class Relic(ref: DecisionOptionRef.Relic) extends DecisionOption
  final case class Vision(ref: DecisionOptionRef.Vision) extends DecisionOption
  final case class Deck(ref: DecisionOptionRef.Deck) extends DecisionOption
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

An option is one selectable button or game object. `ChooseOneAnswer` selects
one option reference. `PartitionAnswer` assigns each option reference to a
section. Both remain declarative and generically validatable.

Rename the Scala model family from `DecisionPayload` to `DecisionAnswer`,
including `Answered.answer` and corresponding command/wire DTO type names. New
events use generic choose-one and partition answer tags. Decoders continue to
accept the existing Recover and Forge tags and translate them to generic
answers, so recorded games require no data migration.

## Resolution semantics

When resolving a parked `Decide`, `ProcedureWalker`:

1. Rebuilds and power-transforms the tree as it does today.
2. Confirms `decide.owner == pending.actor`.
3. For `ChooseOne`, validates the submitted `ChooseOneAnswer` reference against
   its options. For `Partition`, validates the submitted `PartitionAnswer`
   against its options, sections, and minimum counts.
4. Records that answer unchanged.

An empty query is invalid for a parked `Decide`; action trees must omit the
node when no answer is required. `ChooseOne` option references must be unique.
Partition sections must have
unique keys and non-negative minimum counts; partition options must be unique;
an answer must place every option exactly once, use only declared sections,
and meet every minimum. These checks return typed `InvalidEventOrder`
violations rather than throwing.

Because the tree is rebuilt against authoritative state for projection and
resolution, removed or altered options reject stale commands automatically.
No decision-specific validation closure remains.

## Projection and wire format

`WalkerDecisionProjector` projects the options found on the transformed parked
`Decide`; it does not branch on action or decision IDs to rediscover choices.
The projection mirrors the query shape. Each projected option contains its
stable reference plus display details resolved from authoritative state.

A projected partition also contains ordered section keys, labels, minimum
counts, and options. The frontend produces a generic partition answer; it does
not reconstruct action-specific legality.

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

- `Button(ButtonRef("continue"), "Continue")`;
- `Button(ButtonRef("stop"), "Stop")`.

The success decision contains one option per live facedown site relic:

- `Relic(RelicRef(relicId))`.

If no relic exists, the procedure omits the relic `Decide` and finishes as a
legal wasted action, preserving the current ruling.

Recover's `validateChoice` and `validateRelic` closures are deleted. The
projector's Recover-specific relic-candidate branch and shared
`relicCandidates` field are deleted. The frontend renders buttons and relics
from the generic options.

## Forge migration

Forge is a `DecisionQuery.Partition`. Its options are the three eligible
`Denizen` cards. Its sections are `"pay-favor"` and `"pay-secret"`, displayed
as “Pay Favor” and “Pay Secret”, with `minRequired` counts taken from the
printed Forge cost. Every option must be placed in exactly one section. Since
the two minima sum to all three options, Forge's minimum constraints produce
the exact printed resource split.

`ForgeProcedure` declares the live eligible targets and printed resource
minima; it does not enumerate assignments. Its trailing operation translates
each `PartitionAnswer` placement into a `PayCost` from the actor onto the
corresponding denizen: `"pay-favor"` becomes `Cost(favor = 1)` and
`"pay-secret"` becomes `Cost(secret = 1)`. `OperationPipeline` therefore
validates the player's resources generically and applies all payments
atomically. Suit banks are not consulted by Forge.

This replaces `validateAssignment`. It does not introduce a universal form
language or answer factory.

The generic projector emits the two sections and denizen options. The frontend
initializes the denizens between those sections, allows drag/drop or accessible
move controls, and enables confirmation only when every option is assigned and
the projected minima are met. It submits a generic `PartitionAnswer`.

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
projection. It may add, remove, or replace choices, sections, or options, but
the resulting query must satisfy its structural invariants.

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
7. Forge declares its options and printed-cost minima without consulting suit
   banks or enumerating complete arrangements, and resolves placements as
   player-funded `PayCost` operations.
8. Forge UI reuses the generic partition interaction, derives confirmation from
   projected minima, and submits a generic partition answer.
9. Stale Recover and Forge options reject after authoritative state changes.
10. Event codec and replay preserve selected answers unchanged.
11. Backend, frontend runtime, Scala.js link, and architecture checks pass.

## Non-goals

- A universal form or workflow description language.
- Persisting decision options in game state or events.
- Moving presentation labels into gameplay/model code.
- Off-turn walker decision ownership.
- Generalizing Recover-specific roll feedback in this change.

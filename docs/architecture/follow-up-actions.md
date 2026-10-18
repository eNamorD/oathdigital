# Follow-up actions

A power can owe its player an action to take once the current one ends. The
owed action is a full action of its own: its own walker run, its own Game Log
section, modifiers chosen as for any start, and no Supply spent. Once the
current walk ends, the walker asks the player: take the action, or Skip. Knights Errant, Hunting
Party and Second Wind are built on it.

Design: [follow-up actions design](../superpowers/specs/2026-10-08-follow-up-actions-design.md),
amended by the [walker-driven game loop](../superpowers/specs/2026-10-09-walker-game-loop-design.md).
Plan: [follow-up actions plan](../superpowers/plans/2026-10-08-follow-up-actions.md).

## The queue and its replay

- `QueuedProcedure(actor, procedure, startArgs, source, kind)`
  (`model/GameState.scala`) is one queued entry. `kind` is `Run`, which the
  driver starts at once, or `Offer`, which asks its actor first. `source` is
  the power that queued it, which the Game Log credits. `actor` may differ
  from the active player; that actor then owns the walk (see
  [Off-turn actors](#off-turn-actors)).
- `CurrentGameState.queue` is the queue, oldest first. It outlives walks and
  phases. `walkerEntry` is the entry the current walk was started from. It
  is walker scratch, which `WalkerCompleted` clears.
- A power adds an entry with the primitive op `QueueProcedure(entry,
  atFront)` (`model/CoreOperations.scala`, applied by `TurnStateOperations`),
  inside the origin action's tree. It appends unless `atFront` is set.
- Two walker events touch the queue (`gameplay/walker/WalkerEvents.scala`):
  - `ProcedureDequeued` is the first event of a walk started from the queue.
    Replay moves the head into `walkerEntry`.
  - `FollowUpSkipped` names the offered entry and changes no state. It only
    anchors the Game Log line.

  `WalkerReplay` refuses a dequeue that does not name the head, or that
  happens while another walk is open. It is an event rather than a
  `WalkerParked` field, because a walk can finish in its starting command
  without parking.
- Wire spellings: the op kind is `queue-procedure`. The event types are
  `walker.procedure-dequeued` and `walker.follow-up-skipped`. The entry's
  codec is in `serialization/FollowUpWire.scala`, beside the power-source
  and procedure spellings it shares with the other walker events. The
  journal format version was not bumped. Readers accept only the exact
  version, so a bump would have stopped every stored game, not only the
  ones holding a nested Knights Errant Campaign.

## Start and skip

- When a walk ends, `OathDriver.next` dequeues the head. An `Offer` starts the
  triggered procedure `FollowUpOffer` (`gameplay/walker/FollowUpOffer.scala`),
  a walker decision for the entry's actor: the action "for no Supply", or
  Skip.
- The action's option is priced by `QueuedStart.refusal`: the restriction
  check, the first walk and the playable-option gate, on the state its start
  would see. When it would be refused, the option is unavailable with its
  reason, and Skip stays available.
- Taking it queues a `Run` entry in front (`atFront`), so Second Wind's
  Travel does not wait behind its own queued Campaign offer. The driver
  dequeues it and starts the action in the same command. A start that
  fails refuses the whole command.
- Skip is an answer. `walkerTransition` writes `FollowUpSkipped(entry)`
  before `WalkerCompleted`. There is no Skip command and no service gate:
  the parked offer already holds back every other command.
- No procedure checks the phase. Every start is a queued entry, a menu pick
  or a follow-up, and `QueuedStart` decides whether it starts. A Special
  Envoy Travel that enters Rest still lets the next queued Campaign start.
- The action boundary and the automatic End Wake and Finish Rest wait while
  anything is queued.

## The Supply waiver

- `FollowUpSupplyWaiver` (`gameplay/walker/FollowUpSupplyWaiver.scala`) is
  an automatic engine power, id `engine.follow-up-supply`.
- `WalkerPowers.selected` always offers it. Most of its callers have no
  state, so it gates itself in `applicable`: it applies only when
  `walkerEntry` has a source, at the cost window of the action it names, on
  the start and on every resume.
- It waives Supply only. Recover pays at `RecoverCost`, a window around the
  Supply each roll costs.
- The waiver strips through `gameplay/operations/SupplyStrip.scala`. Every
  unconditional Supply strip uses `SupplyStrip`: News from Afar, Awaited
  Return, and the free-Travel powers. `SupplyStrip` also filters the
  payments a `BuildOps` builds.
- The economy's by-suit waiver (Cup of Plenty, Animal Playmates, Birdsong)
  decides from the answered card at run time, so it stays separate as
  `SuitSupplyWaiver`.

## The offer's display

- There is no follow-up projection, control or panel. The offer is a parked
  choose-one, drawn as any other.
- Each action declares its own reason for an unavailable option
  (`FollowUpOffer.reason`):
  - A Campaign shows its restriction's own text, such as Vow of Peace's.
    With nothing to attack, it reads "No site to conquer and no enemy pawn
    to raid".
  - A Travel reads "No site to travel to".
  - Any other action reads "Cannot start now".
- The start asks for the action's modifiers inside the walk, as a normal
  start does (a queued Travel with Special Envoy, say).

## Game Log

- `ProcedureDequeued` opens the queued run's segment. The start tag appends
  the source, as in "Campaign · Knights Errant" (`StartLines.queuedBy`).
- The offer's own walk writes nothing: its decision is narrated by the tag
  or by the Skip line.
- `FollowUpSkipped` writes "{source}: {Action} skipped.", filed in the
  latest section (`GameLogFormatter.formatLive`).
- A step that only queues posts no "Used {card}" line.
- The waiver is named automatic, so it never appears as a chosen modifier.

## Option warnings

- A power can warn on an option without withholding it.
  `ContributingPower.warning` returns an `OptionReason`, and
  `OptionWarnings.marked` wraps the option in `DecisionOption.Warned`. The
  walker decision projector applies it to every choose-one it shows.
- The warning is projection-only: legality, the walk and the journal never
  read it.
- On the wire the option's state is `warning`. The frontend treats it as
  choosable (`DecisionOptionProjection.choosable`) and shows the reason on
  hover, on the board and in the panels.

## The cards

- **Knights Errant** (`powers/economy/KnightsErrant.scala`) queues a
  Campaign at `MusterActionEligibility`, unconditionally. A restriction such
  as Vow of Peace shows on the offer's Campaign option.
- **Hunting Party** (`powers/search/HuntingParty.scala`) queues a Campaign
  after a Search of the world deck. While it is selected, a discard pile
  warns "Hunting Party needs the world deck".
- **Second Wind** (`powers/campaign/SecondWind.scala`) is a battle plan for
  either side. Once its user wins the Campaign, its `later` step at the root
  queues a Travel and then a Campaign.
  - The two entries are independent: skipping the Travel still offers the
    Campaign.
  - A plan's cost may be paid onto a card that already holds tokens, so the
    taken Campaign offers it again. A chain ends only when its cost cannot
    be paid.

## Queueing another action

Only Campaign and Travel are queued today. A power that first queues
another action must also:

- give the entry its `startArgs` (a Trade's resource) where the power queues
  it;
- anchor the Muster and Trade log tag on their first step with ops. Under
  the waiver they pay nothing, so `StartLines.opening` would post the tag at
  completion.

## Off-turn actors

A queued entry's actor owns its walk (`CurrentGameState.walkActor`, catalog
batch 8, slice 14), so `ctx.actor` is that actor even on another
player's turn, and they pick their own modifiers. A queued start accepts
the entry's actor; a menu start still requires the turn's player.

- Sneak Attack queues its `Offer` at the front, naming the earlier attacker
  as the declared defender in its start arguments.
- A defender's Second Wind queues its Travel and Campaign at the back.
- A queued entry may name its `sender`, the player whose power sent its
  actor (catalog batch 8, slice 15). A waivable restriction the sender could
  waive does not block the walk. A power's queued Travel may carry its
  destination as its first start argument, and then asks no destination
  question (Palanquin). A later argument is for the sender's own rules: a
  waived toll's free option.

## Decisions beyond the design

- The waiver is always offered and gated by state, not appended only when
  an entry is set.
- One unconditional `SupplyStrip`, and two consistently named waivers,
  `SuitSupplyWaiver` and `FollowUpSupplyWaiver`.
- Recover pays at `RecoverCost`.
- The Campaign reason is the restriction's own text.
- The offer is a walker decision, not a panel (walker-driven game loop, slice 1).
- Second Wind is a battle plan for either side, not a start modifier.
- The warning hook is `Warned`, `ContributingPower.warning` and
  `OptionWarnings`.
- There is no journal version bump.

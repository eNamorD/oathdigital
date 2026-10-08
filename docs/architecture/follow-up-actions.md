# Follow-up actions

A power can owe its player an action to take once the current one ends. The
owed action is a full action of its own: its own walker run, its own Game Log
section, modifiers chosen as for any start, and no Supply spent. Its player
starts it or skips it before anything else happens. Knights Errant, Hunting
Party and Second Wind are built on it.

Design: [follow-up actions design](../superpowers/specs/2026-10-08-follow-up-actions-design.md).
Plan: [follow-up actions plan](../superpowers/plans/2026-10-08-follow-up-actions.md).

## The queue and its replay

- `FollowUp(player, action, source)` (`model/GameState.scala`) is one owed
  action. `source` is the power that queued it, which the Game Log credits.
  `player` is the one place an off-turn actor will differ from the active
  player.
- `CurrentGameState.followUps` is the queue, oldest first. It outlives runs
  and phases. `walkerFollowUp` is the entry the current run was started
  from. It is walker scratch, which `WalkerCompleted` clears.
- A power appends an entry with the primitive op `QueueFollowUp`
  (`model/CoreOperations.scala`, applied by `TurnStateOperations`), inside
  the origin action's tree.
- Two walker events take the head (`gameplay/walker/WalkerEvents.scala`):
  - `FollowUpStarted` is the first event of a queued run. Replay moves the
    head into `walkerFollowUp`.
  - `FollowUpSkipped` drops the head.

  `WalkerReplay` refuses either event when it does not name the head, and
  refuses a start while another run is open. It is an event rather than a
  `WalkerParked` field, because a run can finish in its starting command
  without parking.
- Wire spellings: the op kind is `queue-follow-up`. The event types are
  `walker.follow-up-started` and `walker.follow-up-skipped`. The entry's
  codec is in `serialization/FollowUpWire.scala`, beside the power-source
  and procedure spellings it shares with the other walker events. The
  journal format version was not bumped. Readers accept only the exact
  version, so a bump would have stopped every stored game, not only the
  ones holding a nested Knights Errant Campaign.

## Start, skip and the service gate

- `OathRulesWalker.startWalker` dispatches on the queue:
  - With an empty queue, `startNow` is the normal start.
  - With a non-empty queue, `startFollowUp` accepts only the head's action
    from the head's player (`WrongPlayer`, `FollowUpPending`). It evolves
    `FollowUpStarted` first, then runs the normal start on that state, so
    the build sees the entry. A start that fails refuses the whole command,
    and the queue is unchanged.
- `skipFollowUp` emits `FollowUpSkipped` whether or not the head could
  start.
- `OathLifecycle.validateAct` skips its Act-phase check while
  `walkerFollowUp` is set. A Special Envoy Travel that enters Rest still
  lets the next queued Campaign start.
- Automatic Finish Rest and End Wake wait while anything is queued.
- `GameApplicationService.applyCommand` refuses every other command with
  `FollowUpPending` while an entry waits:
  - It allows the head's start, `WithModifiers` included, and its skip
    (`GameCommand.SkipFollowUp`, intent `skipFollowUp`).
  - Undo and Redo never reach this gate.
  - The rejection reads "Start or skip the queued {action} first".

## The Supply waiver

- `FollowUpSupplyWaiver` (`gameplay/walker/FollowUpSupplyWaiver.scala`) is
  an automatic engine power, id `engine.follow-up-supply`.
- `WalkerPowers.selected` always offers it. Most of its callers have no
  state, so it gates itself in `applicable`: it applies only at the cost
  window of the action `walkerFollowUp` names, on the start and on every
  resume.
- It waives Supply only. Recover pays inside its repeat with no cost
  window, so a queued Recover would still pay. Nothing queues one.
- The waiver strips through `gameplay/operations/SupplyStrip.scala`. Every
  unconditional Supply strip now uses `SupplyStrip`: News from Afar,
  Awaited Return, and the free-Travel powers, which used
  `TravelPayments.withoutSupply`. `SupplyStrip` also filters the payments a
  `BuildOps` builds.
- The economy's by-suit waiver (Cup of Plenty, Animal Playmates, Birdsong)
  decides from the answered card at run time, so it stays separate as
  `SuitSupplyWaiver`.

## Projection and the panel

- `GameProjection.followUp` (`FollowUpProjection`) shows the head to every
  viewer:
  - its player, action and label;
  - its source's label;
  - whether it can start, and why not;
  - the labels of the entries behind it (`queuedAfter`).
- `LegalActionProjector.followUpProjection` asks whether the head can start
  of the state `FollowUpStarted` would leave. The waiver and the Act-phase
  exemption therefore apply as they would on a real start.
- Each action declares its own reason (`followUpReason`):
  - A Campaign shows its restriction's own text, such as Vow of Peace's.
    With nothing to attack, it reads "No site to conquer and no enemy pawn
    to raid". `CampaignProcedure.refusal` asks the restriction first.
  - A Travel reads "No site to travel to".
  - Any other action reads "Cannot start now".
- While an entry waits:
  - The head's player gets only `beginFollowUp` and `skipFollowUp`.
  - The phase reads `follow-up-pending`.
  - Minor actions and action selection are closed.
- The frontend's `FollowUpControls` draws the start button, which is
  disabled with its reason on hover when the action cannot start, then Skip
  and a "Then: …" line.
- The start goes through the modifier flow, and the modifier order replaces
  the panel while it is open (a queued Travel with Special Envoy, say).
- A waiting viewer's status reads "{player} may campaign (Knights Errant)".

## Game Log

- `FollowUpStarted` opens the queued run's segment. The start tag appends
  the source, as in "Campaign · Knights Errant" (`StartLines.queuedBy`).
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
  as Vow of Peace shows on the queued Campaign's button.
- **Hunting Party** (`powers/search/HuntingParty.scala`) queues a Campaign
  after a Search of the world deck. While it is selected, a discard pile
  warns "Hunting Party needs the world deck".
- **Second Wind** (`powers/campaign/SecondWind.scala`) is an attacker's
  battle plan. Once the Campaign is won, its `later` step at the root queues
  a Travel and then a Campaign.
  - The two entries are independent: skipping the Travel still offers the
    Campaign.
  - A plan's cost may be paid onto a card that already holds tokens, so the
    queued Campaign offers it again. A chain ends only when its cost cannot
    be paid.
  - A defender is not offered it: a winning defender would be owed actions
    off turn, which waits for the off-turn actor.

## Queueing another action

Only Campaign and Travel are queued today. A power that first queues
another action must also:

- add the action to `LegalActionProjector.followUpStartable`. A Trade or
  Forge falls to "cannot start", and Recover skips the pawn-site check its
  normal control makes;
- give the start its arguments (a Trade's resource) in `FollowUpControls`;
- give Recover a cost window, or the queued Recover pays Supply under a "for
  no Supply" button;
- anchor the Muster and Trade log tag on their first step with ops. Under
  the waiver they pay nothing, so `StartLines.opening` would post the tag at
  completion.

## Sneak Attack hook

Sneak Attack will queue a Campaign for a player who is not the active
player. `FollowUp.player` and the requester comparison in `startFollowUp` are
the hooks. Campaign powers still read `ctx.activePlayer` as the attacker, and
modifiers are selected only on turn, so the off-turn actor is still to be
built. The projection's `startable` reads the active player, and the panel
follows `activeParticipantId`; both change with the requester check.

## Decisions beyond the design

- The waiver is always offered and gated by state, not appended only when
  an entry is set.
- One unconditional `SupplyStrip`, and two consistently named waivers,
  `SuitSupplyWaiver` and `FollowUpSupplyWaiver`.
- Recover has no cost window.
- The Campaign reason is the restriction's own text.
- `queuedAfter` holds labels, not a count.
- Second Wind is an attacker-only battle plan, not a start modifier.
- The warning hook is `Warned`, `ContributingPower.warning` and
  `OptionWarnings`.
- There is no journal version bump.

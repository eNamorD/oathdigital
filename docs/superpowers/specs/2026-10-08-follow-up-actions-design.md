# Follow-up Actions

**Status:** designed 2026-10-08. It takes the roadmap's **Phase - Follow-up
actions**, without Search Party (its own spec) and without Sneak Attack (needs
an off-turn actor).

**Amended 2026-10-09** by the
[walker-driven game loop](2026-10-09-walker-game-loop-design.md), slice 1:
a follow-up is a queued offer the walker asks. Rulings 2, 3 and 6 and the
sections "Start and skip", "Cost waiver" and "Projection and UI" below are
rewritten to match. The rest stands, under the new names: `FollowUp` is
`QueuedProcedure`, `followUps` is `queue`, `walkerFollowUp` is `walkerEntry`,
`QueueFollowUp` is `QueueProcedure`, and `FollowUpStarted` is
`ProcedureDequeued`. The
[architecture note](../../architecture/follow-up-actions.md) holds the
current wiring.

## Goal

A power can queue an action to run after the current one. The queued action
is a full action: it runs as its own walker run, the player selects modifiers
for it, and it gets its own Game Log section, action lines and roll feedback.
It spends no Supply. Knights Errant moves onto this mechanism, and Hunting
Party and Second Wind are built on it.

## Background

Knights Errant (120) nests a Campaign inside the Muster. Two nodes are
appended to the Muster's tree: a decision whether to campaign, and a node
that rebuilds the Campaign's tree from live state. Its Supply payment is
removed at `CampaignCost` only when `PowerCtx.procedure` is Muster, which
works only because the nested Campaign runs on a resume. The nested Campaign
posts no Game Log lines, because the run's procedure is Muster, and the Muster
registry entry borrows Campaign's roll feedback.

No queue exists today. Between two walker runs, the projection and the
controls have no notion of an action owed to a player. `startWalker` refuses
any start while a walker is pending, refuses a requester other than the
active player, and the Act controls appear only in the Act phase.

The [catalog batch 4 rulings](2026-10-05-catalog-batch-4-rulings.md#moved-out-of-the-batch)
moved Hunting Party here as "a Campaign queued after the Search rather than
nested in it. The queued Campaign is a full action and may take modifiers."

## Rulings

1. **Scope.** The mechanism, the Knights Errant migration, Hunting Party and
   Second Wind. Search Party's problems are card-play nesting, not action
   queuing, and get their own spec. Sneak Attack needs an off-turn actor and
   stays engine-blocked; this design keeps hooks for it (see Sneak Attack
   hooks).
2. **Opt-in after the origin ends.** The origin action completes. The walker
   then asks the player: the queued action, for no Supply, or Skip. Taking
   it starts the action at once, with its own modifier decision. No question
   is asked inside the origin action.
3. **Nothing in between.** The offer is a parked walker decision, so no
   other command runs until it is answered. "After X, you may Y" means Y
   right away.
4. **The engine waives Supply.** Every queued action spends no Supply. The
   source power takes no part in the queued run.
5. **An unstartable entry stays.** When the queued action cannot start, its
   button shows disabled with a hover reason, and Skip is the only way on.
   This follows the project's move toward hover reasons over hiding.
6. **The Act phase ending leaves the queue.** Nothing in the rules stops a
   Campaign after the Act phase (Sneak Attack campaigns off turn). When a run
   enters Rest (Special Envoy on a queued Travel, for example), the queue
   drains first, and a queued action's start skips the phase check.
7. **No journal compatibility.** Stored games whose journals contain a nested
   Knights Errant Campaign stop replaying. The journal version marker is
   not bumped (2026-10-08): readers accept only the exact version, so a bump
   would stop every stored game, and the pre-release policy makes it
   optional.
8. **The log credits the source.** A queued run's tag reads
   "Campaign · Knights Errant". Skipping writes a line.
9. **Second Wind's entries are independent.** It queues a Travel and then a
   Campaign. Skipping the Travel still offers the Campaign. The order is
   fixed.
10. **Hunting Party is a selected modifier.** It is a free Search modifier,
    like Knights Errant on Muster. It queues the Campaign only when the
    source is the world deck. While it is selected, each discard pile in the
    source decision carries a warning reason and stays choosable.
11. **Queued runs may queue more.** A queued action is a full action, so its
    modifiers can queue further actions. New entries go to the back of the
    queue.

## Design

### State, ops and events

`CurrentGameState` gains two fields:

- `followUps: Vector[FollowUp]`, the queue. It persists across runs and
  phases.
- `walkerFollowUp: Option[FollowUp]`, the entry the current run was started
  from. It is walker scratch: replaying `WalkerCompleted` clears it with the
  other scratch fields.

```scala
final case class FollowUp(player: PlayerId, action: ActionRef,
  source: PowerSourceRef)
```

A new primitive op, `QueueFollowUp(followUp)`, appends to `followUps`. Source
powers emit it inside the origin's tree, so it is recorded in
`WalkerStepRecorded.ops` and replays like any delta. Entries are appended in
tree order, and entries queued by a queued run go behind the ones already
waiting.

A new walker event, `FollowUpStarted(followUp)`, is the first event of a
queued run's starting command. Replay removes the head of `followUps` and sets
`walkerFollowUp`. It is an event, not a `WalkerParked` field, because a run
can finish in its starting command without parking, and the log tag and the
cost waiver still need the entry.

A new event, `FollowUpSkipped(followUp)`, removes the head of `followUps`. It
anchors the Skip line.

`EnterPhase` does not touch `followUps`.

### Start and skip

When a walk ends, the driver (`OathDriver.next`) takes the queue's head with
`ProcedureDequeued(entry)`, which holds it in `walkerEntry`, and starts the
follow-up offer (`FollowUpOffer`). The offer asks the entry's actor: the
action, or Skip.

- The action's option is priced by `QueuedStart.refusal`: the checks the
  start runs, on the state it would see. When it would be refused, the
  option is unavailable with its reason, and Skip stays available.
- Taking it puts the entry, as a `Run`, in front of the queue. The driver
  dequeues it and starts the action in the same command.
- Skip ends the offer's walk. `FollowUpSkipped(entry)` anchors its Game Log
  line.

A queued run completes like any action. The action boundary runs once the
queue is empty, so a chain gets one boundary, after its last action.

When an action ends the game (`result` is set), a leftover queue is ignored.

### Cost waiver

`FollowUpSupplyWaiver` is always offered and reads `walkerEntry`. When the
entry has a source, its `Transform` at the action's cost window removes every
`SpendSupply` by the entry's actor. Every walker path builds its powers
through `WalkerPowers`, so the waiver reaches the walk on start and on every
resume, the offer's dry run and any preview. Recover pays at `RecoverCost`.

The action-to-cost-window map is total over the seven actions:
`Campaign → CampaignCost`, `Travel → TravelCost`, `Search → SearchCost`,
`Challenge`, `Forge`, `Muster` and `Trade` (their shared cost window in `EconomyTree`) and
`Recover`. It costs little and needs no update when a new card queues another
action.

Cost nodes come as a `Sequence` (Campaign) or a `BuildOps` (Search, which News
from Afar re-wraps). News from Afar's re-wrap is extracted into a shared
`SupplyWaiver.strip(actor)`, used by News from Afar and the engine waiver.

The waiver removes Supply only. A selected power that also waives Supply on a
queued run filters again, harmlessly.

### Projection and UI

The offer is a parked walker decision: the frontend draws it as any
choose-one, with the reason on the unavailable option's hover. There is no
follow-up projection, control or panel.

### Game Log and roll feedback

A queued run is a normal top-level run. `LogJournal` counts
`FollowUpStarted` as a walker event of the run's first segment. The run's
procedure still comes from that segment's closing event, so the section, its
id and its action key need no change.

`StartLines` reads `FollowUpStarted` in the run's first segment and appends
the source's name to the tag: "Campaign · Knights Errant". The Supply span is
absent because 0 was spent. The modifiers line works as before.

Because `run.procedure` is now Campaign, `ActionLines` and `DetailLines`
write the full battle lines for a Knights Errant Campaign.

The Skip line, "Knights Errant: Campaign skipped.", is anchored on
`FollowUpSkipped`. The event follows the previous run's `WalkerCompleted`, so
the line joins the latest section in time order: a Second Wind Campaign
skipped after its Travel shows under the Travel's section. The line does not
name the actor.

No entry is ever dropped without the player's Skip, so there is no "dropped"
line.

Roll feedback works through the Campaign registry entry, keyed by
`walkerProcedure`. The Muster entry's borrowed `CampaignProcedure.rollFeedback`
is deleted.

### Cards

**Knights Errant (120), migrated.** It stays a free selected Muster modifier.
Its `MusterActionEligibility` Transform appends one op after the gain:
`QueueFollowUp(FollowUp(actor, Campaign, Card(120)))`, unconditionally.
Deleted: the decision `muster.knights-errant.campaign`, the nested
`CampaignProcedure.rebuild` branch, the `CampaignCost` contribution, the
`appliesAt` check on `ctx.procedure`, the `campaigns` note key and the
`catalog` constructor parameter. The walker's restriction look-ahead no longer
meets a nested Campaign: Vow of Peace or a protecting Fortress now fails the
queued start, so the button shows disabled with the restriction reason.

**Hunting Party (122), new.** A free selected Search modifier. A `Branch` at
the end of the Search tree reads the resolved source and emits
`QueueFollowUp(FollowUp(actor, Campaign, Card(122)))` for the world deck,
nothing for a discard pile. While it is selected, each discard-pile option in
the source decision carries the reason "Hunting Party needs the world deck"
and stays choosable. The plan finds how a selected power adds reasons to the
source decision's options and adds the smallest hook if none exists.

**Second Wind (16), new.** A battle plan, offered to the attacker only
(2026-10-08: a winning defender would be owed actions off turn, which waits
for the off-turn actor). Its cost (1 secret, 1 favor burnt) is paid when the
plan is chosen, and a plan's cost may be paid onto a card that already holds
tokens. Once the outcome is known, its `later` step at the Campaign's root
queues a Travel and then a Campaign, both from `Card(16)`, when its user won.
A queued Campaign offers it again, so a chain is limited only by its cost.

Hunting Party and Second Wind move out of the holding catalog files into power
classes registered in `WalkerPowerCatalog`.

### Sneak Attack hooks

Sneak Attack (98) stays under the roadmap's engine-blocked Campaign item. Its
missing piece is an off-turn actor: Campaign powers read `ctx.activePlayer` as
the attacker, `startWalker` accepts only the active player, and the projector
and modifier offers assume the active player acts. This design keeps the
entry's `player` field and compares the requester with `followUps.head.player`
in one place, so Sneak Attack can queue through follow-ups once an off-turn
actor exists.

## Testing

Rules scenarios at the `OathRules` level, with real trees:

- Queue lifecycle: an origin queues an entry; the gate refuses other actions,
  minor actions and other players; the queued start removes the head and
  emits `FollowUpStarted`; completion offers the next head; `SkipFollowUp`
  removes the head.
- Waiver: a queued Campaign spends no Supply, a normal Campaign in the same
  game does, and the waiver survives the Campaign's resumes.
- Unstartable head: `startable` is false, the start is refused with the queue
  unchanged, and Skip works.
- Act phase end: a queued Travel with Special Envoy enters Rest, the queued
  Campaign still starts, and the Rest controls come after it.
- Order: entries queued by a queued run go to the back.
- Replay: each scenario's journal replays to the same state, `followUps` and
  `walkerFollowUp` included.

Projection: `FollowUpProjection`'s `startable`, `reason` (Campaign and Travel
reasons, the fallback, the restriction reason first) and `queuedAfter`; only
the head player sees controls; the phase label.

Game Log: a queued run's own section, its tag "Campaign · Knights Errant" and
its battle lines; the Skip line in the latest section.

Cards: `KnightsErrantSuite` rewritten (queues after a Muster; a Muster without
it queues nothing; its Vow of Peace case becomes a disabled button with the
reason). New `HuntingPartySuite` (world deck against discard pile; the pile's
reason). New `SecondWindSuite` (queues on a win, not on a loss; cost paid at
the start; independent entries; a chain).

Some action or power code may assume `phase == Act`. The plan audits the
Campaign and Travel paths for it, since a queued action can run in Rest.

## Docs

- ROADMAP: delete the phase; point the Sneak Attack line at follow-ups
  ("needs an off-turn actor; queues via `FollowUp.player`"); add the cleanup
  item "Play-test follow-ups in a browser".
- Batch-4 rulings: update the Hunting Party and Search Party rows.
- New architecture note: `docs/architecture/follow-up-actions.md`.

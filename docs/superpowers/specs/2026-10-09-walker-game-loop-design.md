# Walker-Driven Game Loop

**Status:** designed 2026-10-09. Slice 1 landed: the queue, the driver and follow-up
offers. Slice 2 is ready to plan.

**Amends**, in the slices that change the behavior:

- the [follow-up actions design](2026-10-08-follow-up-actions-design.md):
  rulings 2, 3 and 6, "Start and skip", "Cost waiver" and "Projection and
  UI" (slice 1);
- the [ownership and phases design](2026-09-12-walker-ownership-and-phases-design.md):
  "Triggered procedures" and the action boundary (slice 1);
- ruling 9 of the [modifier walker design](2026-10-09-modifier-walker-design.md)
  (slice 5).

## Goal

The walker runs the whole turn. It keeps a queue of procedures to run, each
as its own walk. When a walk ends, the walker starts the next queued
procedure. When nothing is queued, it runs the state checks that are due and
then puts the current phase's menu in front of the player: a decision over
everything the player may do now.

Then:

- every option the player sees is a walker option, with the same pricing,
  hover reasons and warnings as any other decision;
- a phase power adds itself to its phase's menu, as a battle plan adds
  itself to the plan decision;
- follow-up actions stop being a parallel mechanism;
- whether an action can start is decided exactly, through the action's own
  modifier decision, instead of by the `ModifierOffers.selections`
  heuristic.

## Background

Most of the turn already runs on the walker: Setup, Take Wealth, End Wake,
Begin Rest, Finish Rest, Use Power, Oathkeeper, Forced Wake, every major
action, Negotiation and the facedown-adviser play. What holds the turn
together does not:

- **Sequencing glue** in `OathRules` and `OathRulesWalker`, about 200 lines:
  `completeAction`, `oathkeeperStep`, `turnBoundary`, `enterWake`,
  `forcedWake`, `autoEndWake` and `autoFinishRest`. The last two start a
  walk by impersonating the active player, so one command can chain Begin
  Rest, Finish Rest, the turn boundary, Wake entry, Forced Wake and End
  Wake.
- **The follow-up queue**, about 340 lines from engine to frontend. It has its
  own panel, its own projection, a hand-written `followUpStartable` match
  over six actions, a service gate and an Act-phase exemption.
- **Start legality.** `LegalActionProjector` builds about 25 string
  controls. Nine procedures have their own `startable` check, each tried
  with no modifier, each modifier alone and all of them together. Forge,
  Begin Rest, the facedown play and the minor actions use plain state
  checks. The frontend maps each control to a button by hand, with costs
  written into the labels.
- **Three legacy minor actions**, Peek Site Relics, Reveal Owned Relic and
  Move Warbands, outside the walker. Their buttons ignore `legalControls`.
- **Phase-power offering.** `PhasePowerProcedure.check` and `usable` restate
  each other, and `PhasePowerProjector` builds its own catalogs.
- **State-based events**: bandit refill, the Usurper and Vision checks,
  round end and War Exhaustion.
- **A gap.** The first Wake after Setup skips `enterWake`, so its
  ignored-rule diagnostics are never recorded.

A single walk that ran the whole turn was ruled out. The walker treats one
walk as one action: `answered` is read by first match on fixed decision ids,
`rollOutcomes` accumulates over the walk, every `WalkerParked` re-records
every answer, the look-ahead searches to the end of the tree, and the Game
Log makes one section per walk. A queue of walks keeps all of that true.

## Rulings

1. **Scope.** The queue, the follow-ups moved onto it, every hook that runs
   when the queue is empty, the Wake, Act and Rest menus, the three legacy
   minor actions moved onto the walker, and the deletion of what the menus
   replace. Chronicle steps, game end and Sneak Attack stay out; the design
   leaves room for them.
2. **Every menu option queues a procedure.** Ending the phase is an option
   like any other: "End Wake" queues `EndWake`, "End Act and Rest" queues
   `BeginRest`, and "Finish Rest" queues `FinishRest`. A menu has no pass
   option, and a menu walk ends after one pick.
3. **Wake and Rest skip the question; Act always asks.** When the
   phase-ending option is the only one available in Wake or Rest, it is
   queued without asking, as `autoEndWake` and `autoFinishRest` do today.
   The Act menu always asks and shows its unavailable options with their
   reasons.
4. **The boundary runs when the queue empties.** Bandit refill and the
   Oathkeeper check run only when the queue is empty, before the phase's
   menu is placed. A queued chain is a run of actions that happen at once,
   with no chance for either check in between, so Muster followed by Knights
   Errant's Campaign gets one boundary, after the Campaign.
5. **Only after an action.** The boundary runs only when an action walk has
   finished since the last boundary. It does not run after End Wake, Begin
   Rest, Finish Rest, a follow-up Skip or the Oathkeeper walk.
6. **A follow-up is an offer.** A power that grants a follow-up queues an
   `Offer` entry. The driver turns it into a menu with one option, the
   action, plus "Skip". An action that cannot start is shown unavailable
   with its reason, and Skip stays available.
7. **Phase powers join their menu.** Each menu is an `OfferHost`. A phase
   power adds an `Offer` contribution at its phase's menu window. (Its
   mechanism is replaced by plan-time ruling 1 of slice 2.)
8. **Exact pricing.** Each offer is priced by a dry run of its own procedure,
   which searches through that procedure's modifier decision. A budget test
   holds the menu's park to 50 ms. If it fails, the projection and the
   command share the search memo before any heuristic comes back.
9. **The menus check the phase.** A procedure no longer checks the phase it
   starts in; the menu that offers it does. A queued entry runs in any
   phase.
10. **The same panel, new data.** The Act menu is drawn as panel buttons in
    today's layout and groups. Each price comes from the dry run, and an
    unavailable option is disabled with its reason on hover.
11. **Two commands play the game.** Resolve and Roll answer every walker
    decision. Undo, Redo, Begin and the three consent intents stay.
    StartWalker, UsePower, EndWake, BeginRest, FinishRest, SkipFollowUp, the
    three minor-action intents and the dead `WithModifiers` go.
12. **A menu pick is silent.** It writes no Game Log line and opens no
    section; the queued action's start line records it. A follow-up's Skip
    keeps its line. Undo after a pick returns to the menu, since the pick and
    the queued action's first park are one command batch.
13. **State-based events stay events.** They run as driver hooks when the
    queue is empty, not as walker operations.
14. **No compatibility.** Stored games whose journals hold the old start
    commands stop replaying. The journal version is not bumped.
15. **Nest or queue.** One procedure's steps are built inside another's
    tree, through `Branch`, `BuildOps` or a shared builder, when the
    enclosing procedure has steps after the inner part, or when the text
    says "as if" (Oracle, Temple of Ancestors, False Prophet, Search Party).
    A nested part is a step of the enclosing action: it has no modifier
    decision and no boundary, and its lines go under the enclosing action.
    An action is queued when the text says "after X, you may Y" (Knights
    Errant, Hunting Party, Second Wind, Sneak Attack). A queued action is a
    full action, with its own walk, modifier decision and Game Log section,
    and no boundary before it.
16. **Actions granted by an ACTION power are ruled per card.** Wild Allies,
    Captains, Palanquin and Whispering Stone are ruled in their catalog
    batch, leaning toward queuing. The override field such an entry would
    need (any site, another actor, chosen participants) is not built here.
17. **Rewrites never reach a queued walk.** A deferral is resolved within its
    action and never crosses into a queued walk. The operation rewrite's
    planned walker-level reach covers nested subtrees only. A power that
    changes a later action uses a persistent rule, or queues its own entry.

## Design

### The queue

```scala
final case class QueuedProcedure(
    actor: PlayerId,
    procedure: StartableRef,
    startArgs: Vector[DecisionOptionRef],
    source: Option[PowerSourceRef],
    kind: QueueKind)

enum QueueKind:
  case Run, Offer
```

- `CurrentGameState.followUps` becomes `queue: Vector[QueuedProcedure]`, and
  `walkerFollowUp` becomes `walkerEntry: Option[QueuedProcedure]`, the entry
  the current walk was started from. It is walker scratch, cleared by
  `WalkerCompleted` as today.
- The op `QueueFollowUp` becomes `QueueProcedure(entry, atFront)`. It
  appends unless `atFront` is set, so follow-up ruling 11 (new entries go
  to the back) holds; only an offer's pick goes in front.
- `FollowUpStarted` becomes `ProcedureDequeued(entry)`. `FollowUpSkipped`
  keeps its meaning under its old name.
- `actor` keeps the hook the follow-up design left for Sneak Attack. Until
  an off-turn actor exists, a queued entry's actor is the active player.
- **Supply.** An entry with a `source` is a follow-up, and its walk waives
  Supply. The waiver reads `walkerEntry` in place of `walkerFollowUp`. Slice 1
  checks that Recover's cost is in a window the waiver reaches, which the
  follow-up architecture note says it is not.
- `startArgs` carry what a start button carries today: Trade's favor or
  secret, Take Wealth's choice, and a phase power's source.

### The driver

`walkerTransition` hands every finished walk to one function,
`OathDriver.next`, which replaces the glue listed under Background. The rule
that a triggered walk refuses to start while anything is pending stays: the
driver starts a walk only when the previous one has finished.

When the queue is not empty, the driver dequeues its head:

- a `Run` entry starts its procedure, owned by `entry.actor`, with its
  `startArgs`;
- an `Offer` entry starts `FollowUpOffer(entry)`, a menu with one option
  that queues the entry as a `Run`, plus "Skip".

When the queue is empty, the driver runs these steps in order:

1. **The action boundary**, when an action walk has finished since the last
   boundary (ruling 5). A flag in walker state, `boundaryDue`, is set when an
   `ActionRef` walk finishes and cleared by the boundary. The boundary records
   the ignored-rule diagnostics, refills the bandits, and runs the
   Oathkeeper check. When the check starts the Oathkeeper walk, the driver
   returns, and on that walk's end it resumes at step 2.
2. **Phase hooks**, after a phase-ending walk:
   - after Finish Rest: round end and War Exhaustion when the last player
     has rested;
   - on entering Wake: the Wake diagnostics, the Usurper and Vision checks,
     then the Forced Wake walk when one is due. When Forced Wake starts, the
     driver returns, and on its end resumes at step 3.

   Setup ends as Finish Rest does, so the first Wake runs these hooks too.
3. **The phase's menu.** In Wake and Rest, when the phase-ending option is
   the only one available, the driver queues it and loops (ruling 3).
   Otherwise it starts the menu walk.

A result ends the game, and the driver starts nothing after it.

Each walk the driver starts gets a fresh `WalkCtx`, so a turn that changes
inside one command (Finish Rest's `BeginTurn`) gives the next walk the new
active player.

### The menus

`PhaseMenu(phase)` is a procedure whose tree is one `OfferHost` at the
phase's menu window: `WakeMenu`, `ActMenu` or `RestMenu`. Like
`CampaignPlanChoice`, it gathers offers on every fold:

- **the engine's offers** for the phase:
  - Wake: Take Wealth (favor), Take Wealth (secret), End Wake;
  - Act: the major actions (Trade twice, once per resource), Place Banner
    Resource, Negotiation, the facedown-adviser play, the three minor
    actions, End Act and Rest;
  - Rest: Finish Rest;
- **phase powers' offers**: one for each accessible source of each
  `PhasePower` of the phase's timing that is not forced (plan-time ruling 1
  of slice 2). Its once-per-turn limit, its source's accessibility and its
  `usable` stay its own. Hunger and any other forced power are started by
  Forced Wake and offer nothing.

Each offer is a `QueuedProcedure` with `kind = Run` and no `source`. It is
priced by `WalkerSimulation` over its procedure's tree from the live state,
and shown unavailable with the dry run's first violation when no path
starts. The pick's child op is `QueueProcedure(offer)`, and the menu walk
ends.

`PhasePowerProcedure.check` and `usable` merge into the offer's dry run.
`validateAct` leaves the 11 procedures that call it, and `validateReady`
leaves End Wake and Take Wealth (ruling 9). `OathLifecycle`'s follow-up
exemption goes.

### The minor actions

Peek Site Relics, Reveal Owned Relic and Move Warbands become walker
procedures, offered by the Act menu. Their rules move unchanged from
`MinorActions.handle` into their trees, and `MinorActionOperationPolicy`,
their events and their codecs go. The Act menu's dry run applies every
restriction to them, Vow of Wandering's included, which the projection's
heuristic skips today.

### Frontend and projection

- The parked-decision renderer draws a menu as panel buttons, grouped as
  today's Act panel is: major actions, minor actions, powers, then the
  phase-ending button. Wake and Rest use the same renderer.
- A button's label carries the dry run's price, in place of the hard-coded
  "(1 Supply)".
- `FollowUpControls`, `PhasePowerButtons`, `PhasePowerProjector`, the
  hand-mapped start buttons and the controls in `LegalActionProjector` go.
  `GameProjection.actionSelectionOpen` and the projector's other
  `walkerPending.isEmpty` gates go with them, since a decision is always
  parked while a game is live.

### The Game Log

- A menu walk writes nothing and opens no section (ruling 12).
- A queued action's tag credits its source, as follow-up ruling 8 says.
- Skip writes its line.
- The headline lines stay: they come from `BeginTurn`, `EnterPhase` and
  `RoundEnded`, which no walk owns.

### What goes away

By the last slice:

- `completeAction`, `oathkeeperStep`, `turnBoundary`, `enterWake`,
  `forcedWake`, `autoEndWake`, `autoFinishRest`, `startFollowUp`,
  `skipFollowUp` and `startTriggered`'s callers in `OathRules`;
- the service's follow-up gate, `answersFollowUp` and the `FollowUpPending`
  error;
- the deleted commands of ruling 11, with their intents, decoders, codecs,
  `GameIntentMapper` cases and authorization helpers;
- `LegalActionProjector`'s controls, the nine `startable` and `startOptions`
  functions, and `ModifierOffers.selections` with its start-control use;
- `MinorActions`, `MinorActionOperationPolicy` and their events;
- the frontend pieces under "Frontend and projection".

## Slices

1. **The queue and the driver.** `QueuedProcedure`, `QueueProcedure`,
   `ProcedureDequeued`, the `Offer` entry and `FollowUpOffer`, the Supply
   waiver on `walkerEntry`, and `OathDriver.next` with every empty-queue
   hook. The glue goes. Until the menus exist, an empty queue ends with
   nothing parked, and the player starts actions with today's commands.
   The follow-up design's and the ownership design's amended sections are
   rewritten, ruling 15 replaces "Nothing nests", and ruling 17 is added to
   the operation rewrite design's extension points.
2. **The Wake and Rest menus.** Take Wealth, the Wake and Rest phase powers,
   End Wake and Finish Rest move onto `PhaseMenu`, with the skip of ruling 3.
3. **The minor actions.** The three legacy minor actions become walker
   procedures.
4. **The Act menu.** `ActMenu`, its offers, the budget test, and the
   frontend panel.
5. **The cleanup.** Everything under "What goes away" that is still there,
   and modifier walker ruling 9 rewritten.

## Plan-time rulings (slice 1)

1. **An offer's pick goes to the front.** `QueueProcedure(entry, atFront)`
   appends by default. The follow-up offer's pick puts its `Run` entry in
   front, or Second Wind's Travel would wait behind its own queued Campaign
   offer.
2. **The driver starts a phase-ending procedure directly.** When End Wake or
   Finish Rest is the only option left (ruling 3), the driver starts it
   without queueing it.
3. **`ActionBoundaryRan` clears `boundaryDue`.** The boundary may write
   nothing else, so a marker event keeps replay exact. It writes no Game Log
   line.
4. **The offer's option runs the start's own checks.** `QueuedStart.refusal`
   builds the entry's tree on the state its start would see, then runs the
   restriction check, the first walk and the playable-option gate, as a
   start does. The look-ahead through the modifier decision stays slice 4's.
5. **Skip is an answer.** The offer's Skip ends its walk, and
   `FollowUpSkipped(entry)` is written before `WalkerCompleted` for the Game
   Log line. Replay checks that it names `walkerEntry` and changes nothing.
6. **Recover gets a cost window**, `RecoverCost`, around the Supply it pays
   on each roll, so the follow-up waiver reaches it.
7. **The automatic Finish Rest follows any walk.** Rest also ends after the
   last REST power is used, and after a queued action that ran in Rest.
8. **The turn-start hooks are keyed on the finished walk**: Finish Rest and
   Setup. Neither queues an entry or leaves the boundary due.
9. **The follow-up client surface goes in slice 1.** The offer is a walker
   decision, so the follow-up projection, controls, panel and Skip command
   are deleted here, not in slice 5.

## Plan-time rulings (slice 2)

1. **The engine lists the phase powers.** A menu reads the phase power
   catalog, as Use Power and Forced Wake do: one option for each accessible
   source of each power its player may choose in the phase. No menu window
   and no `Offer` contribution are built. A `PhasePower` is not a
   `ContributingPower`, and its sources come from the rule-source index, not
   from one power instance per source. This replaces ruling 7's mechanism. A
   window is added when a power needs to restrict a menu option.
2. **One procedure per menu.** `WakeMenu` and `RestMenu` are triggered
   procedures, both built by `PhaseMenu`. Slice 4 adds `ActMenu`.
3. **A menu lists every option of its phase.** Wake: Take Wealth for a
   favor, Take Wealth for a secret, each WAKE power's source, then End Wake.
   Rest: each REST power's source, then Finish Rest. An option that cannot
   start is shown unavailable with its reason. A phase power's option is its
   source.
4. **Availability is the start's own check**, `QueuedStart.refusal`. No
   Wake or Rest option costs anything today, so none shows a price; the
   dry-run price arrives with the Act menu.
5. **The menus draw as a panel**, in the generic choose-one panel. Hovering
   over a card option shows its rules text. The grouped Act layout is slice
   4's.
6. **The phase checks stay in Take Wealth, End Wake and Finish Rest** while
   `StartWalker` can still start them. Ruling 9's removal moves to slice 4.
7. **The End Wake and Finish Rest commands go in slice 2.** In Wake and Rest
   a menu is parked or the phase has ended by itself, so nothing can send
   them. `UsePower` and `BeginRest` stay until the Act menu.

## Testing

- **Driver:**
  - a queued `Run` starts at once, and a queued `Offer` asks with Skip;
  - an unstartable offer shows its reason and Skip still works;
  - Second Wind's Travel and Campaign stay independent;
  - a queued chain gets one boundary, after its last action;
  - no boundary runs after End Wake, Begin Rest, Finish Rest, a Skip or the
    Oathkeeper walk;
  - the Oathkeeper walk resumes the driver at the phase hooks;
  - the first Wake after Setup records its diagnostics;
  - a result stops the driver.
- **Menus:**
  - each phase's offers, priced, with their reasons;
  - Wake and Rest skip the question when only the phase-ending option is
    left, and Act always asks;
  - a phase power appears in its menu and respects its once-per-turn limit;
  - a start that needs two of three modifiers is offered (the roadmap's
    "Start controls and modifier combinations");
  - the budget test at 50 ms per park.
- **Undo:** after a menu pick, Undo returns to the menu.
- **Game Log:** a pick writes nothing; Skip writes its line; a queued
  action's tag credits its source.
- **Minor actions:** each through the walker, with its restrictions.
- The verification gate is the roadmap's standing rule.

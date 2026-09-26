# Game Log: A Player-Facing Action History

> Status: design confirmed 2026-09-25 from an Impeccable shape pass over the
> table's Log pane, then amended 2026-09-26 by a full-feature design pass that
> checked the server half against the code. It covers the server-side
> formatter, the state change it needs, the wire contract, the seat route, and
> the pane and overlay that read them. No implementation is authorized by this
> document alone; a plan follows it.

## Why now

The Log pane has read "Game history will appear here." since the Arcs-panel
slice reserved it (2026-09-08). The 2026-09-25 critique scored Visibility of
System Status and Recognition Rather Than Recall at 2/4 each, and named the
dead pane its second P1: PRODUCT.md's third principle, "serve the cold return",
has no surface. The roadmap phase "Player-facing action history" already
describes the shape of the answer: a typed semantic formatter over
authoritative event batches, grouped into player actions, with public and
player-scoped projections that never leak hidden information.

This design settles the decisions that phase left open and turns them into a
contract a builder can implement without inventing anything.

## Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Reading direction | Chronological, newest at bottom, sticks to bottom while already there. |
| 2 | Granularity | One headline per round, per turn, and per action; subordinate lines beneath an action. |
| 3 | Room | The 16% pane row stays. Clicking the pane heading opens a full-height overlay with the whole log. |
| 4 | Last-looked marker | Client side, `localStorage`, keyed by game and seat. Not on the server. |
| 5 | Hidden information | Resolved on the server per viewer, per card, from the game state on each side of the operation. |
| 6 | Entry shape | Typed spans, not a string: text, player, card, site, and amount refs. |
| 7 | State per event | A new `EventReplayEngine.scan` exposes the state before and after every event. The log and the board share one fold. |
| 8 | Entry provenance | Every entry traces to exactly one journal event. One event may produce several entries. |
| 9 | Unknown input | Compile-time exhaustive matches over `OathEvent` and `ProcedureRef`. Operations inside a step use an allow-list; an unlisted operation is silent. |
| 10 | Voice | Past-tense verbs throughout, subject omitted under the actor's own turn. |
| 11 | Knowledge | A player who knew a card keeps knowing it when the card moves. Fixed in game state, so the board benefits too. |
| 12 | Delivery | Three vertical slices (see Delivery). |

## Vocabulary

- **Entry**: one line of the log, at one depth, with a stable `(sequence, ordinal)` key.
- **Headline**: an entry at depth 0. Round, turn, action, and victory entries are headlines.
- **Subordinate line**: an entry at depth 1 under an action or turn: a decision, roll, resource delta, or triggered power.
- **Span**: one piece of an entry's text. Either plain text or a typed reference.
- **Viewer**: the seat the log is formatted for, or nobody for an observer.
- **Run**: the contiguous journal events one walker records for one procedure, from its first event up to a park or completion.
- **Divider**: the client-only line "Since you last looked" placed before the first entry newer than the stored marker.

## The formatter

### Input

The formatter reads the journal the application already folds for state:
`Vector[RecordedEvent[OathEvent]]` in index order. `RecordedEvent.index` is
the journal sequence this document calls `sequence`. The formatter never reads
the raw JSON envelope.

It needs the game state around each event, for two reasons. Operations carry
only requested amounts (`GainSupply` and `SpendSupply` hold a delta that is
clamped to 0..7 when applied), so a line such as "Increased supply from 2 to 5"
needs the values on both sides. And whether a viewer may see a card's name
depends on the accumulated `CardKnowledge` and the card's current orientation,
which no single event carries.

`EventReplayEngine.replay` (`engine/Engine.scala`) folds the journal but
returns only the final state. This design adds a sibling:

```scala
final case class ReplayStep[S, E](event: RecordedEvent[E], before: S, after: S)

def scan(events: Vector[RecordedEvent[E]])
    : Either[EventReplayFailure[V], Vector[ReplayStep[S, E]]]
```

`replay` becomes the last step's `after` (or the initial state for an empty
journal), so the two cannot drift. `GameApplicationService.reconstruct` keeps
calling `replay`.

For a `walker.step-recorded` event whose batch holds several operations, the
formatter steps through the batch from `before`, applying each operation with
the same operation application replay uses. Every line and every visibility
judgement is therefore made against the state immediately before and after the
operation it describes, not the whole batch.

The formatter reads operations, not `DeltaMeaning`. `DeltaMeaning` falls back
to an operation's class name for every batch that is not a dice-pool change, a
supply spend, or a relic acquisition (`ProcedureWalker.deltaMeaning`), which is
too little to narrate from. The field stays as it is; its retention is a
separate decision under the replay posture, not part of this feature.

### Output

```scala
final case class LogEntry(
    sequence: Long,        // journal index of the event that produced it
    ordinal: Int,          // position among entries sharing a sequence
    kind: LogKind,
    depth: Int,            // 0 headline, 1 subordinate
    spans: Vector[LogSpan])

enum LogKind { case Round, Turn, Action, Decision, Roll, Delta, Trigger, Victory }

sealed trait LogSpan
object LogSpan:
  final case class Text(value: String) extends LogSpan
  final case class Player(id: String, name: String) extends LogSpan
  final case class Card(id: String, name: String) extends LogSpan
  final case class Site(id: String, name: String) extends LogSpan
  final case class Amount(value: Int, unit: String) extends LogSpan
```

`(sequence, ordinal)` is the stable identity of an entry, and entries are
emitted in that order. Display order equals key order, so the client only ever
appends. One journal event may produce several entries: a
`walker.step-recorded` whose batch both spends supply and moves warbands yields
two subordinate lines. Sequence references stay stable across replays, which
the roadmap requires for later replay navigation.

The formatter is a pure function of the scanned journal prefix and the viewer:

```scala
def format(steps: Vector[ReplayStep[OathState, OathEvent]],
    viewer: Option[PlayerId]): Vector[LogEntry]
```

Two viewers may receive different spans for the same entry. They always receive
the same entries with the same keys; only a `Card` span may differ, between a
name and a placeholder or count.

### Exhaustiveness

- The formatter matches `OathEvent` exhaustively at compile time. Each case
  either produces entries or is named in a `silent` branch with a comment
  saying why (for example `IgnoredRulesRecorded`, a diagnostic). A new event
  fails compilation until someone decides.
- The action headline matches `ProcedureRef` exhaustively. A new action or
  phase transition fails compilation until it has a verb. `use-power:{id}` is
  the one open-ended key and is covered by "Used {card}'s power".
- Operations inside a step batch go through an allow-list: the operations
  mapped in the templates below produce lines, and every other operation
  produces none. There is no exhaustiveness test over `CoreOperation`; most of
  its cases (`EnterPhase`, `Decide`, `Sequence`, `ModifyDicePool`, and so on)
  have nothing to say to a player.
- The formatter never emits an operation's or event's class name.

`SiteRelicsPeeked` and `OwnedRelicRevealed` are top-level `OathEvent` cases, not
operations, and are handled by the event match.

### Grouping

The journal carries no action-start or turn-start event as such, so boundaries
come from the operations and walker events already recorded:

| Boundary | Signal | Entry |
|----------|--------|-------|
| Game start | `setup.game-started` | Round headline "Setup", then the setup procedure's lines as subordinates |
| Round | `gameplay.round-ended` | Round headline "Round n" for `nextRound` |
| Turn | A `BeginTurn(player, phase)` operation, wherever it is recorded | Turn headline "Red's turn" |
| Action | The first event of a run whose procedure is an `ActionRef` | Action headline; subordinates are that run's lines |
| Phase transition | A run whose procedure is a `PhaseTransitionRef` | Subordinate line under the current turn: "Ended Wake", "Began resting" |
| Triggered | A run with `TriggeredProcedureRef.Oathkeeper` | Subordinate line: "Oathkeeper passed to Blue" |
| Victory | `gameplay.usurper-victory`, `gameplay.vision-victory`, `gameplay.war-exhaustion-resolved` | Victory headline |

`BeginTurn` is recorded by Finish Rest for every turn after the first, and as
the last leaf of `SetupProcedure` for the first turn. Both count. The
`BeginTurn` doc comment in `CoreOperations.scala`, which says Finish Rest is
the only procedure that declares it, is corrected in the same slice.

A round headline always precedes the first turn headline of its round. The
plan verifies the relative order of `gameplay.round-ended` and the round's
opening `BeginTurn` against the fixture journal; if the `BeginTurn` comes
first, the formatter emits the round headline at that operation's position and
`gameplay.round-ended` emits nothing.

**Action headlines are anchored to the first event of their run.**
`walker.step-recorded` carries no procedure; only the `walker.parked` or
`walker.completed` that ends the run names it. One player command appends all
its events in one transaction, and the state projection's `nextSequence`
advances once per command, so a formatted prefix always contains whole runs.
The formatter therefore looks ahead within the prefix to the run's closing
event, and emits the action headline at the run's first event with ordinal 0.
That event's own lines take ordinals 1 and up.

A run that parks and later resumes continues under the same headline. If any
other headline was emitted between the park and the resume, the formatter
repeats the action headline at the resume's first event, with the text span
" (continued)" appended, rather than attaching lines to a headline the reader
has scrolled past.

Actions that record no steps before completing still get a headline, anchored
to their `walker.completed`.

### Verbs

Headlines omit the subject under a turn headline. The subject appears when the
actor differs from the turn's player: a defender's choices, a negotiation
partner's transfers, a bandit refill, an oathkeeper trigger.

| `ProcedureRef` key | Headline | Detail source |
|--------------------|----------|---------------|
| `muster` | Mustered {amount} warbands at {site} | `Gain.Warbands` amount; pawn site |
| `travel` | Travelled to {site} | destination from the pawn `Move` |
| `campaign` | Campaigned against {Blue \| the bandits} at {site}, and {won \| lost} | `RecordCampaignResult` |
| `search` | Searched the {World Deck \| {region} discard} | `Draw` source |
| `trade` | Traded for {amount} favor with {card} | `Gain.Favor`, suit, and the adviser or site card that paid |
| `recover` | Recovered {card} at {site} | `Move` of a relic or banner |
| `forge` | Forged {card} | the edifice card |
| `play-facedown-adviser` | Played {card} face down | the card, subject to the visibility rule: the owner and anyone who knows it see the name, others see "a card" |
| `take-wealth` | Took wealth: {amount} favor | `Gain.Favor` |
| `challenge` | Challenged {player} for {banner} | banner move |
| `place-banner-resource` | Placed {amount} {secrets \| favor} on {banner} | `Move` of counted pieces |
| `negotiation` | Negotiated with {players} | see Negotiation |
| `use-power:{id}` | Used {card}'s power | the power's card |
| `end-wake` | Ended Wake | subordinate under the turn |
| `begin-rest` | Began resting | subordinate |
| `finish-rest` | Increased supply from {before} to {after} | subordinate; supply values from the states around `GainSupply` |
| `oathkeeper` | Oathkeeper passed to {player \| the bank} | `SetOathkeeper`; subordinate |
| `setup` | (no headline; the Setup round headline covers it) | each participant's starting lines as subordinates |

Finish Rest names no phase in its line: the turn is already in Rest, and the
"Began resting" line precedes it.

Campaign gets four subordinate lines in this order: force ("Attacked with force
3: 2 warbands and 1 sacrificed"), attack roll ("Rolled attack: 2 swords, 1
skull"), defense roll ("Blue rolled defense: 1 shield"), and outcome ("Blue lost
2 warbands at Deep Woods", one per affected site or raid target). Battle plans
played appear as `Trigger` lines between the force line and the rolls.

Subordinate line templates:

| Kind | Template | Source |
|------|----------|--------|
| Decision | Kept {cards}, discarded {n} | `PartitionAnswer` with a keep-one section |
| Decision | Chose {option} | `ChooseOneAnswer` |
| Decision | Chose {options} | `ChooseManyAnswer`, names joined with commas |
| Decision | Paid {amount} {unit} | `ChooseAmountAnswer` under a payment query |
| Roll | Rolled {faces} | `RollPayload` faces, named as the rulebook names them |
| Delta | Spent {n} supply ({before} to {after}) | `SpendSupply` |
| Delta | Increased supply from {before} to {after} | `GainSupply` |
| Delta | Gained {n} favor from the {suit} bank | `Gain.Favor` |
| Delta | Gained {n} secrets | `Gain.Secrets` |
| Delta | Moved {n} warbands to {site} | `Move` of warbands |
| Delta | Drew {cards} \| Drew {n} cards | `Draw`, per the visibility rule |
| Delta | Discarded {cards} to the {region} discard | `Discard.Denizen` |
| Delta | Buried {card} | `Bury` |
| Delta | Peeked at {cards} \| Peeked at {n} relics | `SiteRelicsPeeked`, `Peek`, per the visibility rule |
| Delta | Revealed {card} | `Reveal`, `OwnedRelicRevealed` |
| Trigger | {card}: {effect} | `RecordPowerUse` followed by its batch, effect from the batch's own delta line |

The die face names in `Rolled {faces}` come from the rulebook under
`reference/`; the plan fixes the exact strings.

### Negotiation

The back-and-forth of a negotiation (proposals, counter-proposals,
acceptances) produces no lines. A negotiation produces a headline and then
either a summary or an ending line.

- **Headline**: "Negotiated with Blue, White and Yellow". The participants come
  from the negotiators `ChooseManyAnswer` step. When only one opponent was
  eligible, that step is not recorded (`NegotiationProcedure.tree` omits the
  decision), and the formatter reads the participant from
  `NegotiationDeal.eligible` on the state before the run.
- **Agreed**: the settling step's batch holds `Give` and `Peek` operations. Each
  produces one subordinate line with its own subject:
  - "Blue gave 1 favor to Yellow" from a `Give` of favor.
  - "Yellow gave {relic} to White" from a `Give` of a relic.
  - "White showed Blue {adviser}" from a `Peek` disclosure. It says "showed",
    not "revealed": a disclosure tells one player and flips nothing.
- **Declined**: "Negotiation ended by Yellow", from the `DeclineDeal` answer's
  `by` field. No settlement operations follow.

A face-down card in these lines follows the visibility rule. A viewer who may
not see it reads "facedown relic (slot 1)" or "facedown adviser (slot 3)",
where the slot is the card's 1-based position in its owner's relic or adviser
row in the state before the operation.

### Names

Names come from the same presentation projector the state projection uses:
`GamePresentationProjector.siteLabel`, `denizenLabel`, `relicLabel`, and
`edificeLabel`, which are catalog lookups. Player names come from a new
`playerLabel(PlayerId)` on the projector, extracted from the inline
`safeLabel(playerId.value)` calls in `setupPlayers` and `readyPlayers`, and
used by both the Players strip and the log. The formatter takes the projector
as a collaborator so the log and the board never disagree on a name. Seat
colour stays on the client, keyed by the `Player` span's `id`.

### Visibility

One rule decides every card span. A card is named for a viewer when
`GamePresentationProjector.identifiesCard` holds for that viewer either at the
card's source in the state before the operation, or at its destination in the
state after it. Otherwise the span shows a placeholder ("a card", "facedown
relic (slot 1)") or the line uses its count form ("Drew 3 cards", "Peeked at
2 relics"). An observer (`viewer = None`) gets the same rule with no player.

The rule covers every case the log meets:

- A site denizen discarded is public at its source, so it is named for all.
- A card drawn into a hand is identified at its destination for the hand's
  owner only.
- A peek adds the peeker's knowledge, so the peeked card is identified at its
  destination for the peeker only.
- A face-down adviser discarded is named only for its owner and for players
  who know it, judged on the state before.
- A face-down adviser played is named for its owner and for players who know
  it; everyone else reads "a card".

### Knowledge follows the card

`identifiesCard` today loses knowledge in two places. An owner's knowledge is
implicit (`player == owner`) and never recorded, so a player who gives a card
away stops being able to see it. Site peeks are stored per site in
`CardKnowledge.siteRelics`, and `identifiesCard` for a player area reads only
`heldRelics`, so a player who peeked a relic at a site loses its name once
someone takes it. Both are board bugs today, not only log concerns.

The first task of the second slice fixes this in the operation application
(`CardFaceOperations` and the move path), before any subordinate line exists:

- A card that leaves a player's area adds that player to its `heldRelics` or
  `advisers` knowledge.
- A relic that leaves a site moves every `siteRelics` peeker of it into
  `heldRelics`.
- A card that enters a deck or the reliquary clears every player's knowledge
  of it.

`knowledge` is derived during replay and never journaled, so no migration is
needed: existing games re-derive the fuller knowledge on load. A follow-up on
the roadmap ("key `CardKnowledge` by card id") replaces this per-move
maintenance with a structure where knowledge cannot be left behind.

## Wire contract

The wire types live in `shared/src/main/scala/oathdigital/protocol/`, beside
`GameProjectionDto`, so the server and the Scala.js client compile the same
definitions.

```scala
final case class LogEntryWire(sequence: Long, ordinal: Int, kind: String,
    depth: Int, spans: Vector[LogSpanWire])

final case class LogSpanWire(kind: String, // text | player | card | site | amount
    text: String,                           // display text for every kind
    id: Option[String] = None,              // player, card, site
    value: Option[Int] = None,              // amount
    unit: Option[String] = None)            // amount

final case class LogPageWire(gameId: String, after: Long, nextSequence: Long,
    entries: Vector[LogEntryWire])
```

`text` is always present so a client that ignores span kinds still renders
a sentence. Kind strings are the lower-case enum names. A hidden card is a
`text` span, never a `card` span with its id withheld, so the wire carries no
hidden card id in any field.

## Route

Trusted seat routes refuse query strings (`noQuery`), so the cursor rides
the path:

```
GET /games/{gameId}/api/log/{after}
```

`after` is a journal sequence; the page holds every entry with
`sequence >= after`, formatted for the authenticated seat. `after = 0` is
the whole log. The response carries `nextSequence` so the client can poll
with it next time, the same cursor the state projection already carries.
Anything outside `0..nextSequence` is a 400 `malformed`. The route uses
the same `oath_seat` cookie authentication and private headers as the
projection route.

The development routes keep their raw event log unchanged. No public or
trusted route ever exposes it.

### Cost

The route scans the whole prefix on every request, as `load` already folds
the whole journal. A page after sequence `n` still needs the fold from 0 to
name things and judge visibility. Because the client asks only when
`nextSequence` has advanced, that cost is paid once per command per seat, not
per poll. This is acceptable for an alpha with journals in the low thousands
of events; the six-player fixture has 1053. The place to cache, when needed,
is a per-game `Vector[LogEntry]` for the public formatting plus per-seat
overlays; this design does not add it.

## Client

### Pane

The Log pane renders the tail of the log at the pane floor: 11px Ink, no
monospace. Depth 1 lines indent by one card gutter and use Ink Dim. Round and
Victory headlines use the `replay` green; Turn headlines carry the player's
seat color on the name span. Action headlines are Ink at the pane floor, in
the Headline weight.

The current turn headline sticks to the pane's top edge while its lines
scroll under it, so the reader always knows whose turn the visible lines
belong to.

The pane sticks to the bottom: when the reader is within a few pixels of the
end and new entries arrive, it stays at the end. When the reader has scrolled
up, new entries do not move the view; a small "New" chip appears at the
pane's bottom edge and returns the view to the end on click. The chip uses the
Control face.

The stripe texture and the placeholder paragraph go. Before `GameStarted`,
the pane shows the single headline "Setup".

### Divider

On load, the client reads `oath.log.seen.{gameId}.{seatId}` from
`localStorage`. If it holds a sequence lower than the last entry's, the client
inserts a client-only line "Since you last looked" before the first entry
with a higher sequence, and scrolls the pane so that line sits at the top.
Otherwise it opens at the bottom. The marker is written whenever the pane or
the overlay has been scrolled to the end for one second, and on page unload.
An observer seat writes no marker and shows no divider. Reads and writes are
wrapped so a blocked store degrades to "no divider". This is the first
`localStorage` use in the frontend's main code; the jsdom harness already
clears the store between tests.

### Overlay

Clicking the pane heading, or pressing Enter on it, opens a full-height
overlay over the table, the same idiom as `CardInspectionOverlay`: dim scrim,
Pane fill panel, `role="dialog"`, Escape closes and returns focus to the
heading. The overlay renders the whole log with round headlines sticky at its
top. It opens scrolled to the same position the pane shows, divider included.
The pane heading gets `role="button"` and `aria-expanded`.

### Accessibility

The list is `role="log"` with `aria-live="polite"`. Headlines inside the
overlay are headings (`h3` for rounds, `h4` for turns) so a screen reader can
jump between them; in the pane they are plain list items so the table's
heading outline stays short. Every line's `title` is its sequence and ordinal.

### Fetching

The client adds no timer of its own. Whenever it receives a state projection,
from the `SnapshotPollingCoordinator` timer, a command response, or a session
load, and that projection's `nextSequence` is higher than the log's, it
requests the log route with the log's last `nextSequence` and appends. It
never re-requests entries it has. A stale-position rejection on a command
does not touch the log. On a session change the log resets with the rest of
the table.

## Delivery

Three vertical slices, each shippable on its own:

1. **Headlines end to end.** `EventReplayEngine.scan`; the formatter with the
   `OathEvent` and `ProcedureRef` matches, run anchoring, and round, turn,
   action, and victory headlines only; `playerLabel`; the `BeginTurn` doc
   fix; the visibility rule, applied to headline card spans; wire types,
   codec, and route; the pane rendering entries and the fetch rule; the leak
   test over headlines. The rule is safe before the knowledge fix: that fix
   only adds knowledge, so without it the log can hide a name a player
   should see, never show one they should not.
2. **Lines and knowledge.** Knowledge follows the card (first task); every
   subordinate template; negotiation; golden tests; the leak test extended to
   every line.
3. **Reading aids.** The overlay, the divider and its marker, the "New" chip,
   and the sticky turn headline.

## Scope

Out of scope, on purpose:

- Click-to-highlight of sites or cards on the map.
- Filters, collapsing, and search.
- Timestamps. The journal records none; adding them is a durable wire change
  for another decision.
- A server-side read marker.
- Replay navigation. The stable `(sequence, ordinal)` keys are the hook for
  it; nothing else is built here.
- Caching the formatted log.
- Failed victory checks. The journal records only victories; logging a failed
  check would need a new event. The roadmap's "victory checks" is met by the
  victory headlines.
- Negotiation proposals and counter-proposals.
- Removing `DeltaMeaning`.
- Re-keying `CardKnowledge` by card id (a roadmap follow-up).

Untouched: the Actions pane, the map, the Players strip beyond `playerLabel`,
the development raw event log.

## Testing

Server:

- **Fixture journal.** The completed six-player game
  `manual-1790205747051-112090` (1053 events, current walker vocabulary,
  ending in `gameplay.usurper-victory`) is extracted once, by a read-only JDBC
  query against a copy of `var/oathdigital`, into
  `src/test/resources/journals/six-player-usurper.json`. The first task proves
  it replays under the current code. It then doubles as a replay-drift guard:
  a later journal wire change migrates this fixture with everything else.
- **Coverage.** A test lists which `ProcedureRef` keys the fixture exercises.
  Each key it misses (and a declined negotiation) gets a small scripted journal
  driven through `GameApplicationService`, not a hand-built event vector.
- **Golden tests.** The exact `LogEntry` vectors for the fixture and each
  scripted journal, for the public viewer and for the acting seat.
- **Viewer agreement.** For every journal, every viewer receives the same
  entry keys; spans differ only where a `Card` span becomes a placeholder or a
  count.
- **Leak test.** Across every journal, the public formatting contains no
  `Card` span that fails the visibility rule for `viewer = None`, and no wire
  field carries a hidden card's id.
- **Knowledge.** A giver still identifies a given face-down relic; a site
  peeker still identifies a relic after another player takes it; a buried or
  reshuffled card is identified by nobody.
- **Scan.** `scan(events).last.after == replay(events)` over the fixture.
- **Route.** Cookie auth, `after` bounds, `nextSequence` echo, observer
  formatting, and that the development raw log is unchanged.

Frontend (jsdom, munit):

- Rendering of each kind at each depth, span colouring, sticky turn headline
  structure.
- Stick-to-bottom versus "New" chip on append.
- Divider placement and marker read/write with a fake store, including a
  throwing store.
- Overlay open, close, focus return, and `aria-expanded`.
- Fetching only when the projection's `nextSequence` advanced.

## Resolved decisions

The original design left three decisions for the plan. All are settled:

- Finish Rest shows the supply values: "Increased supply from 2 to 5".
- Die face names come from the rulebook; the plan fixes the strings.
- A negotiation's terms are neither the proposer's subordinates nor headlines
  of their own: only the settlement or the ending is logged (see Negotiation).

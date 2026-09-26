# Game Log: A Player-Facing Action History

> Status: design confirmed 2026-09-25 from an Impeccable shape pass over the
> table's Log pane. It covers the server-side formatter, the wire contract, the
> seat route, and the pane and overlay that read them. No implementation is
> authorized by this document alone; a plan follows it.

## Why now

The Log pane has read "Game history will appear here." since the Arcs-panel
slice reserved it (2026-09-08). The 2026-09-25 critique scored Visibility of
System Status and Recognition Rather Than Recall at 2/4 each, and named the
dead pane its second P1: PRODUCT.md's third principle, "serve the cold return",
has no surface. The roadmap phase "Player-facing action history" already
describes the shape of the answer: a typed semantic formatter over
authoritative event batches, grouped into player actions, with public and
player-scoped projections that never leak hidden information.

This design settles the six decisions that phase left open and turns them into
a contract a builder can implement without inventing anything.

## Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Reading direction | Chronological, newest at bottom, sticks to bottom while already there. |
| 2 | Granularity | One headline per round, per turn, and per action; subordinate lines beneath an action. |
| 3 | Room | The 16% pane row stays. Clicking the pane heading opens a full-height overlay with the whole log. |
| 4 | Last-looked marker | Client side, `localStorage`, keyed by game and seat. Not on the server. |
| 5 | Hidden information | Resolved on the server per viewer before any text exists. Owners see names; others see counts. |
| 6 | Entry shape | Typed spans, not a string: text, player, card, site, and amount refs. |

## Vocabulary

- **Entry**: one line of the log, at one depth, with a stable `sequence`.
- **Headline**: an entry at depth 0. Round, turn, action, and victory entries are headlines.
- **Subordinate line**: an entry at depth 1 under an action: a decision, roll, resource delta, or triggered power.
- **Span**: one piece of an entry's text. Either plain text or a typed reference.
- **Viewer**: the seat the log is formatted for, or nobody for an observer.
- **Divider**: the client-only line "Since you last looked" placed before the first entry newer than the stored marker.

## The formatter

### Input

The formatter reads the journal the application already folds for state:
`Vector[RecordedEvent[OathEvent]]` in sequence order. It also needs the
state at each action boundary to name things the events only reference by
id, so it runs inside the same fold `GameApplicationService.reconstruct`
performs, or over a replayed prefix. It never reads the raw JSON envelope.

It reads operations, not `DeltaMeaning`. `DeltaMeaning` falls back to an
operation's class name for every batch that is not a dice-pool change, a
supply spend, or a relic acquisition (`ProcedureWalker.deltaMeaning`), which
is too little to narrate from. The 2026-09-24 retention decision kept the
field for this phase; this phase concludes the operations themselves are the
better source, and the retention decision may be revisited after the
formatter ships.

### Output

```scala
final case class LogEntry(
    sequence: Long,        // journal sequence of the event that produced it
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

`(sequence, ordinal)` is the stable identity of an entry. One journal event
may produce several entries: a `walker.step-recorded` whose batch both spends
supply and moves warbands yields two subordinate lines with ordinals 0 and 1.
Sequence references stay stable across replays, which the roadmap requires
for later replay navigation.

The formatter is a pure function of the journal prefix and the viewer:

```scala
def format(events: Vector[RecordedEvent[OathEvent]], viewer: Option[PlayerId])
    : Vector[LogEntry]
```

Two viewers may receive different entries for the same sequence. A test
asserts that the public formatting is a projection of every player-scoped
formatting: every entry the public sees, every player sees, and a player's
extra knowledge only ever replaces a count with names.

### Grouping

The journal carries no turn or round event as such, so boundaries come from
the operations and walker events already recorded:

| Boundary | Signal | Entry |
|----------|--------|-------|
| Game start | `setup.game-started` | Round headline "Setup", then the setup procedure's lines as subordinates |
| Round | `gameplay.round-ended` | Round headline "Round n" for `nextRound` |
| Turn | `BeginTurn(player, phase)` operation inside a Finish Rest step | Turn headline "Red's turn" |
| Action | `walker.parked` or `walker.completed` whose `procedure` is an `ActionRef` | Action headline; subordinates are the step lines between the previous boundary and this one |
| Phase transition | `walker.completed` with a `PhaseTransitionRef` | Subordinate line under the current turn: "Ended Wake", "Rested" |
| Triggered | `walker.completed` with `TriggeredProcedureRef.Oathkeeper` | Subordinate line: "Oathkeeper passes to Blue" |
| Victory | `gameplay.usurper-victory`, `gameplay.vision-victory`, `gameplay.war-exhaustion-resolved` | Victory headline |

The formatter buffers step events until the boundary that names their
procedure arrives, then emits the action headline first, followed by its
subordinates in journal order. A walker that parks mid-action emits the
headline at the first park, so a decision the log shows as pending sits under
its action, and the lines after the resume attach to the same headline. A
second park of the same procedure adds no second headline.

Actions that record no steps before completing still get a headline.

### Verbs

Headlines omit the subject under a turn headline. The subject appears when the
actor differs from the turn's player: a defender's choices, a bandit refill, an
oathkeeper trigger.

| `ProcedureRef` key | Headline | Detail source |
|--------------------|----------|---------------|
| `muster` | Mustered {amount} warbands at {site} | `Gain.Warbands` amount; pawn site |
| `travel` | Travelled to {site} | destination from the pawn `Move` |
| `campaign` | Campaigned against {Blue \| the bandits} at {site}, and {won \| lost} | `RecordCampaignResult` |
| `search` | Searched the {World Deck \| {region} discard} | `Draw` source |
| `trade` | Traded for {amount} favor with {card} | `Gain.Favor`, suit, and the adviser or site card that paid |
| `recover` | Recovered {card} at {site} | `Move` of a relic or banner |
| `forge` | Forged {card} | the edifice card |
| `play-facedown-adviser` | Played a card face down | never names the card, for anyone. The owner already sees it on the board |
| `take-wealth` | Took wealth: {amount} favor | `Gain.Favor` |
| `challenge` | Challenged {player} for {banner} | banner move |
| `place-banner-resource` | Placed {amount} {secrets \| favor} on {banner} | `Move` of counted pieces |
| `negotiation` | Negotiated with {player} | terms as subordinates |
| `use-power:{id}` | Used {card}'s power | the power's card |
| `end-wake` | Ended Wake | subordinate under the turn |
| `begin-rest` | Began resting | subordinate |
| `finish-rest` | Rested: supply {n} | subordinate; supply after `GainSupply` |
| `oathkeeper` | Oathkeeper passes to {player \| the bank} | `SetOathkeeper`; subordinate |
| `setup` | (no headline; the Setup round headline covers it) | each participant's starting lines as subordinates |

Campaign gets four subordinate lines in this order: force ("Force 3: 2 warbands
and 1 sacrificed"), attack roll ("Attack: 2 swords, 1 skull"), defense roll
("Defense: 1 shield"), and outcome ("Blue lost 2 warbands at Deep Woods",
one per affected site or raid target). Battle plans played appear as
`Trigger` lines between the force line and the rolls.

Subordinate line templates:

| Kind | Template | Source |
|------|----------|--------|
| Decision | Kept {cards}, discarded {n} | `PartitionAnswer` with a keep-one section |
| Decision | Chose {option} | `ChooseOneAnswer` |
| Decision | Chose {options} | `ChooseManyAnswer`, names joined with commas |
| Decision | Paid {amount} {unit} | `ChooseAmountAnswer` under a payment query |
| Roll | Rolled {faces} | `RollPayload` faces, named as the rulebook names them |
| Delta | Supply {before} to {after} | `SpendSupply`, `GainSupply` |
| Delta | Favor +{n} from the {suit} bank | `Gain.Favor` |
| Delta | Secrets +{n} | `Gain.Secrets` |
| Delta | {n} warbands to {site} | `Move` of warbands |
| Delta | Drew {cards} \| Drew {n} cards | `Draw`, scoped |
| Delta | Discarded {cards} to the {region} discard | `Discard.Denizen` |
| Delta | Buried {card} | `Bury` |
| Delta | Peeked at {cards} \| Peeked at {n} relics | `SiteRelicsPeeked`, scoped |
| Delta | Revealed {card} | `Reveal`, `OwnedRelicRevealed` |
| Trigger | {card}: {effect} | `RecordPowerUse` followed by its batch, effect from the batch's own delta line |

Every operation the formatter does not recognise produces no line. The
formatter never emits an operation's class name. A test enumerates every
`CoreOperation` case and asserts each is either mapped or listed in an explicit
`silent` set with a comment saying why (for example `EnterPhase`, which the
phase-transition headline already covers, and `AdvanceVisionsDrawn`).

### Names

Names come from the same presentation projector the state projection uses:
`GamePresentationProjector.siteLabel`, `denizenLabel`, `relicLabel`,
`edificeLabel`, and the participant's `displayName`. The formatter takes the
projector as a collaborator so the log and the board never disagree on a name.

### Visibility

Scoping happens once, in the formatter, per span. The rule, from the brief:

- The viewer sees their own draws, peeks, and hand contents by name.
- Everyone else sees the count: "Drew 3 cards", "Peeked at 2 relics".
- A face-down adviser play names no card for anyone.
- Discards, Search reveals, relic recoveries, and everything on the board are
  public and named for all.
- An observer (`viewer = None`) receives the public formatting.
- Any card whose identity the state projection would hide from this viewer
  (`GamePresentationProjector.identifiesCard` returns false) is hidden here
  too. The two use the same predicate.

A test feeds a Search with a hidden draw through both a viewer-scoped and a
public format and asserts the public one contains no `Card` span the state
projection would have hidden.

## Wire contract

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
a sentence. Kind strings are the lower-case enum names.

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
the same cookie authentication and private headers as the projection route.

The development routes keep their raw event log unchanged. No public or
trusted route ever exposes it.

### Cost

The formatter runs over the whole prefix on every request, as `load` already
folds the whole journal. A page after sequence `n` still needs the fold from
0 to name things. This is acceptable for an alpha with journals in the low
thousands of events. The place to cache, when needed, is a per-game
`Vector[LogEntry]` for the public formatting plus per-seat overlays; this
design does not add it.

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
wrapped so a blocked store degrades to "no divider".

### Overlay

Clicking the pane heading, or pressing Enter on it, opens a full-height
overlay over the table, the same idiom as the card zoom: dim scrim, Pane fill
panel, Escape closes and returns focus to the heading. The overlay renders
the whole log with round headlines sticky at its top. It opens scrolled to
the same position the pane shows, divider included. The pane heading gets
`role="button"` and `aria-expanded`.

### Accessibility

The list is `role="log"` with `aria-live="polite"`. Headlines inside the
overlay are headings (`h3` for rounds, `h4` for turns) so a screen reader can
jump between them; in the pane they are plain list items so the table's
heading outline stays short. Every line's `title` is its sequence and ordinal.

### Polling

The client polls the log route with the `nextSequence` it last received
whenever the state projection's `nextSequence` advances, and appends. It
never re-requests entries it has. A stale-position rejection on a command
does not touch the log. On a session change the log resets with the rest of
the table.

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

Untouched: the Actions pane, the map, the Players strip, the development raw
event log.

## Testing

Server:

- Formatter golden tests over the existing journal fixtures, one per action
  kind, asserting the exact `LogEntry` vectors for a public viewer and for
  the acting seat.
- The exhaustiveness test over `CoreOperation` cases described under Verbs.
- The leak test described under Visibility.
- Route tests: cookie auth, `after` bounds, `nextSequence` echo, observer
  formatting, and that the development raw log is unchanged.

Frontend (jsdom, munit):

- Rendering of each kind at each depth, span colouring, sticky turn headline
  structure.
- Stick-to-bottom versus "New" chip on append.
- Divider placement and marker read/write with a fake store, including a
  throwing store.
- Overlay open, close, focus return, and `aria-expanded`.

## Open decisions for the plan

- Whether `finish-rest` shows the supply value or the gain ("+3 supply"). The
  brief recommends the value, since the board shows the value.
- The exact rulebook names for die faces in `Rolled {faces}`.
- Whether a Negotiation's terms are subordinates of the proposer's action or
  a headline of their own when the other player answers on a later sequence.
  The brief recommends subordinates under the proposer's action, with the
  answer as a Decision line whose subject is the answering player.

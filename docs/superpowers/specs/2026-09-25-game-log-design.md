# Game Log: A Player-Facing Action History

> Status: design confirmed 2026-09-25 from an Impeccable shape pass over the
> table's Log pane, then amended twice on 2026-09-26: first by a full-feature
> design pass that checked the server half against the code, then by a
> planning pass that found most actions park before their details exist. The
> second amendment replaces action headlines with lines posted once their
> facts are complete. It covers the server-side formatter, the state change it
> needs, the wire contract, the seat routes, and the pane and overlay that read
> them. No implementation is authorized by this document alone; a plan follows
> it. Amended 2026-09-26 by the second slice's plan and its delivery: rolls
> are drawn as dice, and the wording and anchors that slice fixed are
> recorded under "Resolved decisions". Amended 2026-09-26 by the third
> slice's plan: where the marker, the divider and the overlay's position come
> from is recorded under "Resolved decisions".

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
| 2 | Granularity | Headlines for rounds, turns, phases (Wake, Act, Rest) and victories only. Everything else is a flat line under the current phase. |
| 3 | Room | The pane row is 24% of the table (amended 2026-09-27 from 16%, see "Resolved decisions"). Clicking the pane heading opens a full-height overlay with the whole log. |
| 4 | Last-looked marker | Client side, `localStorage`, keyed by game and seat. Not on the server. |
| 5 | Hidden information | Resolved on the server per viewer, per card, from the game state on each side of the operation. Card backs are public. |
| 6 | Entry shape | Typed spans, not a string: text, player, card, site, amount, and cost refs. |
| 7 | State per event | A new `EventReplayEngine.scan` exposes the state before and after every event. The log and the board share one fold. |
| 8 | Entry provenance | Every entry traces to exactly one journal event. One event may produce several entries. |
| 9 | Unknown input | Compile-time exhaustive matches over `OathEvent`'s sealed cases and `ProcedureRef`. Operations inside a step use an allow-list; an unlisted operation is silent. |
| 10 | Voice | Past-tense verbs throughout, subject omitted under the actor's own turn. |
| 11 | Knowledge | A player who knew a card keeps knowing it when the card moves. Fixed in game state, so the board benefits too. |
| 12 | Delivery | Three vertical slices (see Delivery). |
| 13 | When a line is posted | At the event where its facts are complete. A line is never posted thin and never changes once sent; the client only appends. |
| 14 | Start lines | Every action that can take modifiers opens with a start line, naming the chosen modifiers when there are any. |
| 15 | Supply | Supply an action spends rides on its start line as a distinct cost span, not a line of its own. |
| 16 | Test journals | Scripted journals built through `GameApplicationService` on every run. No stored game is a golden fixture. |

## Vocabulary

- **Entry**: one line of the log, at one depth, with a stable `(sequence, ordinal)` key.
- **Headline**: an entry at depth 0. Round, turn, phase, and victory entries are headlines.
- **Line**: an entry at depth 1. Every entry that is not a headline is a line, in journal order under the current turn.
- **Start line**: the line that opens an action that can take modifiers.
- **Span**: one piece of an entry's text. Either plain text or a typed reference.
- **Viewer**: the seat the log is formatted for, or nobody for an observer.
- **Run**: every journal event one walker records for one procedure, from its first event to its `walker.completed`, across any number of parks.
- **Segment**: the part of a run one command appends, ending at a `walker.parked` or `walker.completed`.
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

def scan(events: Iterable[RecordedEvent[E]])
    : Either[EventReplayFailure[V], Vector[ReplayStep[S, E]]]
```

`replay` becomes the last step's `after` (or the initial state for an empty
journal), so the two cannot drift. `GameApplicationService.reconstruct` keeps
calling `replay`.

For a `walker.step-recorded` event whose batch holds several operations, the
formatter steps through the batch from `before`, applying each operation with
the same operation application replay uses (`WalkerReplay.executeRecorded`,
exposed one operation at a time). Every line and every visibility judgement
is therefore made against the state immediately before and after the
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
    depth: Int,            // 0 headline, 1 line
    spans: Vector[LogSpan])

enum LogKind { case Round, Turn, Action, Decision, Roll, Delta, Trigger, Victory }

sealed trait LogSpan
object LogSpan:
  final case class Text(value: String) extends LogSpan
  final case class Player(id: String, name: String) extends LogSpan
  final case class Card(id: String, name: String) extends LogSpan
  final case class Site(id: String, name: String) extends LogSpan
  final case class Amount(value: Int, unit: String) extends LogSpan
  final case class Cost(value: Int, unit: String) extends LogSpan
```

`(sequence, ordinal)` is the stable identity of an entry, and entries are
emitted in that order. Display order equals key order, so the client only ever
appends. One journal event may produce several entries: a
`gameplay.round-ended` yields a round headline and the next turn's headline.
Sequence references stay stable across replays, which the roadmap requires for
later replay navigation.

The formatter is a pure function of the scanned journal prefix and the viewer:

```scala
def format(steps: Vector[ReplayStep[OathState, OathEvent]],
    viewer: Option[PlayerId]): Vector[LogEntry]
```

Two viewers may receive different spans for the same entry. They always receive
the same entries with the same keys; only a card reference may differ, between
a name and its card back.

### Posting

A line is posted at the event where its facts are complete, and is built only
from that event, the events before it, and the rest of its own segment. The
rest of a segment is safe to read because one command appends a whole segment
in one transaction (`HsqldbEventStreamRepository` appends a command's events
atomically), so any journal prefix a client can observe ends on a segment
boundary. That look-ahead is how a line learns which procedure its operations
belong to: `walker.step-recorded` carries no procedure, and only the segment's
closing `walker.parked` or `walker.completed` names it.

Nothing is ever posted thin. An action that parks before its details exist
posts nothing about them until they do: Place Banner Resource says nothing
until its placement, then posts "Placed 3 favor on People's Favor". Progress
on an action that is still parked belongs to the table's waiting message, not
to the log (see Scope).

The consequence, and the property the tests hold the formatter to: formatting
a prefix that ends on a segment boundary yields exactly the entries the whole
journal yields below that boundary. Nothing already sent changes, so the
client only ever appends.

### Exhaustiveness

- The formatter matches `OathEvent` exhaustively at compile time. Each case
  either produces entries or is named in a `silent` branch with a comment
  saying why (for example `IgnoredRulesRecorded`, a diagnostic).
  `WalkerEvent` is an open trait (`GameEventProtocol.scala`), so its three
  known cases are matched by name and an unknown walker event is silent, the
  same fallback `OathRules.evolve` uses.
- Lines for a walker run match `ProcedureRef` exhaustively. A new action or
  phase transition fails compilation until it has a decision.
  `use-power:{id}` is the one open-ended key.
- Operations inside a step batch go through an allow-list: the operations
  mapped below produce lines, and every other operation produces none. There
  is no exhaustiveness test over `CoreOperation`; most of its cases
  (`EnterPhase`, `Decide`, `Sequence`, `ModifyDicePool`, and so on) have
  nothing to say to a player.
- The formatter never emits an operation's or event's class name.

### Headlines

| Headline | Signal | Entry |
|----------|--------|-------|
| Setup | `setup.game-started` | "Setup" |
| Round 1 | The Setup procedure's closing `BeginTurn(player, Wake)`, recognised by the phase before it being `Setup` | "Round 1", then that player's turn headline and "Wake" |
| Round n | `gameplay.round-ended` with `nextRound = Some(n)` | "Round n", then the turn headline for the round's first player (the active player after the event) and "Wake" |
| Turn | Any other `BeginTurn(player, Wake)` | "Red's turn", then "Wake" |
| Phase | `EnterPhase(Act)` or `EnterPhase(Rest)` from another phase | "Act" or "Rest" |
| Victory | `gameplay.usurper-victory`, `gameplay.vision-victory`, `gameplay.war-exhaustion-resolved` | See below |

Finish Rest records `BeginTurn(firstPlayer, RoundEnd)` before the round-end
event, and `gameplay.round-ended` then moves that same player into Wake
(verified against the six-player journal: sequence 321 `begin-turn` into
`round-end`, 322 `walker.completed`, 323 `gameplay.round-ended`). A
`BeginTurn` into `RoundEnd` therefore posts nothing, and the round-end event
posts both headlines, so the round headline always precedes the turn it
opens. After round eight, `nextRound` is `None`: no round or turn headline,
and the victory headline follows.

A Wake that posted no line reads "Nothing happened in Wake" before the Act
headline. The rule reads only the entry posted last, so it is prefix-stable.

Victory headlines:

- `UsurperVictory(p)` and `WarExhaustionResolved(p, Usurper)`: "Pink won as the Usurper".
- `VisionVictory(p, v)` and `WarExhaustionResolved(p, Visionary, Some(v))`: "Pink won with {vision}".
- `WarExhaustionResolved(p, Oathkeeper)`: "Pink won as the Oathkeeper".
- `WarExhaustionResolved(p, RandomSelection)`: "Pink won by random selection".

The `BeginTurn` doc comment in `CoreOperations.scala`, which says Finish Rest
is the only procedure that declares it, is corrected: Setup declares it too.

### Start lines

The nine actions whose `WalkerProcedureRegistry` entry declares a
`modifierWindow` open with a start line; the others (take wealth, negotiation,
place banner resource, use power) do not.

| Action | Start line |
|--------|------------|
| search | Started Search |
| play-facedown-adviser | Playing Facedown Adviser |
| recover | Started Recover |
| forge | Started Forge |
| travel | Started Travel |
| muster | Started Muster |
| trade | Started Trade |
| challenge | Started Challenge |
| campaign | Started Campaign: {Raid \| Conquest} against {Blue \| the bandits} |

- **Modifiers.** When the player chose modifiers, the line ends
  " with Gambling Hall and Wandering Flame". A run that parks carries the
  player's selection on every `walker.parked`. A run that never parks records
  no selection, so its modifiers are read from its steps' `contributions`,
  keeping only powers whose `resolution` is not automatic. Each modifier is
  named by its source card, the same name the modifier picker shows.
- **Supply.** The Supply the action has spent by the start line's event rides
  on it as a `Cost` span ("−2 Supply"), drawn distinctly rather than behind a
  separator. No span when nothing was spent.
- **Anchor.** The start line is posted at the run's first event, with these
  exceptions, each because its facts complete later:
  - Muster and Trade pay their Supply after the source decision, so their
    start line is posted at the cost step.
  - Campaign's start line names its kind and defender, so it is posted at
    the first event by which both are settled. The kind and defender
    decisions are asked only when there is a choice, so the plan fixes where
    the formatter reads each one when its decision was not asked.
- **Recover** spends Supply again each time the player continues rolling. Each
  such spend posts "Continued Recover" with its own `Cost` span.

### Action lines

Posted at the event named in the last column. The subject is omitted under
the actor's own turn.

| Action | Line | Posted at |
|--------|------|-----------|
| travel | Travelled to {site} | the pawn `Move` |
| search | Drew {cards} from the {World Deck \| Provinces discard} and kept {cards} | the keep/discard answer, or the run's completion when only one card was drawn |
| play-facedown-adviser | Played {card} as an adviser \| Revealed {Vision}, for a Vision played faceup \| Played {card} to {site} \| Discarded {card} | the placement step |
| muster | Mustered {n} warbands with {card} | `Gain.Warbands`; the card from the earlier cost step |
| trade | Traded with {card} for {n} {favor \| secrets} | `Gain.Favor` or `Gain.Secrets`; the card from the earlier cost step |
| take-wealth | Took 1 {favor \| secret} from {site} | the `Take` |
| recover | Recovered {relic} at {site} | the relic `Move` |
| recover (failed) | Failed to recover at {site} | the run's completion without a relic move |
| forge | Placed favor on {cards} and secrets on {cards} | the payment step; an empty half is left out |
| forge | Forged {relic} | the relic `Play` from the relic deck |
| challenge | Took {banner} from {Blue \| the bank} with {n} {favor \| secrets} | the banner custody `Move` |
| place-banner-resource | Placed {n} {favor \| secrets} on {banner} | the `Move` onto the banner |
| campaign | {Blue} wins! | `RecordCampaignResult` |
| negotiation | Negotiated with Blue and White | the settlement step |
| negotiation (declined) | Negotiation ended by Yellow | the `DeclineDeal` answer |
| use-power:{id} | Used {card} | the first step recording one of the power's own effects, after any payment; the run's completion if it records none |
| oathkeeper | Oathkeeper passed to {Blue \| the bank} | `SetOathkeeper` |
| finish-rest | Increased supply from {before} to {after} | `GainSupply`; values from the states around it |

- A Recover that succeeds with no relic available posts no action line.
- Forge plays the top of the relic deck face down into the forger's area. The
  relic is named to the forger and anyone who knows it; others read "a Relic".
- End Wake and Begin Rest post nothing: the phase changes are visible from the
  lines around them.
- `use-power:{id}` names the power's source card, found from the power id
  through the catalog. The ideal is that a power declares its own log line
  and this generic one is only a fallback (a roadmap follow-up).
- Modifier powers used inside another action are named only on that action's
  start line.

### Setup lines

Under the Setup headline, one line per player's pawn placement ("Red placed
pawn at Green Shore") and one per adviser kept ("Red kept Tinker"). The kept
adviser is named to its owner; others read "Red kept a Denizen" or "Red kept
a Vision".

### Detail lines

These arrive with the second slice, between the start line and the action
line that closes the action.

| Kind | Template | Source |
|------|----------|--------|
| Decision | Chose {option} | `ChooseOneAnswer` not already covered by an action line or narrated by its power's notes |
| Decision | Chose {options} | `ChooseManyAnswer`, names joined with commas |
| Roll | Rolled {dice}, with " for the attack" or " for the defense" on a Campaign's pools | `RollPayload` faces, as a `dice` span |
| Delta | Gained {n} favor from the {suit} bank | `Gain.Favor` not covered by an action line |
| Delta | Gained {n} secrets | `Gain.Secrets` not covered by an action line |
| Delta | Moved {n} warbands to {site} | `WarbandsMoved`, and `Move` of warbands inside a run |
| Delta | Drew {cards} | `Draw` outside Search |
| Delta | Discarded {cards} to the {region} discard | `Discard.Denizen` |
| Delta | Buried {card} | `Bury` |
| Delta | Peeked at {cards} | `SiteRelicsPeeked`, `Peek` |
| Delta | Revealed {card} | `Reveal`, `OwnedRelicRevealed`, and a card in its owner's play area turned faceup, unless Card Play's placement answer played it faceup as an adviser (its action line tells it) |
| Trigger | {card}: {effect} | `RecordPowerUse` followed by its batch |
| Trigger | {player} became the Usurper | `UsurperFlipped` |
| Trigger | Bandits returned to {sites} | `BanditsRefilled` |

Die faces are named as the rulebook names them (`reference/Oath Combined
Rulebook.pdf`, "Attack" and "Defend"): attack faces "hollow sword", "sword",
and "two swords and a skull"; defense faces "blank", "shield", "two shields",
and "doubler".

### Campaign

Campaign carries the most lines, in this order:

1. The start line: "Started Campaign: Raid against Blue" with its cost span.
2. "Targets: {sites and pieces}".
3. "Attack Pool: {n}, Defense Pool: {n}".
4. "{attacker} activated {battle plans}".
5. "{defender} revealed {cards}", when a defending battle plan was face down.
6. "{defender} activated {battle plans}".
7. The attack result lines.
8. "{attacker} sacrificed {n} warbands".
9. The defense result lines.
10. "{winner} wins!"
11. A line of everything the winner gained, if anything, then a line of what
    the loser lost that the previous line does not already say: warbands lost,
    favor burned, banishment.

The attack and defense results move here from the action pane: once the log
carries them, the campaign result panel leaves the action pane. The second
slice fixed the wording and where each line posts:

- Lines 2 and 3 post at the force answer, read with `CampaignSetup.setup`
  and `CampaignBattle.printedDefense` exactly as the procedure reads them.
- Lines 4 and 6 are one line per plan at its choice: "Activated {plan}", or
  "Blue activated {plan}" for a player defender, and "The bandits activated
  {plan}" at the record a bandit's application leaves. A face-down plan card
  flipped at its use posts "Blue revealed {card}" at the flip, after its
  activation line.
- Line 7 is "Rolled {dice} for the attack", then "Attack: {score}" with
  " with {n} skulls" when skulls were paid. A plan that rewrites the total in
  the same segment (Outriders) is told once, as rewritten.
- Line 8 is "Sacrificed {n} warbands for an attack of {total}".
- Line 9 is "Rolled {dice} for the defense", then "Defense: {score}".
- Line 11 posts at the run's completion: "Took {relics and banners} from
  Blue" for a Raid, or "Placed {n} warbands on {site}" for a Conquest; then
  the loser's "Blue lost {n} warbands, burned {n} favor, discarded {cards},
  set aside {relics} and was sent to {site}", keeping only what happened,
  with no subject when the loser is the actor and "The bandits" for bandits.

### Negotiation

The back-and-forth of a negotiation (proposals, counter-proposals,
acceptances) produces no lines.

- **Agreed**: "Negotiated with Blue, White and Yellow", then one line per
  settlement operation with its own subject:
  - "Blue gave 1 favor to Yellow" from a `Give` of favor.
  - "Yellow gave {relic} to White" from a `Give` of a relic.
  - "White showed Blue {adviser}" from a `Peek` disclosure. It says "showed",
    not "revealed": a disclosure tells one player and flips nothing.
- **Declined**: "Negotiation ended by Yellow", from the `DeclineDeal` answer's
  `by` field. No settlement operations follow.

The participants come from the negotiators `ChooseManyAnswer`. When only one
opponent was eligible, that decision is not recorded
(`NegotiationProcedure.tree` omits it), and the formatter reads the
participant from `NegotiationDeal.eligible` on the state before the run.

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

One rule decides every card reference. A card is named for a viewer when
`GamePresentationProjector.identifiesCard` holds for that viewer either at the
card's source in the state before the operation, or at its destination in the
state after it. Otherwise it is shown by its back.

Card backs are public. A card that is not named reads as its kind: "a
Denizen", "a Vision", "a Relic". Several unnamed cards are counted by kind:
"Drew 2 Denizens and 1 Vision from the World Deck and kept a Vision". The one
exception is a face-down card in a player's row that a line must tell apart
from its neighbours, which reads by slot ("facedown relic (slot 1)"). An
observer (`viewer = None`) gets the same rule with no player.

The rule covers every case the log meets:

- A site denizen discarded is public at its source, so it is named for all.
- A card drawn into a hand is identified at its destination for the hand's
  owner only.
- A peek adds the peeker's knowledge, so the peeked card is identified at its
  destination for the peeker only.
- A face-down adviser discarded is named only for its owner and for players
  who know it, judged on the state before.
- A face-down adviser played is named for its owner and for players who know
  it; everyone else reads its back.

### Knowledge follows the card

`identifiesCard` today loses knowledge in two places. An owner's knowledge is
implicit (`player == owner`) and never recorded, so a player who gives a card
away stops being able to see it. Site peeks are stored per site in
`CardKnowledge.siteRelics`, and `identifiesCard` for a player area reads only
`heldRelics`, so a player who peeked a relic at a site loses its name once
someone takes it. Both are board bugs today, not only log concerns.

The first task of the second slice fixes this in the operation application
(`CardFaceOperations` and the move path), before any detail line exists:

- A card that leaves a player's adviser or relic row adds that player to its
  `heldRelics` or `advisers` knowledge. A move between the owner's own hand
  and play area, or out of a temporary hand, records nothing: an owner knows
  their own cards without a record.
- A relic that leaves a site moves every `siteRelics` peeker of it into
  `heldRelics`.
- A card that enters any card pile clears every player's knowledge of it: a
  deck, a regional discard, the reliquary, the set-aside relics, the
  dispossessed, or the atlas. No one at the table tracks a card inside a
  pile, so a card discarded and later drawn by another player is not named
  to the player who discarded it. (Widened from decks and the reliquary on
  2026-09-26, at the product owner's request.)

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

final case class LogSpanWire(kind: String, // text | player | card | site | amount | cost | dice
    text: String,                           // display text for every kind
    id: Option[String] = None,              // player, card, site; dice: face wire names, space-separated
    value: Option[Int] = None,              // amount, cost
    unit: Option[String] = None)            // amount, cost; dice: attack | defense

final case class LogPageWire(gameId: String, after: Long, nextSequence: Long,
    entries: Vector[LogEntryWire])
```

`text` is always present so a client that ignores span kinds still renders
a sentence. Kind strings are the lower-case enum names. A card shown by its
back is a `text` span, never a `card` span with its id withheld, so the wire
carries no hidden card id in any field.

## Routes

Trusted seat routes refuse query strings (`noQuery`), so the cursor rides
the path:

```
GET /games/{gameId}/api/log/{after}
```

`after` is a journal sequence; the page holds every entry with
`sequence >= after`, formatted for the authenticated seat. `after = 0` is
the whole log. The response carries `nextSequence` so the client can ask
with it next time, the same cursor the state projection already carries.
Anything outside `0..nextSequence` is a 400 `malformed`. The route uses
the same `oath_seat` cookie authentication and private headers as the
projection route. It has no observer mode, as the projection route has
none; observer formatting is exercised by the formatter's own tests.

The development table reads the same log through a sibling of its projection
route, `GET /api/dev/first-games/{gameId}/log/{after}?playerId=...`, with the
same `playerId` binding its other routes use. The development raw event log
is unchanged, and no public or trusted route ever exposes it.

### Cost

The route scans the whole prefix on every request, as `load` already folds
the whole journal. A page after sequence `n` still needs the fold from 0 to
name things and judge visibility. Because the client asks only when
`nextSequence` has advanced, that cost is paid once per command per seat, not
per poll. This is acceptable for an alpha with journals in the low thousands
of events. The place to cache, when needed, is a per-game `Vector[LogEntry]`
for the public formatting plus per-seat overlays; this design does not add it.

## Client

### Pane

The Log pane renders the tail of the log at 13px Ink (amended 2026-09-27
from 11px, see "Resolved decisions"), no monospace. Lines indent by one card
gutter and use Ink Dim. Round and Victory
headlines use the `replay` green; Turn headlines carry the player's seat
color on the name span. Phase headlines sit under their turn, smaller, in
Brass and a few pixels in, and do not stick. A `cost` span is set apart from the sentence in a
lighter treatment, with no separator character.

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
`localStorage` use in the frontend's main code. The jsdom harness
(`TestBrowser`) makes every storage access throw, to catch credential
storage, so the divider's suites supply a fake store instead of lifting that
trap.

### Overlay

Clicking the pane heading, or pressing Enter on it, opens a full-height
overlay over the table, the same idiom as `CardInspectionOverlay`: dim scrim,
Pane fill panel, `role="dialog"`, Escape closes and returns focus to the
heading. The overlay renders the whole log with round headlines sticky at its
top. It opens scrolled to the same position the pane shows, divider included.
The pane heading gets `role="button"` and `aria-expanded`.

### Accessibility

The list is `role="log"` with `aria-live="polite"`. Headlines inside the
overlay are headings (`h3` for rounds, `h4` for turns, `h5` for phases) so a screen reader can
jump between them; in the pane they are plain list items so the table's
heading outline stays short. Every line's `title` is its sequence and ordinal.

### Fetching

The client adds no timer of its own. Whenever it receives a state projection,
from the `SnapshotPollingCoordinator` timer, a command response, or a session
load, and that projection's `nextSequence` is higher than the log's, it
requests the log route with the log's last `nextSequence` and appends. It
never re-requests entries it has. A failed log request leaves the log as it
was and is retried at the next advancing projection. A stale-position
rejection on a command does not touch the log. On a session change, and on a
development seat switch, the log resets with the rest of the table.

## Delivery

Three vertical slices, each shippable on its own:

1. **Headlines and action lines.** `EventReplayEngine.scan`; the formatter
   with the `OathEvent` and `ProcedureRef` matches and segment look-ahead;
   round, turn, and victory headlines; start lines with modifiers and cost;
   one action line per row of the Action lines table, including Campaign's
   start line and "{winner} wins!" and Negotiation's first line;
   `playerLabel`; the `BeginTurn` doc fix; the visibility rule with card
   backs; wire types, codec, both routes; the pane rendering entries, sticking
   to the bottom, and the fetch rule; the prefix-stability and leak tests. The
   rule is safe before the knowledge fix: that fix only adds knowledge, so
   without it the log can hide a name a player should see, never show one
   they should not.
2. **Details and knowledge.** Knowledge follows the card (first task); every
   detail line; setup lines; negotiation's settlement lines; the rest of the
   campaign lines, and the campaign result panel's removal from the action
   pane; golden tests over every line.
3. **Reading aids.** The overlay, the divider and its marker, the "New" chip,
   and the sticky turn headline.

## Scope

Out of scope, on purpose:

- Showing a parked action's progress. The table's waiting message is the
  place for it (a roadmap follow-up), not the log.
- Powers declaring their own log lines (a roadmap follow-up); the generic
  "Used {card}" stands in until then.
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

Untouched: the Actions pane except the campaign result panel's removal, the
map, the Players strip beyond `playerLabel`, the development raw event log.

## Testing

Server:

- **Scripted journals.** Every journal a log test reads is built on each run
  by driving commands through `GameApplicationService` with deterministic
  ports, the way `testkit/Situation` seeds games. No stored game is a golden
  fixture: replay re-checks unimplemented-power diagnostics
  (`IgnoredRulesRecorded`) and re-derives state-based events, so a stored
  journal breaks whenever a card is implemented, for reasons that have
  nothing to do with the log. A script that a new card changes fails at the
  command that no longer applies, which names the fix.
- **Coverage.** Scripts together exercise every `ProcedureRef` key the
  formatter handles, including a declined negotiation, and a campaign.
- **Six-player smoke read.** During the first slice, the completed six-player
  game `manual-1790205747051-112090` (1053 events) is formatted once from a
  copy of `var/oathdigital` into scratch space for a person to read. Nothing
  from it is committed.
- **Golden tests.** The exact `LogEntry` vectors for each script, for the
  acting seat and for another seat.
- **Prefix stability.** For every script and every segment boundary `k`,
  formatting the first `k` events yields exactly the full formatting's entries
  below `k`.
- **Viewer agreement.** For every script, every viewer receives the same entry
  keys; spans differ only where a card reference becomes its back.
- **Leak test.** Across every script, no formatting contains a `Card` span
  that fails the visibility rule for its viewer, and no wire field carries a
  hidden card's id.
- **Knowledge.** A giver still identifies a given face-down relic; a site
  peeker still identifies a relic after another player takes it; a buried,
  reshuffled, discarded, set-aside or dispossessed card is identified by
  nobody.
- **Scan.** `scan(events).last.after == replay(events)` over every script.
- **Routes.** Cookie auth, `after` bounds, `nextSequence` echo, the
  development route's `playerId` binding, and that the development raw log is
  unchanged.

Frontend (jsdom, munit):

- Rendering of each kind at each depth, span colouring, the cost span, sticky
  turn headline structure.
- Stick-to-bottom versus "New" chip on append.
- Divider placement and marker read/write with a fake store, including a
  throwing store.
- Overlay open, close, focus return, and `aria-expanded`.
- Fetching only when the projection's `nextSequence` advanced, and not again
  for entries already held.

## Resolved decisions

The original design left three decisions for the plan. All are settled:

- Finish Rest shows the supply values: "Increased supply from 2 to 5".
- Die face names come from the rulebook (see Detail lines).
- A negotiation's terms are not logged; only the settlement or the ending is.

The 2026-09-26 planning pass settled four more:

- Headlines are rounds, turns, and victories. An action is a start line and an
  action line posted when its facts are complete, because most actions park
  before their details exist and a headline sent at the start would be thin.
  Re-sending an open action's entries was considered and rejected: entries
  would change after being read.
- Start lines are consistent: every action that can take modifiers has one,
  with or without modifiers chosen.
- Supply rides on the start line; Recover's continued rolls repeat it.
- Card backs are public, so a hidden card reads as its kind.

The second slice (2026-09-26) settled these, the first at the product
owner's choice:

- Rolls are drawn as dice: a seventh span kind, `dice`, which the pane draws
  with the same die-face chips as the table.
- Bandit battle plans are named like every other plan activation.
- A decision posts "Chose {options}" (or "Blue chose …") for a single or a
  non-empty multiple choice that no other line tells. A button reads the
  label its chooser was shown, from the parked decision rebuilt on the state
  before the answer.
- Negotiation's settlement reads "Blue gave 1 favor to Yellow", "Blue gave
  {relic} to Yellow", "White showed Blue {card}", and "Blue was shown {relic}
  at {site}" for a site relic, which names no shower.
- Search plays its kept card through Card Play, so it posts the same
  "Played … / Discarded …" line as Play Facedown Adviser.
- "{card}: {effect}" is not a separate line: only Use Power and Take Wealth
  record a power's use, and Use Power's "Used {card}" is followed by its
  effect's own lines. "Used" now also posts at the power's own decision, so
  it precedes that decision's "Chose" line.
- Reviewing the golden logs as copy changed three lines: Card Play's discard
  names its pile ("Discarded {card} to the {region} discard"), warbands that
  are not the actor's name their owner ("Moved 1 of Blue's warbands to
  {site}"; bandits read as bandits), and a sacrifice states the attack it
  makes.
- A test script's arranged favor gain posts "Gained … favor from the … bank"
  inside the End Wake run it joins. It is truthful to the journal and the
  golden logs keep it.

The third slice (2026-09-26) settled these:

- The marker holds the sequence of the last entry the client holds, written
  once a list has stayed at the end for one second. On `pagehide` the client
  writes only a mark already waiting for its second, so unload never moves
  the marker past what the reader reached. A browser that refuses storage,
  by throwing or by having none, gives no marker and no divider.
- The frontend has no observer seat, so an empty seat id, which a trusted
  table has until the server names its seat, counts as the observer: it
  reads no marker, writes none and shows no divider.
- The divider is placed only when the list is drawn in full: when a session
  loads or the log resets. Appends never add, move or remove it. The pane and
  the overlay read the marker once per seat and divide at the same entry.
- The overlay's list is a pane list too, so it has its own "New" chip.
- The list stays flat. Every turn headline in the pane is sticky, and the
  latest one past the top covers the earlier ones; the overlay does the same
  with round headlines. A sticky headline's `top` is minus the pane's
  padding, so it meets the pane's top edge.
- The pane's reading position is an index into its children: the first one,
  not a sticky headline, that shows below the stuck headline. Both lists hold
  the same children, so the overlay scrolls that child to just under its own
  stuck round headline. A pane at the end opens the overlay at the end.
- The overlay's list reads at 13px; the die chips, sized in `em`, scale with
  it.

A layout pass on 2026-09-27 amended the pane's room and size after
comparing it with the Arcs table it follows. There the log pane is about
21em wide and 9em tall in its own font, six lines like ours, but the font is
the table's body size (body `2vh`, the pane fitted from a layout unit and
raised 10%), roughly 16 to 19px on a laptop, with an "Expanded" setting that
triples the pane's height. Our overlay is that expansion; the pane itself was
the outlier at 11px in a 16% row:

- The pane row is `minmax(120px, 24%)` in the wide table layout and
  `minmax(120px, 23%)` in the narrow one: nine lines at 1440×900, seven at
  1024×768. Actions gives up the room; the one point less in the narrow
  layout is what keeps the starting-site decision out of scroll at
  1024×768. The World pane spans both right-column rows, so the map's Fit
  scale does not move.
- The pane reads at 13px, the overlay's size, one step under the Actions
  pane's 0.9rem body. The pane and the overlay now share one list size, and
  the overlay is the same list with room.
- On open, focus goes to the overlay's scrolling list, so arrow keys and Page
  Down scroll it at once; Close is one Shift+Tab away. Escape, from anywhere
  on the page, Close and a click on the scrim shut it and return focus to
  the pane heading.
- The overlay is the only dialog while it is open: an open card overlay
  closes first, and the table and the developer panel behind it are `inert`,
  so Tab cannot leave it.
- The pane heading is `role="button"` with `aria-expanded` and
  `aria-controls`; Enter and Space open the overlay. The role replaces the
  heading's own role, so the Log pane's section is named by it but the
  document outline loses that `h2`. The spec made the heading the control,
  and the overlay's own `h2` names the dialog.
- The "New" chip is a 30px Control, like the table's other compact controls,
  not a 44px target: mobile support is undecided.

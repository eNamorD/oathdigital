# Board Decisions

**Status:** designed 2026-10-03; done (plan 2026-10-04-board-decisions). Spec 1 of
two. It replaces the target
selection item of **Phase - Campaign UI** and lays the ground for spec 2,
walker-native card play through card slots, which replaces **Phase - Card
play UI**.

## Goal

A player answers a decision about something on the table by clicking that
thing on the table. Sites, site cards, advisers, relics, banners, favor banks,
decks, pawns and player boards light up when they are options. The player
selects them there, inspects them there, and sees why something is not on
offer. The Action panel keeps only the prompt, the running summary, the
buttons that are not board objects, and the Confirm.

## Rulings

- Every decision with a board option is answered on the board. There is no
  per-decision opt-in; a decision may opt out (see Protocol).
- A click on an option's card body selects it. A round "i" button in the
  card's bottom-right corner opens the card summary. On a card that is not an
  option, a body click still opens the summary.
- A board pick never submits by itself. Confirm submits it.
- A Decide button (a `Button` option such as "Finish battle plans") submits
  at once.
- A choose-many shows no "nothing more" button. Its summary line ("Target 2
  sites") and Confirm cover the empty case.
- An option is in one of four states: available, selected, locked, or
  unavailable. Locked is decided already: shown selected, with a lock, and
  not clickable. Unavailable is shown dimmed with its reason on hover.
- Board objects that are not options are drawn as they are today. Nothing is
  dimmed.
- Clear resets the selection. Cancel abandons the action and appears only
  where today's UI already offers a way back. Spec 1 adds no new way back.
- Partition, Distribute, Negotiate, Inspect and Travel's
  `BoardTargetSelectionState` are unchanged.

## Protocol

### Choose-many fields

`DecisionQuery.ChooseMany` gains three optional fields.

- `locked: Vector[DecisionOption]`: options decided already. They are not
  part of the answer, and the engine adds them as it does today. A Campaign's
  origin site (Conquest) or defender pawn (Raid), now implicit in
  `CampaignSetup.setup`, moves here.
- `tallies: Vector[Tally]`, with
  `Tally(label: String, perOption: Map[DecisionOptionRef, Int], limit: Option[Int])`.
  The client sums the values of the selected and locked options. A limit
  makes an option unavailable while selecting it would exceed the limit. A
  tally is advice: the answer-time check stays.
- `summaryNoun: Option[Noun]`, singular and plural ("site", "sites"), for the
  summary line.

### Unavailable options

`DecisionOption.Unavailable(option: DecisionOption, reason: String)` sits
beside `Priced` and `Badged`.

- The answer check rejects it as not a member, in a choose-one and a
  choose-many.
- `min`, `max` and "nothing left to offer" count available options only.
- `WalkerSearch.narrow` wraps an option it drops instead of removing it. The
  reason is the note `WalkerPowerGather.lookAheadNotes` already writes for
  that option, prefixed with the power's name. An option with no note is
  removed as today. Parked decisions are rebuilt by `narrowParked`, so no
  persisted shape changes.
- An option builder may emit `Unavailable` itself, with a reason written as
  player text. Spec 1 does this for Campaign targets, battle plans and Bandit
  Prince.

### Presentation flag

`Decide` gains `presentation: Board | Panel`, default `Board`. `Panel` keeps
a decision in the Action panel when it reads worse on the board.

### Projection

- `DecisionOptionProjection` gains `state: available | locked | unavailable`
  and `reason: Option[String]`.
- The choose-many projection gains `tallies` and `summaryNoun`, and carries
  locked options as options in the locked state.
- The walker decision projection carries `presentation`.
- `ActionProjectionCodec` encodes the new fields. Server and frontend ship
  together, so there is no compatibility shim.

There is no validator closure and no new query kind. The answer wire format
is unchanged.

## Frontend

### Routing

`ParkedDecision` routes a choose-one or choose-many with `presentation =
Board` and at least one option with a board home to `Surface.BoardDecision`.
It replaces `Surface.Board`, which served only setup pawn placement; that
decision moves onto the new surface.

### Board home

A pure rule over the world projection says where an option is drawn.

| Option ref | Board home |
| --- | --- |
| `site` | the site box |
| `denizen`, `edifice`, `relic` at a site | the card in the site's row |
| `adviser-slot`, `denizen`, `relic` on a player board | the card on that board |
| `banner` | the banner on its holder's board |
| `favor-bank` | the bank chip |
| `deck` | the pile display |
| `player` | the player board header and that player's pawns |

A ref with no element on the table, and a small marker such as the
Oathkeeper or Usurper status, stays in the Action panel as a card face or
button with the same state styles. Battle plans are adviser and site cards,
so they are on the board.

### BoardOptions

A new pure module, built from the parked decision and the selection draft.
It maps each ref to a view: state, reason, selected. Board renderers call
`BoardOptions.decorate(element, ref)`, which adds the state classes, `title`
set to the reason, `aria-pressed`, click and Enter/Space handlers, and
`data-option-ref` for hover sync.

- An option card gets the inspect button, only when the viewer knows the
  card. Its click opens `CardInspection` and stops there.
- A pawn's click stops at the pawn, as `onCard` already stops card clicks
  inside a site.
- A player option decorates both the header and the pawns.

### Selection

`WalkerBoardDraft` becomes `BoardSelectionDraft(decisionId, refs)`, keeping
today's `reconcile`. A choose-one click replaces the selection; a
choose-many click toggles. While adding an option would break `max` or a
tally limit, that option renders unavailable with a local reason, such as
"Needs 5 warbands; limit 4".

### Styles

From the mock-up approved on 2026-10-03:

- Available: brass border, today's `board-target` colour; glow on hover.
- Selected: blue border and fill, check badge at the top-left.
- Locked: selected styling with a lock badge; default cursor.
- Unavailable: dimmed, not-allowed cursor, reason on hover.
- Inspect button: round "i", bottom-right, inside the card box. The top-right
  holds defense dice and the knowable pip.

### Action panel

Top to bottom:

1. Prompt.
2. Summary line from `summaryNoun` ("Target 2 sites").
3. One row per locked and selected option, with its tally values.
4. Tally totals.
5. An unlabelled rule, then the Decide buttons, which submit at once.
6. A rule labelled "Decision", then Clear (when something is selected),
   Confirm, and Cancel where it exists.

Confirm has a distinct primary style. It is disabled, with its reason under
it, while the selection is below `min` or over a tally limit.

### Hover sync

Hovering a panel row highlights its board element; hovering a board option
highlights its row. A player's header and pawns highlight together. Sync
toggles a class on every element with the matching `data-option-ref`.

## Decisions spec 1 changes

- **Campaign targets.** The origin site or defender pawn is locked. Two
  tallies: "Defense dice (before plans and powers)" from each target's
  defense, and "Defending force" from its defending warbands. The engine
  computes both from the same sources as `CampaignBattle.defenderForce` and
  pool gathering. `summaryNoun` is "site" for a Conquest and "target" for a
  Raid. Targets not on offer carry a reason.
- **Raid defender.** A player option, picked by header or pawn.
- **Battle plans.** The engine keeps the `Repeat` of a choose-one. A survey
  of the 40 implemented plans found that one plan's application changes
  another's legality (Wrestlers and Warning Signals against Encirclement;
  Wrestlers emptying a plan's site; a revealed adviser enabling Disgraced
  Captain) and that order changes results (Great Crusade and Pledge of
  Defense counting revealed Nomads; Hospital counting only later
  sacrifices). A single choose-many with budget tallies would change game
  results, so it is out of scope. Each pass highlights the offered plan
  cards on boards and at sites; a pick plus Confirm plays one, and "Finish
  battle plans" submits at once. Plans played earlier show locked, from
  `answeredOptions`. Plans no longer offered show unavailable with a reason:
  a cost shortfall ("Costs 1 favor; you have 0"), Encirclement's force rule,
  or a surcharge.
- **Bandit Prince.** A "Warbands needed" tally, limited to the warbands in
  the player's bank, since each chosen site's bandits are replaced with the
  player's own warbands. Sites that cannot be afforded alone carry a reason.
  The answer-time sum check stays as a guard.
- **Restriction-pruned options.** Everywhere, through `narrow`.
- **Everything else** with board options moves to the board through
  routing: Hunger, Dream Thief, Silver Tongue's bank, Garrison, raid relic
  and banner targets, setup pawn placement, and the rest.

## Slices

Each slice lands green on its own.

1. **Protocol.** `Unavailable`, `locked`, `tallies`, `summaryNoun`,
   `presentation`, through the projection and the codec. No player-visible
   change.
2. **Board decisions in the frontend.** `BoardOptions`,
   `Surface.BoardDecision`, decoration of every board piece in the table
   above, the inspect button, `BoardSelectionDraft`, the panel layout and
   hover sync. Setup pawn placement moves over and generic routing turns on.
3. **Restriction reasons.** `narrow` wraps pruned options with their notes.
4. **Campaign.** Locked targets, the two tallies and target reasons; then
   battle-plan reasons and locked played plans.
5. **Bandit Prince.** The tally and site reasons.
6. **ROADMAP.** Mark the Campaign UI target item done; keep its results
   item. Rewrite Phase - Card play UI as spec 2. Add follow-ups: move Travel
   onto board decisions; add reasons to the remaining option builders as
   playtests find them.

## Testing

- Engine suites: the answer check rejects `Unavailable`; `min` and `max`
  count available options; `narrow` wraps with the right note and still
  removes options without one; Campaign suites assert locked options,
  tallies and reasons, and the battle-plan reasons; Bandit Prince suites
  assert the tally; the projection codec round-trips every new field.
- Frontend suites: `BoardOptions` alone (board-home rule, states, live tally
  limits, toggle and replace); routing, extending `BoardSurfaceSuite`; panel
  layout and Confirm states.
- Browser check on a scratch database server on port 8081, never the live
  database: a Conquest and a Raid through targets, plans and Confirm, and a
  Bandit Prince turn, with screenshots.

## Spec 2 handoff

Walker-native card play through card slots builds on this spec. Settled
already:

- Slot choice merges the discard: a site slot holding a card the player may
  replace is an option marked "replace". People's Favor discards only when
  playing to a site.
- People's Favor and Homeland together allow up to two discards; spec 2
  decides how that choice is presented.
- Playing a facedown adviser starts from the Minor Actions button; the
  player then picks the adviser on the board.
- Empty site slots and empty adviser slots need refs; today's `AdviserSlot`
  points at an occupied position.
- Card-play reasons for options not on offer belong to spec 2.
- Partition decisions keep their drag and drop.

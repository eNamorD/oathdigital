# Game Log Action Sections

**Status:** designed and implemented 2026-10-05. It answers the last item of **Phase -
Campaign UI** ("Results: make it clear what a Campaign's battle did") and
closes that phase. The battle summary the playtest also asked about is
deferred to a new **Phase - Campaign battle summary**.

## Goal

A reader of the Game Log can tell where each action starts and ends, which
action it was, and what its dice rolled. The playtest of 2026-10-01 found the
Campaign lines hard to tell apart from the lines around them, the attack and
defense dice hard to tell apart from each other, and the solid and hollow
swords hard to tell apart at log size.

## Rulings

- Every top-level action in the log is a **section**: a band drawn behind
  its lines. The list stays one flat list.
- Each major action's band has its own color. Aggressive actions are warm.
  Playing a facedown adviser takes Search's color, as a continuation of
  Search.
- A minor action, an ACTION power, a Negotiation and placing a banner
  resource get a neutral band with no tag.
- Wake's Take Wealth, phase transitions, triggered procedures (Setup,
  Oathkeeper, Forced Wake), state-based events and every headline stay
  outside any band.
- A tagged band opens with a **tag row**: the action's name in its color,
  then the Supply it cost ("SEARCH −2 Supply"). The tag replaces the start
  line. Whatever the start line said beyond the name and cost stays as a
  line under the tag; a start line that said nothing more is gone.
- Attack dice are red chips and defense dice are blue chips, everywhere dice
  are drawn.
- The hollow sword is an outlined sword. Both swords get a wider blade.
- The battle summary (a summary opened from a log line, and one that fills
  in while a Campaign runs) is out of scope.

## Colors

| Action | Band color | Family |
|---|---|---|
| Campaign | `#d47c74` | warm (aggressive) |
| Muster | `#c2b25c` | warm (aggressive) |
| Challenge | `#c99a62` | warm (aggressive) |
| Travel | `#7fb0a8` | cool |
| Search, Play facedown adviser | `#b88aae` | cool |
| Trade | `#94b58a` | cool |
| Recover | `#9f8fc0` | cool |
| Forge | `#8fa3bd` | cool |
| Neutral | `#b8ab92` | none |

A band's fill is its color mixed 9% into the Pane fill (`#211f1b`), and its
border is the color mixed 28% into it. The neutral band uses 6% and 20%. The
tag's name is the color itself. These values come from the brainstorm
mockups, except Campaign: the mockups' `#c9706a` gave its tag 4.21:1 on its
band, so it was lightened to `#d47c74` (4.80:1). The browser check may tune
them, under two constraints: every tag color clears 4.5:1 on its band, and
no two colors that commonly sit next to each other in a turn read as the
same color family. Ink Spent falls under 4.5:1 on every band fill, so a
spent cost inside a band is Ink Dim.

## Server: sections and the tag entry

### Where a section comes from

`GameLogFormatter` formats every walker event inside a `Run`
(`LogJournal.scala`), which holds the run's `procedure: ProcedureRef`, its
actor and `first`, the journal index of the run's first event. A top-level
action is one run, so the run decides the section of every entry it posts.

`LogEntry` gains `section: Option[LogSection]`, where
`LogSection(id: Long, action: String)`:

- `id` is `journal.sequence(run.first)`, the same for every entry of one
  run.
- `action` is `run.procedure.key` (`search`, `play-facedown-adviser`,
  `campaign`, `use-power:<id>`, and so on).

A new `LogSections.of(procedure: ProcedureRef): Boolean` decides whether a
run is a section at all. It is false for `ActionRef.TakeWealth`, every
`PhaseTransitionRef` and every `TriggeredProcedureRef`, and true otherwise.

Entries outside a run:

- A minor-action event (`SiteRelicsPeeked`, `OwnedRelicRevealed`,
  `WarbandsMoved`) is its own section: `id` is that event's sequence and
  `action` is `minor`.
- `BanditsRefilled`, `UsurperFlipped`, round and victory headlines, and the
  "Nothing happened in Wake" line carry no section.

Headlines never carry a section, even when a run posts them (the
`BeginTurn` and `EnterPhase` headlines in `GameLogFormatter.headlines`).

### The tag entry

`LogKind` gains `Section` (wire key `section`). A section entry is posted
where `StartLines.start` posts the start line today: at the run's first
event, except Muster and Trade (their cost step) and Campaign (the first
event by which kind and defender are both settled). Its spans are the
action's name and, when the run has spent Supply by then, the existing
`LogSpan.Cost`. Its text therefore reads "Search −2 Supply" to a client that
ignores kinds.

| Procedure | Tag name |
|---|---|
| Search | Search |
| PlayFacedownAdviser | Play facedown adviser |
| Recover | Recover |
| Forge | Forge |
| Travel | Travel |
| Muster | Muster |
| Trade | Trade |
| Challenge | Challenge |
| Campaign | Campaign |

The procedures with no start line today (Take Wealth, Place banner
resource, Negotiation, ACTION powers) get no tag.

What the start line said beyond its title and cost becomes an `Action` line
posted right after the tag:

- the chosen modifiers: "With Cup of Plenty, Fox", using the words
  `StartLines.modifiers` builds today;
- Campaign's kind and defender: "Raid against Blue", "Conquest against the
  bandits", with its modifiers after them: "Raid against Blue with Fox".

When neither applies, nothing follows the tag. Recover's continuation line
becomes "Continued" followed by its `Cost` span.

### Nothing before the tag

In a tagged run, the tag entry is the run's first entry. Power lines already
wait for the start line (`PowerLines.held`). The plan checks every other
line source that can post in a Muster, Trade or Campaign run before the
tag's anchor (`ActionLines`, `DetailLines`, `CampaignLines`) and holds what
can come first the same way. A test pins this for each of the three.

## Protocol

`LogEntryWire` gains `section: Option[LogSectionWire]`, where
`LogSectionWire(id: Long, action: String)`. `LogPageCodec` always writes
`section` (`null` when absent) and decodes it with a default of `None`.
`LogSpanWire` is unchanged. Event formats may change in place before the
first public release, so nothing is migrated.

## Client: bands

`GameLogPane` keeps one flat `ol`. The sticky headline, `position`,
`restore`, the "Since you last looked" divider and append-only rendering
stay as they are.

`GameLogPane.item` gives an entry with a section the classes `log-band` and
`log-band-<action>`, where `<action>` is the section's action, except
that `play-facedown-adviser` becomes `search` and any `use-power:<id>`
becomes `use-power`. Band edges:

- `log-band-start` goes on an entry whose section id differs from the
  previous entry's, or that follows the divider.
- `log-band-end` goes on the last entry of its section so far. When an
  appended entry continues the section of the list's last entry, the last
  entry loses `log-band-end` and the new one takes it. A redraw computes
  both edges from the whole list.

A divider inside a section splits its band into two pieces, each with its
own edges.

CSS maps actions to colors:

- The eight action colors go to their actions.
- Every other band (`minor`, `use-power`, `negotiation`,
  `place-banner-resource`) is neutral.

Each color is a custom property on its band class (`--band`), so the fill,
border and tag color derive from one value.

A `section` entry draws as the tag row:

- the name in 11px uppercase, weight 700, 0.12em tracking, in `--band`;
- the cost in 12px Ink Dim, set apart by a 0.6em margin.

The band's fill and its left and right borders run on every banded entry.
The top border and top radius go on `log-band-start`, and the bottom border
and bottom radius on `log-band-end`. Banded lines keep the log's hanging
indent inside the band. Bands keep the No-Reflow and Flat Board rules: no
shadow, and no size change on hover.

The log overlay uses the same `GameLogPane`, so it shows the same bands.

## Dice

`DieFace.roll(faces, die: Option[String])` takes the die kind. With a kind,
each chip gets `die-face-attack` or `die-face-defense`:

- a face is filled with `die-attack` (`#c0392b`) or `die-defense`
  (`#3d6fb4`), with a border of the same color lightened and its symbols in
  Bright Cream (`#fff2d6`);
- a blank face is a dashed chip with a border in the die's color, on the die
  color mixed 25% into Base.

The callers pass the kind:

- `GameLogPane` passes the dice span's `unit` (`attack` or `defense`).
- `WalkerPanelSupport.rollFeedback` maps the pool: `campaign.attack` to
  attack, and `campaign.defense` and `recover` to defense. An unknown pool
  passes `None` and keeps today's neutral chip.

## Swords

In `TokenSprite`, both swords take a wider blade:

- **Sword:** `M12 1 16 6v8H8V6zM5 14h14v3H5zm5.5 3h3v6h-3z`.
- **Hollow sword:** the same sword with every part outlined:
  `M12 1 16 6v8H8V6zm0 3.4L10 7v5h4V7zM4.5 14h15v4h-15zm1.8 1.4v1.2h11.4v-1.2zM10 18h4v5h-4zm1.4 1.2v2.6h1.2v-2.6z`.

The paths draw with the even-odd rule, as the other glyphs do. The change
reaches every use of the glyphs: dice, battle plans and card text. If the
browser check finds the hollow sword's outline blurring at log size, the
outline thickens without changing the shape.

## DESIGN.md

- A new named rule, **The Action Band Rule**. The Game Log's action bands
  are the one UI surface besides the map regions that may carry color. The
  eight action colors are warm for aggressive actions, are used only in the
  log, and never stand for a suit, player or die. The Pieces Carry the Color
  Rule names this rule as its exception.
- The action colors and the neutral band join the color tokens.
- The Dice section describes the filled chips and the redrawn swords.
- The Panes section describes the bands, the tag row and the band edges.

The `.impeccable/design.json` sidecar is out of date already; refreshing it
is not part of this work.

## Roadmap

- Delete **Phase - Campaign UI** once this work ships.
- Add **Phase - Campaign battle summary**: a summary of a Campaign's battle,
  opened by clicking one of its log lines, and the same summary filling in
  while a Campaign runs (attack and defense dice as plans are chosen,
  sacrificed warbands). It is not always visible.

## Testing

- Formatter suites:
  - which entries carry which section;
  - headlines, Wake, phase transitions and triggered procedures carry none;
  - a minor-action event is its own neutral section;
  - the tag entry replaces the start line, with and without a cost;
  - the modifiers line and Campaign's kind line follow the tag;
  - "Continued" for Recover;
  - nothing comes before the tag in Muster, Trade and Campaign.
- `LogPageCodec`: a round trip with and without a section.
- `GameLogPane`:
  - band classes on a full redraw;
  - on append, `log-band-end` moving to the new last entry;
  - a new section starting;
  - the divider splitting a band;
  - the facedown adviser band taking Search's class.
- `DieFace`: the chip classes for attack, defense and no kind; the roll
  panel's pool mapping.
- Browser check on a scratch DB server on port 8081, never the live
  database: a turn with Search, a facedown adviser, a minor action, Trade
  and a Campaign, in the pane and the overlay, at 1440×900 and 1024×768.
  Check the tag contrast, the dice and the swords at log size.

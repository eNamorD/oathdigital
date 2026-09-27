# Power Log Lines

**Status:** designed 2026-09-26. Starts after
[Rule gaps in implemented cards](2026-09-26-rule-gaps-in-implemented-cards-design.md)
merges: it needs that phase's Restriction look-ahead and the Transform that
removes Vow of Peace's sacrifice decision.

**Builds on** the [Game Log design](2026-09-25-game-log-design.md). That
design left "powers declaring their own log lines" out of scope, kept the
generic "Used {card}" as a stand-in, and reserved the detail row
"Trigger | {card}: {effect}" without building it. This phase builds it.

## Goal

Powers change what an action does without saying so in the Game Log. A phase
power used as an action logs a generic "Used {card}". A modifier is named only
on its action's start line. Automatic powers and Restrictions leave no line at
all: when Vow of Peace removes the attacker's sacrifice decision, nothing
records why.

After this phase each power can declare the lines it contributes, at the
point its effect happens, through one generic mechanism. Every implemented
power that qualifies gets its line in this phase.

## Rulings

These were settled while designing and bind the rest of this document.

1. **Each power decides.** No power gets a line by default. A power writes a
   line when its effect is one the rest of the log does not show:
   - it removes a decision or step;
   - it hides an option at a decision the player faces;
   - it adds an effect, such as "Book Binders: Blue gained 2 favor from the
     Order bank.";
   - it significantly alters the procedure, such as "Careful Plans: The
     defender must roll first!".

   A chosen modifier whose effect is only an amount or dice change stays
   named on its action's start line with no line of its own. An automatic
   cost change stays in the start line's cost span.
2. **Every line starts with the source's name.** A line reads
   "{Card}: {Sentence}". The formatter adds the name as a card or site chip.
   The power writes only the sentence.
3. **Blocked actions get no line.** A Restriction that stops a whole action
   before it starts (Vow of Peace's first sentence, the Fortress's Raid-only
   block) leaves the log untouched. Nothing happened. Explaining a missing
   control belongs to the control, not the log.
4. **A hidden option gets a line only at a decision the player actually
   faces**, and only when its power writes one.
5. **A phase power's own line replaces "Used {card}".** The operations after
   it still log as they do today, except any a note covers (see "Covering").
   "Used {card}" remains as a fallback.
6. **The journal records the fact, the power owns the wording.** The walker
   journals a structured note when the effect happens. The formatter renders
   the note's wording when it formats the log, so rewording a power's line
   also rewords it in old games.

## 1. Mechanism

### The note

`PowerNote(key: String, args: Vector[NoteArg])` lives in the gameplay model,
beside the operation types.

- `key` tells apart the lines one power can write. The key `used` is
  reserved for a phase power's action line (section 2).
- `args` are typed references, never free text, so card names still pass the
  log's knowledge rule:

| `NoteArg` | Renders as |
|---|---|
| `Player(id)` | player chip |
| `Card(ref)` | card chip, or the card's back to a viewer who may not identify it |
| `Site(id)` | site chip |
| `Amount(n, unit)` | "3 favor", "1 secret", "2 Supply", "1 warband" |
| `Bank(suit)` | "the Order bank" |
| `Dice(die, faces)` | die-face chips, as the log's `Dice` span |

### Emission path 1: the `Note` leaf

`Note(power: PowerId, source: RuleSourceRef, build: NoteStates => PowerNote,
covers: Boolean = false)` is a new leaf of the operation tree. The walker handles it the way it handles
`Decide` and `Roll`. It is not a `CoreOperation` and changes no state.

- A power puts a `Note` in any tree it builds or rewrites: a Transform's
  output, a battle plan's subtree, a phase power's `build`, a when-played
  tree, a setup rule's operations.
- When the walk reaches the leaf, the walker calls `build` and appends
  `PowerNoted(power, source, note)` to `WalkCtx.events`, in walk order.
- `NoteStates(now: ReadyGame, previous: Option[(ReadyGame, ReadyGame)])`
  gives `build` the live state, like `BuildOps`, and the states before and
  after the step this walk journaled immediately before the note. `previous`
  is empty when the note is the first step of its command.
- **Amounts are what happened, never the printed number.** Supply clamps at
  the track's maximum, a favor gain takes at most what the bank holds, and a
  kill does what the board allows. A note that restates a step reads its
  amount as the difference between `previous`'s two states, so no power
  repeats the cap logic of the operation it describes. "Magic Waterskin:
  {Red} gained {3} Supply." reads 3 when the track had room for only 3.
- A `Note` inside a branch that never runs is never journaled. A line
  therefore appears only when its effect happened.
- A Transform that removes a decision leaves a `Note` in its place. Vow of
  Peace swaps the sacrifice `Decide` for a note reading "Vow of Peace: The
  attacker cannot sacrifice against Blue."
- `source` names the card the line starts with. The setup rules, whose own
  `source` is a game rule, pass their edifice card.

### Emission path 2: the hide hook

`OptionRestriction`, and the `Restriction` the look-ahead probes, gain an
optional field:

```scala
note: (PowerCtx, DecisionOptionRef) => Option[PowerNote]
```

It defaults to returning nothing.

- When the walker first reaches a `Decide` and removes options from it, it
  calls the hook of the power that removed each option.
- Each returned note is journaled as a `PowerNoted`, in option order, before
  the decision parks or resolves.
- A note that names only what binds, not the option it hid, merges into one
  line per action (see "Placement"). Narrow Pass hiding three sites as
  targets, or the Circlet of Command hiding three relics, reads once.

### Game rules

The Homeland rule is not a power. `CardPlay` lets a player discard a card at
a full Homeland whose edifice matches the played card's suit, which is the
same choice People's Favor's Mob face offers everywhere. It gets the same
line.

- A game rule that writes a line gets a `PowerId` of its own, such as
  `rule.homeland-discard`, and declares its templates in `RuleNotes`, a small
  registry the formatter consults beside the two power catalogs.
- The card-play planner puts a `Note` with that id in the placement tree when
  the rule offers the discard. Its `source` is the Homeland site, so the line
  starts with the site's chip.

### Once per effect

A resumed command walks down to the parked position again and folds the same
windows again. A note is journaled only when the walker enters its node
fresh, that is with no resume cursor. Re-entering on the way to a parked
position journals nothing. Each new pass of a `Repeat` counts as fresh,
because the effect really happens again.

### Layering

- `PowerNote`, `NoteArg` and the `Note` leaf sit beside the operation types.
  Powers never import `gameplay.walker`, which `BackendArchitectureSuite`
  forbids.
- `PowerNoted` is a `WalkerEvent` in `gameplay/walker/WalkerEvents.scala`.
- Emission lives in `WalkerPowerGather` or a new `WalkerNotes.scala`.
  `ProcedureWalker.scala` is at 655 of its 800 allowed lines and does not
  grow beyond the single dispatch case the leaf needs.

## 2. Log

### Rendering

`GameLogFormatter.eventLines` gains a `PowerNoted` case, handled by a new
`PowerLines.scala` in `application/gamelog`.

- The line is the source's name as a chip, then ": ", then the power's
  sentence with its arguments filled in.
- The source is named the way `ActionLines.powerSource` names sources today,
  and passes the same identity check as any card. A facedown source reads as
  its back.
- Card arguments go through `LogWords.card` with the states before and after
  the event, so a card the viewer may not identify reads as its back ("a
  Relic").
- The first word of the sentence is capitalised: "Vow of Peace: The attacker
  cannot sacrifice against Blue."
- The kind is `Trigger`, the kind the Game Log design reserved for
  "{card}: {effect}". The wire format and the client do not change.

### Wording

Each power declares a template for each key it emits, beside its own code:

```scala
def noteTemplates: Map[String, Vector[NotePart]]
// NotePart = Text(words) | Arg(index) | Plural(index, one, many)
```

`Plural` picks a word by the `Amount` argument at `index`, so "Killed {n}
{Blue} warband" reads "Killed 1 Blue warband" and "Killed 2 Blue warbands".
An `Amount` rendered through `Arg` pluralises its own unit.

The formatter finds the power by id through `WalkerPowerCatalog` and
`PhasePowerCatalog`. `GameLogFormatter` already builds a lookup from the
first.

Wording rules:

- Past tense for what happened: "Toll Roads: Red paid 1 favor to Blue."
- "must", "may" or "cannot" for a rule that binds: "Narrow Pass: Red cannot
  travel to other sites in the region."
- Terse fragments are fine: "Outriders: Skulls ignored."
- An exclamation mark is allowed where a procedure changes dramatically.
- A pawn placed by a power "placed at" a site. It did not travel or move.
- Every amount is an argument, including one the card prints as fixed. The
  applied value can be smaller.
- Players are named by chip, never "you". Every viewer reads the same line.

### Placement

- A power line sits at depth 1 under its action, in journal order, among the
  action's detail lines. The walker journals each note where its effect
  happens, so journal order is effect order.
- A power line never precedes its action's start line. When a run's first
  event is a `PowerNoted` (the hide hook at an action's first decision), the
  start line posts first.
- Setup notes post under the Setup headline, among the setup lines.
- A power line identical to one already posted in the same action is
  dropped. The Circlet of Command hiding three relics, or Gleaming Armor
  taxing two plans, reads once. Only earlier lines are compared, so prefix
  stability holds.

### Phase powers

Every note in this table is the power's `used` line unless marked otherwise.
"Covers" names the generic line the note replaces. Today most of these
effects log nothing but "Used {card}": `GainSupply`, `Kill`, pawn moves, a
relic drawn by a power, and secrets moved between players have no generic
line at all. Those notes cover nothing and fill the gap.

A "Chose …" line from the power's own decision stays (see "Covering"). The
cost payment logs nothing today and still logs nothing.

| Card | Line | Covers |
|---|---|---|
| Elders | Elders: {Red} gained {1} secret. | the Gain line |
| Wayside Inn | Wayside Inn: {Red} gained {n} Supply. | |
| Magic Waterskin | Magic Waterskin: {Red} gained {n} Supply. | |
| Alchemist | Alchemist: {Red} gained {n} favor. | nothing: each bank's Gain line stays |
| Gambling Hall | Gambling Hall: {Red} rolled {dice}, Total: {n} | the Rolled line |
| Gambling Hall, key `gained`, when the total is above zero | Gambling Hall: {Red} gained {n} favor from {the Order bank}. | the Gain line |
| Bone Dice | Bone Dice: {Red} rolled {dice}, Total: {n} | the Rolled line |
| Bone Dice, key `gained`, when the score is above zero | Bone Dice: {Red} gained {n} Supply. | |
| Bone Dice, key `buried`, on a skull | Bone Dice: Buried after a skull. | the Buried line |
| Murky Fountain, pawn at its site | Murky Fountain: {Red} rolled {dice}, Total: {n} | the Rolled line |
| Murky Fountain, key `gained`, when the total is above zero | Murky Fountain: {Red} gained {n} Supply. | |
| Murky Fountain, key `ended`, on a zero total | Murky Fountain: {Red}'s Act phase ended. | |
| Murky Fountain, pawn elsewhere | Murky Fountain: {Red} was not at its site. | |
| Dowsing Sticks | Dowsing Sticks: {Red} drew {relic} facedown. | |
| Dowsing Sticks, empty deck | Dowsing Sticks: The relic deck was empty. | |
| Fae Merchant | Fae Merchant: {Red} drew {relic} facedown. | |
| Fae Merchant, key `returned` | Fae Merchant: {Red} put {relic} on the bottom of the relic deck. | the Buried line |
| Crystal Vial | Crystal Vial: {Red} buried {card}. | the Buried line |
| Ivory Eye | Ivory Eye: {Red} peeked at {Blue}'s {card}. | the Peeked line |
| Sleight of Hand | Sleight of Hand: {Red} took 1 secret from {Blue}. | |
| Sleight of Hand, no target | Sleight of Hand: No player could be robbed. | |
| Whistle | Whistle: Placed {Blue} at {site} and gave {Blue} the Whistle's secret. | |
| Whistle, no target | Whistle: No pawn could be pulled. | |
| Wolves | Wolves: Killed {n} {Blue} warband. | |
| Wolves, no warband | Wolves: {Blue} had no warband to kill. | |
| Brass Horse | Brass Horse: {Red} revealed {card} and placed at {site}. | the Revealed line |
| Magic Carpet | Magic Carpet: {Red} placed at {site}. | |
| Magic Carpet, key `given` | Magic Carpet: Given to {Blue}. | |
| Magic Carpet, key `discarded` | Magic Carpet: Discarded. | |
| Wandering Flame (move) | Wandering Flame: {Red} placed at {site}. | |
| Wandering Flame (place) | Wandering Flame: {Red} placed a secret at {site}. | |
| Horned Mask | Horned Mask: {Red} took {card} as a facedown adviser. | |
| Marble Fountains | Marble Fountains: {Red}'s Supply refreshed to {n}. | |
| Silver Tongue (REST) | Silver Tongue: {Red} took {n} favor from {the Order bank}. | the Gain line |
| Vow of Obedience (REST) | Vow of Obedience: {Red} took {n} favor from {the Order bank}. | the Gain line |

### Removed and hidden options

| Card | Path | Line |
|---|---|---|
| Vow of Peace, second sentence | `Note` replacing the sacrifice decision | Vow of Peace: The attacker cannot sacrifice against {Blue}. |
| Vow of Obedience | hide hook | Vow of Obedience: {Red} cannot play a Vision faceup. |
| Secret Police | hide hook | Secret Police: {Red} cannot play a Vision faceup. |
| Sacred Ground | hide hook | Sacred Ground: {Red} cannot play a Vision faceup. |
| Oaken Fortress, Rotting Fortress | hide hook, per protected target | Oaken Fortress: {Blue} cannot be targeted. |
| Circlet of Command | hide hook | Circlet of Command: {Blue}'s banners and relics cannot be targeted. |
| Narrow Pass, Campaign targets | hide hook | Narrow Pass: {Red} cannot target other sites in the region. |
| Narrow Pass, Travel | hide hook | Narrow Pass: {Red} cannot travel to other sites in the region. |

### Added effects

| Card | Line | Covers |
|---|---|---|
| Toll Roads | Toll Roads: {Red} paid 1 favor to {Blue}. / Toll Roads: {Red} burned 1 favor. | |
| Grasping Vines | Grasping Vines: Killed {n} {Red} warband. | |
| Gossip | Gossip: {Blue} gained {n} favor from {the Discord bank}. | the Gain line |
| Book Binders | Book Binders: {Blue} gained {n} favor from {the Order bank}. | the Gain line |
| Gleaming Armor | Gleaming Armor: {Red}'s battle plans cost {1} extra secret. | |
| Conspiracy (when played) | Conspiracy: {Red} seized {relic or banner} from {Blue}. | |
| Dazzle | Dazzle: Discarded {cards}. | the Discard line |
| Mercenaries | Mercenaries: Discarded after {Red} lost. | |
| Sticky Fire | Sticky Fire: Killed {n} {Blue} warband, and {Blue} gained {n} favor. | |
| Truthful Harp | Truthful Harp: Revealed {cards}. | the Reveal line |
| Forest Paths | Forest Paths: Ignoring site powers. | |
| Dragonskin Drum | Dragonskin Drum: Gained {n} warband. | |

### Altered procedures

| Card | Line |
|---|---|
| Outriders | Outriders: Skulls ignored. |
| Warning Signals | Warning Signals: {Blue} may redistribute their warbands. |
| Knights Errant | Knights Errant: {Red} campaigns for no Supply. |
| People's Favor (Mob face) | People's Favor: {Red} may discard a card at their site first. |
| Homeland rule, at a full Homeland matching the played card's suit | {Homeland site}: {Red} may discard a card at their site first. |
| League Treaty | League Treaty: {Blue} may send the region's favor to one bank. |

### Setup

| Card | Line |
|---|---|
| Great Market | Great Market: Placed {n} favor on {site}. |
| Bandit Market | Bandit Market: Placed 1 favor on each bandit site and burned 1 favor from each bank. |
| Great Forge | Great Forge: {Red} drew {relic} facedown. |
| Broken Forge | Broken Forge: Discarded {n} relics at {sites}. |
| Proving Grounds | Proving Grounds: {Red} gained 3 warbands. |
| Empty Grounds | Empty Grounds: Discarded {cards}. |

### No line

- Mountain, Island and Coast terrain: the cost span shows the cost.
- Chosen modifiers whose effect is only an amount or dice change: the Supply
  waivers of Forest Paths and Tents, Welcoming Party, Wild Cry, Cup of Plenty,
  Rowdy Pub, Relic Worship, Augury, and the battle plans that only add or
  remove dice or grant a conditional favor (Battle Honors, Brass Army,
  Fearsome Shield, Cracked Rampart, Towering Rampart, Watchdog, Wrestlers,
  title defense).
- Catacombs: its Move line shows it.
- A Small Favor, Faithful Friend, Garrison, Family Heirloom: their operations
  and decisions already log.
- Silver Tongue's adviser limit: the limit shows only as fewer advisers, and
  no decision point explains it well.
- Take Wealth's once-per-site limit: an ordinary availability rule.
- Every whole-action block (ruling 3): Vow of Peace's first sentence, the
  Fortress's Raid-only block.

## 4. Storage

- `PowerNoted(power, source, note)` joins `WalkerEventCodec` with the type
  `"walker.power-noted"`.
- A note is stored as its key and its arguments, each argument tagged with
  its kind, like the log's span wire format. An unknown argument kind is a
  decode error.
- `FormatVersion` does not change. Old journals stay valid and contain no
  notes.
- `WalkerReplay.applyRecordedReady` gains a no-op `PowerNoted` case ahead of
  its catch-all, which would otherwise reject the event. Replay never reads a
  note, as it never reads `contributions`.
- The `Note` leaf is part of the tree, not the journal, so it needs no codec.

## Tests

Walker:

- A `Note` leaf journals once, in walk order, with arguments read from the
  state at that point.
- A note after a capped step reads the applied amount from `previous`, not
  the printed one: Magic Waterskin near the top of the Supply track.
- A `Note` in a branch that does not run journals nothing.
- A resumed command journals no note twice. A new `Repeat` pass journals
  again.
- The hide hook journals one note per hidden option, only at a decision the
  walker reaches.
- A whole-action Restriction journals nothing.
- Replay ignores `PowerNoted`: a journal with and without notes replays to the
  same state.

Codec, in `GameEventWireSuite`: a round trip of every argument kind, and an
unknown argument kind rejected.

Formatter:

- A note renders as chip, colon and capitalised sentence, with kind
  `Trigger`.
- A card argument a viewer may not identify reads as its back. A facedown
  source reads as its back.
- A power line never precedes its run's start line.
- A `used` note replaces "Used {card}". A run without one still posts it.
- A covering note drops the covered step's detail line and nothing else.
- A note with no template renders nothing.
- A line identical to an earlier line of the same action is dropped; the same
  line in the next action is not.
- `Plural` and a pluralised `Amount` read correctly for 1 and for 2.
- A card played to a full matching Homeland reads the Homeland line; a card
  played to a full Homeland of another suit is refused as today and reads
  nothing.
- The prefix-stability property holds over journals that contain notes.

Catalog:

- Every key a power can emit has a template.
- Every phase power declares a `used` template.
- Every id in `RuleNotes` has a template for each key it emits.

Cards: each row of the audit asserts its exact line in that card's existing
suite.

## Slices

1. **Mechanism.** `PowerNote`, the `Note` leaf, the hide hook, `PowerNoted`
   and its codec, replay's no-op case, `PowerLines`, covering, the fallback,
   and the catalog tests. Proven on Vow of Peace and Gambling Hall.
2. **Phase powers.** The rest of the phase power table.
3. **Removed and hidden options.** That table.
4. **Added effects and altered procedures.** Those tables.
5. **Setup.** The setup table.

The plan may merge slices when it sizes them.

## Out of scope

- Explaining a missing action control (ruling 3), and explaining a hidden
  option where it would have been offered. The roadmap's "explain restricted
  options where they are offered" item covers both. Once it exists, most
  hide-hook lines can move out of the log; the hide hook's note is the
  natural source of that explanation.
- Cards not yet implemented. Catalog batch 2 follows this pattern for each
  card it adds.
- WHEN EXPLORED: the setup rules share that window, but no explore procedure
  folds it yet. Their notes will work there unchanged when one does.

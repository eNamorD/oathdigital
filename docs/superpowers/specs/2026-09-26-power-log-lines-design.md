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

`Note(power: PowerId, source: RuleSourceRef, build: ReadyGame => PowerNote)`
is a new leaf of the operation tree. The walker handles it the way it handles
`Decide` and `Roll`. It is not a `CoreOperation` and changes no state.

- A power puts a `Note` in any tree it builds or rewrites: a Transform's
  output, a battle plan's subtree, a phase power's `build`, a when-played
  tree, a setup rule's operations.
- When the walk reaches the leaf, the walker calls `build` on the current
  state and appends `PowerNoted(power, source, note)` to `WalkCtx.events`, in
  walk order. `build` reads the live state, like `BuildOps`, so a note after
  a roll can name the faces rolled.
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
- Identical notes at one decision (same power, source, key and args) are
  journaled once. Narrow Pass writes one line per hidden site. The Circlet of
  Command, whose note names only the holder, writes one line however many of
  the holder's relics it hides.

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
// NotePart = Text(words) | Arg(index)
```

The formatter finds the power by id through `WalkerPowerCatalog` and
`PhasePowerCatalog`. `GameLogFormatter` already builds a lookup from the
first.

Wording rules:

- Past tense for what happened: "Toll Roads: Red paid 1 favor to Blue."
- "must", "may" or "cannot" for a rule that binds: "Narrow Pass: Red cannot
  travel to Green Shore."
- Terse fragments are fine: "Outriders: Skulls ignored."
- An exclamation mark is allowed where a procedure changes dramatically.
- Players are named by chip, never "you". Every viewer reads the same line.

### Placement

- A power line sits at depth 1 under its action, in journal order, among the
  action's detail lines. The walker journals each note where its effect
  happens, so journal order is effect order.
- A power line never precedes its action's start line. When a run's first
  event is a `PowerNoted` (the hide hook at an action's first decision), the
  start line posts first.
- Setup notes post under the Setup headline, among the setup lines.

### Phase powers

- A phase power's note with the key `used` becomes the action line, kind
  `Action`, at that note's position. It replaces "Used {card}".
- The phase power's other notes are ordinary `Trigger` lines.
- A run with no `used` note posts "Used {card}" exactly as today. In practice
  that is only a journal written before this phase, since a catalog test
  requires every phase power to declare `used` (see Tests).

### Covering

A note may restate a step's result. Gambling Hall's "Rolled 3 shields, Total:
3" repeats the generic "Rolled" detail line.

- A `Note` leaf carries `covers: Boolean`, default false.
- A covering note covers the step journaled immediately before it in the same
  segment, and the formatter drops that step's generic detail lines.
- The formatter already reads ahead to the end of a segment (Game Log design,
  "Posting"), so the step's lines are decided before anything is sent and
  prefix stability holds.
- A note that covers nothing leaves ruling 5 intact: the operations still log.

### Missing wording

A note whose power or key has no template renders nothing, which matches the
log's rule that an event this build does not know stays silent. That happens
only for a journal naming a power that was later renamed or removed. A
catalog test holds current powers to having every template.

## 3. Audit

These rows are the source of truth for this phase. A plan task implements
rows. The wording is final unless spec review changes it. `{…}` marks an
argument. "Covers" names the generic detail line a note replaces.

### Phase powers

Every note in this table is the power's `used` line unless marked otherwise.

| Card | Line | Covers |
|---|---|---|
| Elders | Elders: {Red} gained 1 secret. | the Gain line |
| Wayside Inn | Wayside Inn: {Red} gained 2 Supply. | the Gain line |
| Magic Waterskin | Magic Waterskin: {Red} gained 4 Supply. | the Gain line |
| Alchemist | Alchemist: {Red} gained 4 favor. | nothing: each bank's Gain line stays |
| Gambling Hall | Gambling Hall: {Red} rolled {dice}, Total: {n} | the roll |
| Gambling Hall, key `gained`, when the total is above zero | Gambling Hall: {Red} gained {n} favor from {the Order bank}. | the Gain line |
| Bone Dice | Bone Dice: {Red} rolled {dice}, Total: {n} | the roll |
| Bone Dice, key `gained` | Bone Dice: {Red} gained {n} Supply. | the Gain line |
| Bone Dice, key `buried`, on a skull | Bone Dice: Buried after a skull. | the Bury line |
| Murky Fountain, pawn at its site | Murky Fountain: {Red} rolled {dice}, Total: {n} | the roll |
| Murky Fountain, key `gained` | Murky Fountain: {Red} gained {n} Supply. | the Gain line |
| Murky Fountain, key `ended`, on a zero total | Murky Fountain: {Red}'s Act phase ended. | |
| Murky Fountain, pawn elsewhere | Murky Fountain: {Red} was not at its site. | |
| Dowsing Sticks | Dowsing Sticks: {Red} drew {relic} facedown. | the Draw line |
| Dowsing Sticks, empty deck | Dowsing Sticks: The relic deck was empty. | |
| Fae Merchant | Fae Merchant: {Red} drew {relic} facedown. | the Draw line |
| Fae Merchant, key `returned` | Fae Merchant: {Red} put {relic} on the bottom of the relic deck. | the Bury line |
| Crystal Vial | Crystal Vial: {Red} buried {card}. | the Bury line |
| Ivory Eye | Ivory Eye: {Red} peeked at {Blue}'s {card}. | the Peek line |
| Sleight of Hand | Sleight of Hand: {Red} took 1 secret from {Blue}. | |
| Sleight of Hand, no target | Sleight of Hand: No player could be robbed. | |
| Whistle | Whistle: {Red} pulled {Blue}'s pawn to {site}. | |
| Whistle, no target | Whistle: No pawn could be pulled. | |
| Wolves | Wolves: Killed 1 {Blue} warband. | |
| Wolves, no warband | Wolves: {Blue} had no warband to kill. | |
| Brass Horse | Brass Horse: {Red} revealed {card} and moved to {site}. | the Reveal line |
| Magic Carpet | Magic Carpet: {Red} moved to {site}. | |
| Magic Carpet, key `given` | Magic Carpet: Given to {Blue}. | |
| Magic Carpet, key `discarded` | Magic Carpet: Discarded. | |
| Wandering Flame (move) | Wandering Flame: {Red} moved to {site}. | |
| Wandering Flame (place) | Wandering Flame: {Red} placed a secret at {site}. | |
| Horned Mask | Horned Mask: {Red} took {card} as a facedown adviser. | |
| Marble Fountains | Marble Fountains: {Red}'s Supply refreshed to {n}. | |
| Silver Tongue (REST) | Silver Tongue: {Red} took 1 favor from {the Order bank}. | the Gain line |
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
| Narrow Pass, Campaign targets | hide hook, per site | Narrow Pass: {Red} cannot target {site}. |
| Narrow Pass, Travel | hide hook, per site | Narrow Pass: {Red} cannot travel to {site}. |

### Added effects

| Card | Line | Covers |
|---|---|---|
| Toll Roads | Toll Roads: {Red} paid 1 favor to {Blue}. / Toll Roads: {Red} burned 1 favor. | |
| Grasping Vines | Grasping Vines: Killed 1 {Red} warband. | |
| Gossip | Gossip: {Blue} gained 1 favor from {the Discord bank}. | the Gain line |
| Book Binders | Book Binders: {Blue} gained {n} favor from {the Order bank}. | the Gain line |
| Gleaming Armor | Gleaming Armor: {Red} paid 1 secret to use {plan}. | |
| Conspiracy (when played) | Conspiracy: {Red} took {relic or banner} from {Blue}. | |
| Dazzle | Dazzle: Discarded {cards}. | the Discard line |
| Mercenaries | Mercenaries: Discarded after {Red} lost. | |
| Sticky Fire | Sticky Fire: Killed every {Blue} warband, and {Blue} gained 1 favor. | |
| Truthful Harp | Truthful Harp: Revealed {cards}. | the Reveal line |
| Forest Paths | Forest Paths: {site}'s powers were ignored. | |
| Dragonskin Drum | Dragonskin Drum: Placed 1 warband at {site}. | |

### Altered procedures

| Card | Line |
|---|---|
| Outriders | Outriders: Skulls ignored. |
| Warning Signals | Warning Signals: {Blue} may redistribute their warbands. |
| Knights Errant | Knights Errant: {Red} campaigns for no Supply. |
| People's Favor (Mob face) | People's Favor: {Red} may discard a card at their site first. |
| League Treaty | League Treaty: {Blue} may send the region's favor to one bank. |

### Setup

| Card | Line |
|---|---|
| Great Market | Great Market: Placed {n} favor on {site}. |
| Bandit Market | Bandit Market: Placed 1 favor on each bandit site and burned 1 favor from each bank. |
| Great Forge | Great Forge: {Red} drew {relic} facedown. |
| Broken Forge | Broken Forge: Discarded the relics at {sites}. |
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
- A `Note` in a branch that does not run journals nothing.
- A resumed command journals no note twice. A new `Repeat` pass journals
  again.
- The hide hook journals one note per hidden option, only at a decision the
  walker reaches, with identical notes merged.
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
- The prefix-stability property holds over journals that contain notes.

Catalog:

- Every key a power can emit has a template.
- Every phase power declares a `used` template.

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

- Explaining a missing action control (ruling 3). That belongs to the action
  controls, and no roadmap item covers it yet.
- Cards not yet implemented. Catalog batch 2 follows this pattern for each
  card it adds.
- WHEN EXPLORED: the setup rules share that window, but no explore procedure
  folds it yet. Their notes will work there unchanged when one does.

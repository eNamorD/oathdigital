# Travel Destination in the Walker

**Status:** designed and implemented 2026-10-07. It takes the **Phase - Board decision
follow-ups** item "Move Travel onto board decisions" and unblocks Way Station
(169), which the
[catalog batch 4 rulings](2026-10-05-catalog-batch-4-rulings.md) parked on it.
It also retires the older board targets, whose last user is Travel, and makes
every hidden option explain itself on hover instead of in the Game Log.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**,
**draft set** and **situation**. This design adds **start fact**.

## Goal

A Travel's destination becomes the walk's first parked decision, answered on
the board, instead of a selection the client makes before the walker starts.
Powers that care which site was picked can then act inside the walk, ask
their own questions and pause it. Way Station is the proof card: its
non-ruler's choice to pay appears only when Way Station's site is the
destination.

## Background

Today the client asks for modifiers, then the destination, then sends one
`StartWalker("travel", modifiers, Vector(Site(destination)))`. The tree is
flat and never parks:

```
Sequence(                                  // window = TravelActionEligibility
  Sequence(                                // window = TravelCost
    SpendSupply(actor, printedBase),
    Move(Pawn, source -> destination)))
```

`TravelProcedure.candidates` dry-runs that tree once per destination for the
legal-action projection and the major-action preview. `rebuild` is `build`,
and both read the source from the pawn's current site. The destination is
picked through the older board targets (`BoardTargetActionProjection`,
`BoardTargetSelectionState`), and Travel is the only action left on them.

The walker derives its tree per command from live state (procedure walker
design, ruling 12). Durable beside the cursor sit only what state cannot
re-derive: the procedure, `walkerModifiers` and `walkerStartArgs`, which are
player selections. A fact the engine derives at start is read again on every
resume. Recover and Forge read the pawn's site safely because their pawn never
moves; Travel did only because it never parked. Campaign met the same gap and
pins its kind and defender as sentinel `ModifyDicePool` operations
(`CampaignSetup.pinned`).

When the walker hides an option, the hiding power's note is both the hover
reason (board decisions design) and a Game Log line, journaled as a
`PowerNoted` when the decision is first asked (power log lines design,
slice 3).

## Rulings

1. **Modifiers stay before the start.** The modifier flow is unchanged:
   a player selects modifiers, then the walker starts. Selected modifiers'
   costs are paid inside the walk, after the destination is picked.
   [Modifier selection on the walker](2026-10-09-modifier-walker-design.md)
   moved Travel's modifiers into the walk in its slice 2; this ruling is
   rewritten in its slice 4.
2. **The destination is a board decision.** `StartWalker("travel")` carries
   no start selection. The walk's first node is a board choose-one,
   `travel.destination`, headed "Choose a Travel destination", over every
   in-play site except the pawn's. A start that names a selection is refused.
3. **Every option says what it costs.** Each available destination is
   `Badged` with its cost under the selected modifiers:
   - Supply only: "2 Supply". A free Travel reads "0 Supply".
   - With a favor toll (Toll Roads): "2 Supply · 1 favor". A toll on a
     Travel that costs no Supply reads "1 favor".
   - On Way Station's site, for a player who may pay instead:
     "2 Supply or 1 favor".

   A selected modifier's own cost, paid onto its card, is not in the badge:
   it is the same for every destination. Kills (Tyrant, Grasping Vines) and
   gains (Dragonskin Drum) are not in the badge either.
4. **A destination the actor cannot reach is shown, not hidden.** It is
   `Unavailable`, with a reason:
   - Too little Supply: "Needs 3 Supply; you have 2", worded as Campaign's
     battle-plan shortfalls are.
   - Too little favor for a toll: "Needs 1 favor; you have 0".
   - Narrow Pass: "Narrow Pass: Cannot travel to other sites in the region."
     Narrow Pass's Travel `Restriction` gains a note for this.

   When both apply, the restriction's reason is shown.
5. **No reachable destination, no Travel.** The Travel control shows only
   when at least one destination is available, and the engine refuses a start
   with none (`requiresPlayableOption`, as Muster does). The control and the
   parked decision read the same preview.
6. **Start facts.** A registry entry may declare start facts:
   `DecisionOptionRef`s the engine derives at start, which state cannot give
   back once the walk changes it. They are stored as `walkerStartFacts`
   beside `walkerStartArgs`, carried on `WalkerParked`, restored by replay and
   cleared by `WalkerCompleted`. A procedure's `rebuild` reads them from the
   state it is handed. Travel's source site is its one start fact; its
   `rebuild` never reads the pawn's site. Start args stay what the player
   selected; start facts are what the engine derived.
7. **Way Station.** A persistent power of the card at its site.
   - Its ruler travels there free, with no question.
   - A non-ruler traveling there is asked, in the Action panel, only when
     they hold at least 1 favor and the Travel still costs Supply after every
     other transform. The heading is "Way Station: spend no Supply?", and the
     answers are "Give 1 favor to {ruler}" and "Spend {n} Supply". Only
     affordable answers are offered, and the question is asked even when one
     answer is left.
   - When bandits rule the site, the favor is burnt, and the first answer
     reads "Burn 1 favor".
   - The favor answer keeps Way Station's site available to a player short of
     Supply.
8. **Undo replaces the destination panel's way back.** The pre-walker
   destination pick, its "Cancel" and its "Back to modifiers" retire. Undo
   takes back the start, as it does for the facedown-adviser pick (undo
   design, slice 4). The ordering stage's "Back" and "Cancel action" stay.
9. **Game Log.**
   - Every `travel.` decision is narrated: the destination pick posts no
     "Chose" line, since "Travelled to X" names the site, and Way Station's
     question posts none, since its payment has its own line.
   - Way Station's favor posts the toll lines Curfew and Forced Labor already
     post, under Way Station's name: "Gave 1 favor to {ruler}." or, when
     bandits rule, "Burned 1 favor." Paying Supply posts no Way Station line.
10. **A hidden option explains itself on hover only.** The walker stops
    journaling the notes of the options it hides, for every power. Each note
    stays the hidden option's hover reason. Two notes that name the acting
    player, who is the only one who sees the reason, drop the name:
    - Narrow Pass on Campaign targets: "Narrow Pass: Cannot target other
      sites in the region."
    - The no-faceup-Vision rule (Secret Police, Sacred Ground): "Cannot play
      a Vision faceup."

    Notes that name someone else ("{Blue}'s relics cannot be targeted") are
    unchanged.

## Architecture

### The tree

```
Sequence(
  Decide(travel.destination, board choose-one over sites),
  Branch { after the answer:
    Sequence(                              // window = TravelActionEligibility
      Sequence(                            // window = TravelCost
        SpendSupply(actor, printedBase(source, destination)),
        Move(Pawn, source -> destination))) })
```

- The two windows sit on Sequences inside the `Branch`'s selected vector.
  Every Travel power finds the route by flattening its windowed node to the
  `Move` (`TravelRoute.pawnMove`), and a `Branch` flattens to nothing, so a
  window above the `Branch` would see no route.
- A selected modifier's payment is prepended at `TravelActionEligibility`,
  which is now inside the `Branch`. It is paid after the pick, so nothing is
  recorded before the first decision, as `WalkerSimulation.preview` requires.
- The `Branch` reads the source from the start fact (on a resume) or the
  pawn's site (on a fresh build), and the destination from the answer.
  `TravelRules.cost` is unchanged.
- A start with no answer has no route, so the start command's restriction
  check finds nothing to refuse. The look-ahead answers each destination
  hypothetically, so Narrow Pass sees each route there and hides what it
  blocks.

### Options

- `WalkerSimulation.previewParked` gains a follow-up mode. When an answer
  parks on a further choose-one, its options are previewed too, against the
  state after the answer's recorded steps. The outcome carries them as
  `next`.
- A registry entry may declare `optionPricing`: a function from a parked
  position to the options to show. When an entry declares it, the projector
  shows those options in place of the playable-option filter.
- Travel's pricing previews the parked destination decision in follow-up
  mode:
  - An accepted option is `Badged` with ruling 3's wording, read from the
    operations it recorded: the `SpendSupply` amount, plus favor given
    (`Give`) or paid to a bank or burnt (`PayCost` not onto a card). When the
    option parks on a further question, the badge joins each of that
    question's accepted answers' costs with " or ".
  - A rejected option is `Unavailable` with ruling 4's shortfall. A Supply
    failure does not carry the amount, so the amount is read from a second
    preview against the same state with the actor's Supply at the track's
    maximum.
  - An option the look-ahead already hid stays as it is.
- The start gate, the parked decision and the Travel control all read that
  preview: `requiresPlayableOption` previews the fresh tree, and
  `TravelProcedure.startable` previews it for the legal-action projection.

`TravelProcedure.candidates`, the major-action preview's targets, and the
legal-action projection's board targets retire.

### Way Station

A persistent `ContributingPower` of the card, with a `Transform` at
`TravelCost`, ordered after the other Travel transforms (priority 3) so it
sees the Supply they leave. On a Travel to its site:

- Its ruler: the `SpendSupply` is removed.
- A non-ruler with favor, while a `SpendSupply` is left: the `SpendSupply` is
  replaced by a Panel `Decide`, `travel.way-station`, and a `Branch` that
  pays the favor (a `Give` to the ruler, or a burn when bandits rule, with its
  note) or spends the Supply. The `Move` stays outside the `Branch`, so later
  windows still see the route.
- Otherwise the cost node is unchanged, and a player short of Supply is
  refused.

The park sits before the `Move`, and the source is a start fact, so a resume
rebuilds the same tree.

### Hidden-option notes

- `ProcedureWalker.runLeaf` stops journaling the look-ahead's notes, and
  `WalkerPowerGather` stops journaling the notes of the options an
  `OptionRestriction` hides. Both still turn the note into the option's
  `Unavailable` reason.
- Narrow Pass's Travel `Restriction` passes a note for the look-ahead.

### Frontend

Slice 4 of the undo design is the template.

- A `beginTravel` control starts `StartWalker("travel")` through the modifier
  flow, which orders any modifiers before the start.
- The parked `travel.destination` decision is a board decision on sites, so
  the existing board-decision surface renders it. A site option's badge is
  drawn on the site box, where the old target badge was.
- Way Station's question is a Panel decision with button options.
- Retired, with their tests: the board targets (`BoardTargetSelectionState`,
  `GameProjection.boardTargetActions`, `BoardTargetActionProjection`, the
  draft set's `boardTargets` slot), the modifier flow's targets stage (its
  "Cancel" and "Back to modifiers"), and the major-action preview's `targets`.

### Persistence

- `walkerStartFacts: Vector[DecisionOptionRef]` joins the state's walker
  fields and the `WalkerParked` event and its codec. A park written without it
  reads as empty. The replay check that compares a park's start args with the
  recorded start compares its start facts too.
- No journal holds a parked Travel, because Travel never parked, so no legacy
  rebuild is needed. Before the first public release, event formats change in
  place (roadmap standing rules).

## Testing

- **Engine.** A Travel parks on `travel.destination` and completes on its
  answer. A start with a selection is refused, and a start with no reachable
  destination is refused. A resume reads the source from the start fact:
  move the pawn under a parked Travel, and the rebuilt tree is unchanged.
- **Pricing.** The badges are checked for Supply, a Toll Roads favor, a
  selected modifier that makes the Travel free, and Way Station's "or". The
  unavailable reasons are checked for a Supply shortfall and Narrow Pass.
- **Way Station.** Its ruler travels free and is not asked. A non-ruler with
  favor is asked; with no favor, they are not asked. A Travel that a selected
  modifier already made free is not asked. Under bandit rule the favor is
  burnt. A player short of Supply can still pick the site and pay the favor.
  The Game Log lines for each.
- **Hidden notes.** A hidden option keeps its reason and journals no
  `PowerNoted`.
- **Service.** Undo takes back the start and the destination answer.
- **Frontend.** The launch sends no selection. The parked decision renders on
  the board with badges and reasons.
- Existing suites that start a Travel with a site selection move to start,
  then answer.

## Out of scope

- **Hospitality** (171). Start facts remove what its ruling named as the
  blocker: a resume after the `Move` no longer reads the pawn's site, and the
  options preview already follows a run that parks. Its after-move bank pick
  is its own slice. When this design lands, Hospitality stays in the
  roadmap's engine-blocked group, marked as unblocked, since no other group
  fits it yet.
- **Campaign's pinned facts.** Moving `CampaignSetup.pinned` onto start facts
  goes on the roadmap as a follow-up when this design lands.
- Kills and gains in the destination badge.

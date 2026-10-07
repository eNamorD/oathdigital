# Travel Destination in the Walker

**Status:** designed 2026-10-07. It takes the **Phase - Board decision
follow-ups** item "Move Travel onto board decisions" and unblocks Way Station
(169), which the
[catalog batch 4 rulings](2026-10-05-catalog-batch-4-rulings.md) parked on it.

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
and both read the source from the pawn's current site.

The walker derives its tree per command from live state (procedure walker
design, ruling 12). Durable beside the cursor sit only what state cannot
re-derive: the procedure, `walkerModifiers` and `walkerStartArgs`, which are
player selections. A fact the engine derives at start is read again on every
resume. Recover and Forge read the pawn's site safely because their pawn never
moves; Travel did only because it never parked. Campaign met the same gap and
pins its kind and defender as sentinel `ModifyDicePool` operations
(`CampaignSetup.pinned`).

## Rulings

1. **Modifiers stay before the start.** The modifier flow is unchanged:
   a player selects modifiers, then the walker starts. Selected modifiers'
   costs are paid inside the walk, after the destination is picked.
2. **The destination is a board decision.** `StartWalker("travel")` carries
   no start selection. The walk's first node is a board choose-one,
   `travel.destination`, headed "Choose a Travel destination", over every
   in-play site except the pawn's. A start that names a selection is refused.
3. **Every option says what it costs.** Each destination is `Badged` with
   its cost under the selected modifiers:
   - Supply only: "2 Supply". A free Travel reads "0 Supply".
   - With a favor toll (Toll Roads): "2 Supply · 1 favor".
   - On Way Station's site, for a player who may pay instead:
     "2 Supply or 1 favor".

   Kills (Tyrant, Grasping Vines) and gains (Dragonskin Drum) are not in the
   badge.
4. **A destination the actor cannot reach is shown, not hidden.** It is
   `Unavailable`, with a reason:
   - Too little Supply: "Needs 3 Supply, you have 2".
   - Narrow Pass: "Blocked by Narrow Pass", exactly, with no power-name
     prefix in front of it. Narrow Pass's Travel `Restriction` gains a note
     key of its own for this. It stays a hover reason: a Travel the Pass
     blocks still writes no Game Log line (power log lines design, ruling 3).

   When both apply, the restriction's reason is shown.
5. **No reachable destination, no Travel.** The Travel control shows only
   when at least one destination is available, and the engine refuses a start
   with none (`requiresPlayableOption`, as Muster does). The control and the
   parked decision read the same definition.
6. **Start facts.** An action's `build` may return start facts beside its
   tree: `DecisionOptionRef`s the engine derives at start, which state cannot
   give back once the walk changes it. They are stored as `walkerStartFacts`
   beside `walkerStartArgs`, carried on `WalkerParked`, restored by replay,
   cleared by `WalkerCompleted`, and handed to `rebuild`. Travel's source site
   is its one start fact; its `rebuild` never reads the pawn's site. Start
   args stay what the player selected; start facts are what the engine
   derived.
7. **Way Station.** A persistent power of the card at its site.
   - Its ruler travels there free, with no question.
   - A non-ruler traveling there is asked, in the Action panel, only when
     they hold at least 1 favor and the Travel still costs Supply after every
     other transform. The answers are "Give 1 favor to {ruler}" and "Spend
     {n} Supply". Only affordable answers are offered, and the question is
     asked even when one answer is left.
   - When bandits rule the site, the favor is burnt, and the first answer
     reads "Burn 1 favor".
   - The favor answer keeps Way Station's site available to a player short of
     Supply.
8. **Undo replaces the destination panel's way back.** The pre-walker
   destination pick, its "Cancel" and its "Back to modifiers" retire. Undo
   takes back the start, as it does for the facedown-adviser pick (undo
   design, slice 4). The ordering stage's "Back" and "Cancel action" stay.
9. **Game Log.**
   - The destination pick joins `DetailLines.NarratedIds`: "Travelled to X"
     already names the site, so the pick posts no "Chose" line.
   - Way Station's favor posts "Gave 1 favor to {ruler} for Way Station", or
     "Burnt 1 favor for Way Station" when bandits rule. Paying Supply posts no
     Way Station line.

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
- The `Branch` reads the source from the start fact and the destination from
  the answer. `TravelRules.cost` is unchanged.

### Options

One definition costs every destination. It dry-runs each destination's
subtree with the walk's selected powers, as `candidates` does today, and it
is read by the start gate, the parked decision and the legal-action
projection. Where it runs (the query at park time, or the projector) is the
implementation plan's choice. The constraint is that there is one definition.

- A run that completes gives the badge: the Supply it spent and any favor it
  gave or burnt.
- A run that parks on Way Station's question is previewed through both
  answers. The Supply answer gives "n Supply", the favor answer "1 favor".
  The destination is available when either answer completes.
- A run that fails on Supply gives the written shortfall reason.
- A destination a restriction refuses is wrapped by the walker's look-ahead
  (`WalkerSearch.narrow`) as `Unavailable(Noted)`, with Narrow Pass's new note.

`TravelProcedure.candidates` and its callers retire: the legal-action
projection's Travel entry in `boardTargetActions` and the major-action
preview's Travel `PreviewTarget`s. `BoardTargetSelectionState` stays for
setup pawn placement.

### Way Station

A persistent `ContributingPower` of the card, with a `Transform` at
`TravelCost`, ordered after the other Travel transforms so it sees the Supply
they leave. When ruling 7's conditions hold, it prepends a Panel `Decide` and
a `Branch` to the cost node:

- Favor: a `Give` of 1 favor to the ruler (a burn when bandits rule), and the
  `SpendSupply` removed.
- Supply: the cost node unchanged.

When its ruler travels there, it removes the `SpendSupply` with no question.
The park sits before the `Move`, and the source is a start fact, so a resume
rebuilds the same tree.

### Frontend

Slice 4 of the undo design is the template.

- The Travel launch button sends `StartWalker("travel")` with the selected
  modifiers through the modifier flow. The flow still previews Travel to offer
  modifiers, and orders them before the start.
- The parked `travel.destination` decision is a board decision on sites, so
  the existing board-decision surface renders it. Badges and unavailable
  reasons come from the projection.
- Way Station's question is a Panel decision with button options.
- Retired: `"travel"` in `ModifierFlowDraft.targetedActions`, the Travel
  branch of `ServerUiSupport.commandForSelection`, and the Travel uses of the
  targets stage (`FlowStep.Targets`, `TargetsEntry.Board`) with their "Cancel"
  and "Back to modifiers". Whatever only Travel used is deleted.

### Persistence

- `walkerStartFacts: Vector[DecisionOptionRef]` joins the state's walker
  fields and the `WalkerParked` event and its codec. The replay check that
  compares a park's start args with the recorded start compares its start
  facts too.
- No journal holds a parked Travel, because Travel never parked, so no legacy
  rebuild is needed. Before the first public release, event formats change in
  place (roadmap standing rules).

## Testing

- **Engine.** A Travel parks on `travel.destination` and completes on its
  answer. A start with a selection is refused, and a start with no reachable
  destination is refused. The badges are checked for Supply, a Toll Roads
  favor, a selected modifier that makes the Travel free, and Way Station's
  "or". The unavailable reasons are checked for a Supply shortfall and Narrow
  Pass. A resume reads the source from the start fact: move the pawn under a
  parked Travel, and the rebuilt tree is unchanged.
- **Way Station.** Its ruler travels free and is not asked. A non-ruler with
  favor is asked; with no favor, they are not asked. A Travel that a selected
  modifier already made free is not asked. Under bandit rule the favor is
  burnt. A player short of Supply can still pick the site and pay the favor.
  The Game Log lines for each.
- **Service.** Undo takes back the start and the destination answer.
- **Frontend.** The launch sends no selection. The parked decision renders on
  the board with badges and reasons. The retired targets stage is gone.
- Existing suites that start a Travel with a site selection (about 33 server,
  2 shared and 11 frontend files) move to start, then answer. `TravelFixture`
  keeps one helper, renamed to say it starts and answers.

## Out of scope

- **Hospitality** (171). Start facts remove what its ruling named as the
  blocker: a resume after the `Move` no longer reads the pawn's site, and the
  options definition already previews a run that parks. Its after-move bank
  pick is its own slice. When this design lands, the roadmap moves Hospitality
  out of the engine-blocked group.
- **Campaign's pinned facts.** Moving `CampaignSetup.pinned` onto start facts
  goes on the roadmap as a follow-up when this design lands.
- Kills and gains in the destination badge.

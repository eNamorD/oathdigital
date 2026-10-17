# Consent for refused automatic powers

**Status:** designed 2026-10-15; done 2026-10-17. It covers roadmap item **10b. Consent for
refused automatic powers** under **Phase - Catalog batch 8**. It depends on
slice 10 of [catalog batch 8](2026-10-12-catalog-batch-8-design.md), which
lands the `AutomaticUse` node with silent refusal (see "Slice 10's share"
below). Implementation starts only once slice 10 is on main.

**Builds on** the [consent system](2026-10-08-consent-system-design.md),
whose vocabulary (waivable power, granter, consent request, consent answer,
consent preference, consent ledger) and rulings apply unless this design
says otherwise. The [undo design](2026-10-06-undo-design.md) defines
**command batch**, **issuer** and **barrier**.

This design adds **automatic power use**, **power-use step** and
**deactivation**.

## Goal

Spell Breaker, Forest Council and City Wall forbid enemies of their ruler
to use certain powers. Slice 10 makes a chosen power refused by them
askable through ordinary consent. An automatic power has no option to ask
from, so slice 10 refuses it silently.

With this design, an enemy's automatic power refused only by waivable
restrictions is not refused outright. The ruler of the refusing card
decides, each time, whether the power works. The decision goes through the
consent system: its ledger, its preferences and its undo rules. Only the
wording changes. The power's user makes no choice and is never asked.

Many automatic powers carry a drawback (Tyrant must kill, Hunger must
bury). Leaving the request to the user would let them dodge it. Asking the
ruler every time lets the ruler switch off a harmful power, or leave a
helpful one off.

## User story

Red rules City Wall's site. Blue holds Tyrant faceup and travels to that
site.

1. Blue picks the destination. Blue is not asked anything. The pawn moves,
   and Tyrant's kill would run. The log reads "City Wall: Red decides
   whether Tyrant works." Blue sees "Waiting for Red's decision on
   Tyrant…" with Undo offered.
2. Red is prompted: "Use City Wall to deactivate Blue's Tyrant?" The
   options are **Always deactivate**, **Deactivate**, **Let it work** and
   **Always let it work**, with the usual notice about the preferences grid.
3. **Deactivate** and **Always deactivate** log "Red deactivated Tyrant
   with City Wall." Nothing is killed. **Let it work** and **Always let it
   work** log "Red let Tyrant work." Tyrant kills a warband and writes its
   own line. Either way the Travel goes on.
4. The two "Always" answers also set Red's preference for Blue on City
   Wall, the same preference the grid shows as Never Allow and Always
   Allow. A later request is then answered at once, with the same lines.
5. Blue travels again later in the turn. The remembered answer applies
   without asking. A remembered deactivation logs "Tyrant stays deactivated
   by City Wall."; a remembered grant writes nothing extra.
6. If Blue presses Undo while Red has not answered, the request is
   withdrawn, its line is struck through, and the destination pick is taken
   back, in one press.

## Rulings

1. **What counts as using an automatic power.** An automatic power is used
   when it makes something happen: a gain, a kill, a bury, a card's effect.
   Restrictions, limits, requirements on an action and cost changes are not
   uses. Card by card:
   - **Used:** every WHEN PLAYED power (the 26 denizens and the Vision
     Conspiracy), Tyrant, Hospitality, Hunger, Keep, Vow of Beastkin's extra
     warband and Vow of Wandering's extra Muster warbands.
   - **Not used, so never refused:** Initiation Rite (a cost change), Pied
     Piper's "ignores the adviser limit" and the adviser limits of
     Insomnia, Silver Tongue and Assassin (limit changes), Vow of
     Obedience and Vow of Poverty (restrictions), and the edifice setup
     rules (ruling 7).
   Slice 10 applies the same definition to its silent refusal.
2. **The ruler decides.** Spell Breaker, Forest Council and City Wall do not
   refuse an enemy's automatic power outright. The refusing card's ruler
   decides each time. Consent means the power works; a refusal
   deactivates it. This departs from consent ruling 3 ("the game always
   enforces") for automatic powers only.
3. **Judged where the effect runs.** Whether a power is refused is judged
   against the state when its effect would run, not when its window folds.
   Tyrant arriving at City Wall's site is refused, though the pawn was
   elsewhere when the Travel's cost node folded.
4. **Deactivated unasked.** As in the consent system, nobody is asked, and
   the power is deactivated, when a refusing card is ruled by the bandits
   or by nobody (consent ruling 4), when the use happens inside a Campaign
   action (consent ruling 2; Keep), or when any refusal on the use is not
   waivable. Giant Python's restriction holds for every player, so it is
   never waivable (consent ruling 1). A player who rules the refusing card
   is not its enemy, so their own powers are never refused by it (consent
   ruling 5). Slice 10's silent refusal writes no line, and nor does this.
5. **Answers last the turn.** An answer is remembered for the rest of the
   turn for the refusing power, the user and the used power's card
   (consent ruling 12), the key slice 10 uses for a chosen use. It covers
   every later use of that card's power by that user that turn, chosen or
   automatic, whatever the occasion. It survives undo (consent ruling 13).
6. **Several rulers.** When refusing cards with different rulers fall on
   one use, every ruler is asked at once and the first deactivation
   settles it (consent rulings 9 and 10). One ruler of several refusing
   cards gets one prompt naming all of them.
7. **Setup.** Edifice setup rules (Great Forge, Proving Grounds, Great
   Market, Empty Grounds and their other faces) are never refused during
   Setup. Setup is not a player using a power, and it has no turn for the
   ledger. `PowerWindow.WhenExplored` is declared but nothing folds it;
   whichever change makes exploration fold it decides its own filtering.
8. **No dead end.** A used power's effect, or its absence, never makes the
   rest of the action unfinishable. Every power in ruling 1's "used" list
   only adds a gain, kill, bury or card effect after the action's own
   operations, so this holds. A power added to the list later must keep it.
9. **Who withdraws.** While a request waits, the issuer of the batch that
   walked to the power-use step may press Undo. That withdraws the request
   and takes the batch back. On their own Travel that is the user. When
   another player's command walked there (an off-turn Travel such as
   Palanquin's), it is that player, and the user waits.

## Architecture

### Slice 10's share

Slice 10 adds the power-use filter: one rule saying whether a player may
use a card's power, read by the walker and by `PhasePowerProcedure.check`.
Part 1 ([plan](../plans/2026-10-14-catalog-batch-8-slice10-part1.md))
defines `PowerUse`, the description of a use, and `PowerUseRules`. Part 2
([plan](../plans/2026-10-15-catalog-batch-8-slice10-part2.md), Task 3)
adds the `AutomaticUse` node below with only the silent refusal, and wraps
every automatic power this design asks about except Hunger. Hunger keeps
part 1's check in `ForcedWakeProcedure.due`; this design wraps it. Keep
keeps a check where its window folds (see "Which powers are wrapped").

This design adds the waivable branch, the step and the consent wiring. The
[batch 8 design](2026-10-12-catalog-batch-8-design.md)'s slice 10 section
records this split.

### The `AutomaticUse` node

`AutomaticUse(user, power, card, effect)` is a composite `Operation` that
slice 10 adds. `effect` is built live, `(ReadyGame, PendingTree) =>
Vector[Operation]`, as a `Branch`'s selector is. The walker resolves the
node when it reaches it (`walker/AutomaticUses`), against the live state,
judging `PowerUse.automatic(user, power, card)`:

- **Allowed:** its children are the effect.
- **Refused, not waivable** (ruling 4): no children.
- **Refused, waivable:** its children are the power-use step and a branch
  on the step's answer: the effect on `use`, nothing on `skip`. Slice 10
  has no such case; it treats every refusal as not waivable.

The filter's verdict for this purpose ignores the consent ledger. A grant
or a refusal never changes the node's shape, only the step's answer. So the
node is shaped the same on the first walk and on every resume, and a
parked position inside it stays valid. The step's answer is in the pending
tree's `answered`, so a resume after it takes the same branch.

A refold could still flip a verdict when the effect itself changes what
the filter reads (moving the user's pawn, changing a ruler) before a later
decision inside the same node. No power in ruling 1's list does.

The node names its power, so a tree dump reads `AutomaticUse(…, Tyrant,
…)` with its effect below, not nested anonymous branches.

### The power-use step

`Decide("power-use.<power id>", user, ChooseOne(use, skip))`, where `use`
and `skip` are buttons. A player never answers it, and it is never
projected. It has its own window, not `PowerWindow.PayOrAsk`, so the toll
previews (`WalkerSimulation.waitsOnStep`, `PricedOptions`) do not treat it
as a toll.

Spell Breaker, Forest Council and City Wall each contribute a waivable
`OptionRestriction` at the step's window that bars `use` while that card
refuses the power. Its `Waiver` names the card's ruler as granter and
carries the card's consent-worded note. The existing ledger check in
`WalkerPowerGather.forbidding` drops the bar once a grant is remembered.
The request covers the key slice 10 remembers a chosen use's grant under:
`ConsentKey(refusing power, user, "power.use", PowerUseRules.target(use))`,
the used power's card. So one answer covers that card's use by that player
for the turn, chosen or automatic (ruling 5).

### Reaching the step

- **A refusal is remembered** for any refusing power: the walker answers
  the step `skip` in place, in the same command, and writes "{Tyrant} stays
  deactivated by {City Wall}." Only this in-place answer writes the line,
  not a `skip` that settles a fresh request.
- **A grant is remembered** for every refusing power: the walker answers
  the step `use` in place and writes nothing extra.
- **Otherwise** the walker parks at the step. Remembered grants have
  already dropped their bars, so the request asks only the other granters.
  The command's events end with `ConsentRequested`. The request names the
  user as requester, the step as its decision, `use` as its target, and the
  used power in a new field `use: Option[PowerId]`. Automatic answers from
  preferences follow, as for any request (`ConsentDesk.automatic`).

The request is opened by the walk, not by a `RequestConsent` command. The
check `RequestConsent` makes (the target is askable) holds by
construction: every block on `use` is waivable, or the node would not have
built a step.

### Settling

`ConsentDesk.settled` handles a request with `use` set differently from an
ordinary one. When the request completes:

- **Every ask granted:** record Granted, then answer the parked step with
  `use` as the requester.
- **Any refusal:** record Refused, then answer the parked step with `skip`
  as the requester. An ordinary refusal re-renders the decision instead.

Both answers open a `CommandAccepted(requester)` batch, as a grant does
today, and resume the walk.

### Undo coupling

A request opened by the walk sits at the end of the batch that walked to
the step. The answer to the step opens the next batch. Undo treats the two
as one:

- **While the request waits,** Undo from the issuer of the batch that
  walked to the step (ruling 9) emits `ConsentRequestWithdrawn` and takes
  that batch back. The request line is struck through and nothing is
  remembered (consent ruling 14).
- **After the answer,** undoing the batch that answered the step also takes
  back the batch that walked to it, in one press. Without this, the walk
  would be left parked at a step that nobody answers. Redo restores both.
  The answer stays remembered (consent ruling 13), so walking there again
  this turn reuses it.
- Consent answers stay off the undo stack. The ruler's answer is never a
  barrier for the user.

### Search and previews

- **Search** (`narrowAt`, `reach`, `verdict`, `lookAheadNoted`) never
  opens a request. At a step with a remembered answer it takes that
  branch. Without one it tries `use`, then `skip`; the path is legal when
  either reaches a legal end. By ruling 8 both always do. A step folds the
  same way inside a search's quiet powers.
- **Previews** (`WalkerSimulation.preview` and `previewParked`,
  `PricedOptions`, `QueuedStart.costOf`) never park at a step. They follow
  a remembered answer, or `use` when there is none, so a badge shows the
  power working, as the user would expect without City Wall.

### Which powers are wrapped

Slice 10 part 2 wraps, each in an `AutomaticUse` appended at its window:

- **WHEN PLAYED:** the `WhenPlayedPower` base, which covers the 26
  denizens in one place, and Conspiracy's WHEN PLAYED
  (`ConspiracyWhenPlayed`). The user is the card player. Forest Council
  reaches only Beast cards, so it never reaches the Vision.
- **Tyrant:** its kill and line. The user is the traveller.
- **Hospitality:** its `Branch((live, _) => welcome(...))` becomes the
  node's live effect, so the node replaces a layer rather than adding one.
  The user is the traveller (`route.player`), on or off turn.
- **Vow of Beastkin and Vow of Wandering's Muster:** each appends its own
  `Gain.Warbands` of the extra amount and its "Gained {n} more warband."
  line after the Muster's gain, instead of rewriting that gain.

This design adds:

- **Hunger:** `ForcedWakeProcedure.build` wraps each forced power's tree
  in an `AutomaticUse` whose user is its holder, and `due` stops leaving
  out a refused one, so the node judges it. Hunger is the only forced
  power.

Not wrapped:

- **Keep.** Its transforms replace a Raid's consequences and relocation
  rather than append, so they cannot sit inside a node. It runs inside a
  Campaign, so it is deactivated unasked (ruling 4) and needs no step.
  Slice 10 judges it where its window folds, through the collector's
  `refused` test.

### Model and projection

- `ConsentRequest` gains `use: Option[PowerId]`, the automatic power the
  request is about. Wording keys off it. Old journals need not decode.
- The granter's `consentPrompt` carries `use`. The client words it "Use
  City Wall to deactivate Blue's Tyrant?". Its buttons read Always
  deactivate, Deactivate, Let it work and Always let it work, and send
  `ConsentChoice` Never, No, Yes and Always.
- The requester's `consentWaiting` reads "Waiting for Red's decision on
  Tyrant…". When the issuer of the batch that walked to the step is
  another player, Undo is offered to that player instead.
- The step decision is never projected to anyone. Other players see the
  usual waiting banner.
- The preferences grid is unchanged. Spell Breaker, Forest Council and City
  Wall appear in it because their contributions declare a `waiver`. One
  setting per enemy covers both their chosen-power asks (slice 10) and
  their automatic ones.
- Nothing marks an answer as automatic (consent ruling 11's speed leak is
  still accepted).

### Game Log

- **Request:** "{City Wall}: {Red} decides whether {Tyrant} works." With
  several granters or cards: "{City Wall}, {Forest Council}: {Red} and
  {Green} decide whether {Tyrant} works."
- **Answers:** "{Red} deactivated {Tyrant} with {City Wall}." and "{Red}
  let {Tyrant} work." With several of one granter's cards, the deactivation
  line names them all ("with City Wall and Forest Council").
- **Remembered refusal:** "{City Wall}: {Tyrant} stays deactivated." It is
  a power line, so it starts with its source, as every power line does.
- **Withdrawal:** the request line is struck through, as today.
- Private preference lines are unchanged.
- A deactivated power writes none of its own lines. Hunger's "No adviser
  could be buried." belongs to its effect, so it is not written either.

## Testing

- **Walker:**
  - The node's three verdicts.
  - Shape stability: Blue with Tyrant and Hospitality travels to City
    Wall's site, the walk parks at Hospitality's bank question after the
    move, and the resume lands on the same decision.
  - A remembered answer answers the step in place.
  - Search tries both branches. Previews follow `use` or the remembered
    answer, and never park at a step.
- **Application scenarios:**
  - The four manual answers.
  - Automatic answers from preferences.
  - Several rulers, with the first deactivation settling the request.
  - A bandit ruler, a Campaign (Keep), and a refusal mixed with one that is
    not waivable: each deactivated unasked.
  - The ruler's own power is not refused.
  - Ruling 1's unfiltered powers work under City Wall.
  - Undo coupling: withdraw while waiting, undo after a grant, undo after a
    refusal, and redo.
  - The off-turn issuer withdraws (ruling 9), once slice 15 gives an
    off-turn Travel.
  - Turn reuse, with the "stays deactivated" line.
  - A grant settles a Narrow Pass ask, and the resumed walk reaches a
    power-use step and opens a second request.
- **Hidden information:** Blue's projection and log are identical after an
  automatic and a manual answer of each kind.
- **Frontend:** the reworded prompt and the waiting banner.
- The coverage gate applies as usual.

## Slices

1. **The step and Tyrant.** The step, the waivable branch of
   `AutomaticUse`, the opened request, settling, undo coupling, the log
   lines, the projection and the client wording, end to end for Tyrant.
2. **Hunger and the other powers.** Hunger wrapped in `ForcedWakeProcedure`,
   and a scenario for each other wrapped power under a waivable refusal:
   a When Played power, Conspiracy, Hospitality and the two vows.

Docs, with slice 1: rulings 2 to 9 go into the
[batch 8 rulings](2026-10-12-catalog-batch-8-rulings.md) under slice 10,
next to ruling 1, which slice 10 already needs and is recorded there now.
The roadmap item is marked done after slice 2.

## Deferred

- A hover note on an option whose preview passes an unanswered step ("City
  Wall may deactivate Tyrant"). Previews follow `use` meanwhile.
- A ruler who never answers. This is covered by the roadmap's unanswered
  off-turn decisions item, as for every consent request.
- Merging several refused powers of one action into one prompt. Tyrant and
  Hospitality on one Travel to City Wall's site prompt twice; preferences
  cut the repeats.
- Filtering edifice rules when exploration folds `WhenExplored` (ruling 7).

## Departures

Item 10b was planned after slices 10 to 15 had landed. These choices fit
the code as it then stood.

1. **The step declares its ask.** Spell Breaker, Forest Council and City
   Wall contribute no option restriction at the step. The step's builder
   words its `use` option as askable from the refusals, as Palanquin's
   destination does (slice 15). The step has no window. Keys, granters and
   preferences are as designed.
2. **A remembered answer is not journaled as a step answer.** With a
   remembered grant, a remembered refusal or a preview, the node builds no
   step. A parked step exists only while a request waits.
3. **The node always has two children**, a slot and a body, when it is
   allowed or waivable, and none when it is refused unasked. A resume
   inside the effect is never confused with a resume at the step.
4. **The request carries `ConsentUse(card, issuer)`**: the used card, which
   words the prompt and the log, and the issuer of the batch that walked to
   the step, who opens the answer's batch and may withdraw.
5. **The answer's batch is opened by the issuer**, not the requester, so a
   coupled undo takes back batches of one issuer. On a player's own Travel
   the two are the same player.
6. **Barriers.** When the batch that walked to the step is a barrier
   (Hunger's forced Wake follows a turn change), nobody may withdraw the
   request or undo its answer. Undo takes back a coupled chain whole or not
   at all.
7. **Redo after a withdrawal asks the rulers again**, with the same
   automatic answers from preferences.
8. **A manual Never or Always on an automatic power's request** sets the
   preference before the walk resumes, so a second step the same command
   reaches is answered by it at once.
9. **"Stays deactivated" reads as a power line**, "City Wall: Tyrant stays
   deactivated.", under a key the three cards share.
10. **Previews** walk with the powers marked as previewing. An unanswered
    step is walked as `use`, so a preview never parks there.

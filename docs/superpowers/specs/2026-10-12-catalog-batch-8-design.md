# Catalog Batch 8: Engine Seams

**Status:** designed 2026-10-12. Slices 1 to 9 are done, and slice 10's
first two parts (Spell Breaker, Forest Council, City Wall). The per-card rulings are in
[the rulings appendix](2026-10-12-catalog-batch-8-rulings.md).

**Builds on** [Catalog batch 5](2026-10-08-catalog-batch-5-design.md),
[Catalog batch 6](2026-10-09-catalog-batch-6-design.md) and
[Catalog batch 7](2026-10-09-catalog-batch-7-design.md), whose vocabulary,
cost rules, walker shapes and log-line rules apply unchanged.

## Goal and scope

**Phase - Catalog batch - engine-blocked** in the roadmap groups its cards by
the engine change each needs. A survey on 2026-10-10 ranked the groups by
cards unlocked per engine change. This batch takes the 30 cards whose
mechanisms are small and well bounded, in 15 small slices, one mechanism
each. A card ships whole or not at all: no slice implements part of a card's
text and records the rest as a gap.

| Slice | Count | Cards |
|---|---|---|
| 1. No new engine | 3 | 202 Ward of Silence, 171 Hospitality, 203 Vow of Wisdom |
| 2. Oathkeeper goal | 2 | 209 True Oath, 244 The Red Seer |
| 3. Warbands at a Campaign's start | 1 | 185 Vow of Union |
| 4. A Raid's defeat replaced | 1 | 5 Keep |
| 5. Burns taken | 2 | 74 Vow of Silence, 86 Vow of Division |
| 6. Operation triggers | 2 | R38 Secret Testament, R25 Brass Army |
| 7. Trade at another site | 3 | 177 Small Friends, 224 Friendly Familiar, 55 Secret Signal |
| 8. Effective suit | 2 | 223 Pledge to Discord, R41 Spiteful Mirror |
| 9. Darkest Secret lens | 1 | R40 Yew Staff |
| 10. Power-use restrictions | 4 | 162 Spell Breaker, 194 Forest Council, 252 City Wall, 186 Giant Python |
| 11. Actor-aware lock | 1 | 165 Ancient Bloodline |
| 12. Card play at another site | 3 | 250 Baron, 188 New Growth, 218 Forest Warden |
| 13. Campaign from another site | 2 | 198 Wild Allies, 115 Captains |
| 14. Off-turn actor | 2 | 98 Sneak Attack, 16 Second Wind (defender side) |
| 15. Palanquin | 1 | 107 Palanquin |

Slices run in this order. The dependencies that fix it:

- Slice 10 needs slice 9: Giant Python's ACTION checks the Darkest Secret
  through Yew Staff's lens.
- Slice 11 needs an acting player on an operation, as slice 5 gives one to
  a burn.
- Slice 15 needs slice 14's off-turn actor, and is tested with slice 1's
  Hospitality and slice 6's Brass Army.
- Slices 1 to 8 need nothing from each other and come first because they
  are cheap.

Slices 9 and 11 may fold into slice 10 at plan time if they turn out tiny.

### The bar

The approved changes outside `gameplay/powers/`:

- **Rewrites inside a card play** (slice 1). A model trait,
  `RewritableComposite`, lets a custom composite rebuild itself with its
  children rewritten, and `OperationRewrite` descends it.
  `CardPlayProcedure.PlacementBody` implements it and keeps the rewrite
  through every later `adjust`, and `PlacementTree` always holds one body,
  so a rewrite at `SearchPlayAdviser` reaches the site play's favor gain
  (Vow of Wisdom).
- **A Set Oathkeeper Goal operation** (slice 2). `SetOathkeeperGoal(goal)`
  writes the campaign state's `oathkeeperGoal`, beside `SetOathkeeper`
  (`model/CoreOperations.scala`). Replay applies it and the codec records it.
- **A warband arrangement with no minimum** (slice 3). `WarbandArrangement`
  forces at least one warband per site and allows no move back when
  `toSites` is false. Vow of Union needs a variant over a fixed set of
  sites, in one direction, with any number moved and none required.
- **An end to a walk** (slice 3, approved 2026-10-12). `EndWalk` is a
  control node: the procedure finishes where it is reached, and nothing
  after it runs. It changes no state and is never journaled.
- **A Campaign that ends early** (slice 3, approved 2026-10-12). The last
  step before the Campaign's targets ends it when no kind is settled: no
  kind is legal any more, or the attacker chose "Skip Campaign". A power at
  the Campaign's start offers the Skip by recording the marker pool
  `CampaignIds.skipOffered`; the kind question is then asked even with one
  legal kind. The Supply stays spent.
- **A Raid transfer in two parts** (slice 4). `CampaignRaid.transfer`
  builds the takes, the facedown discards, the set-aside relics and the
  half-favor burn as one batch. It splits into `takes` and `consequences`.
  The consequences run in their own window, `CampaignRaidConsequences`,
  nested in the transfer's window after the takes, so a power can drop them
  while the takes stay. The relocation keeps its tree address. The
  Campaign's closing line leaves out any step that a power's covering line
  restates.
- **A burn's actor, and burns taken** (slice 5, detailed at plan time).
  Each `Burn` names the player who burns, as `by`:
  - the actor of the procedure that builds it;
  - each owner in an effect that makes each player burn;
  - the defeated defender, for a Raid's half-favor burn.

  A cost's burnt portions are its payer's. Before the walker runs a batch,
  each burn a power claims becomes a `Take` of the same tokens to its
  taker, so a restriction on a `Take` holds against it. A `Burn` or
  `PayCost` in the tree that a power claims runs whole as one batch.
- **A secret move's side** (slice 5, added at plan time). A secret move
  from a board may name the side it takes (`PositionedLocation.side`,
  `Take.sourceSide`), and the secrets keep their side where they land.
  Book Burning and Ancient Binding burn facedown secrets by naming that
  side, instead of flipping them faceup first.
- **Triggers on executed operations** (slice 6, detailed at plan time). An
  `ExecutedTrigger` power's `onExecuted` is asked after each walker batch,
  with the operations the batch executed. A follow-up it starts is a tree,
  walked right after the batch under the batch's own node, so Brass Army
  can ask its holder. The batch holds its operations with `Defer`, in its
  own step, so a command that resumes inside a follow-up asks again with
  the same operations. A follow-up's own batches start no follow-up. A
  parked position inside a follow-up resolves to its node through
  `WalkerPowerGather.leafAt`, which asks the triggers the same way.
  Secret Testament needs no trigger: it is a Challenge modifier.
- **An as-if pawn site for Trade** (slice 7, detailed at plan time). A
  modifier pick may be followed by its own questions inside the modifier
  loop, in the same pass, after its payment
  (`ContributingPower.selectionQuestions`). Small Friends and Friendly
  Familiar ask for a site under one shared decision,
  `PowerAccess.asIfDecision`. `PowerAccess.locate` reads the walk's
  answers: the as-if site replaces the pawn site in `usableSites`. Ruled
  sites and the player's advisers and relics stay usable; the physical
  pawn site does not, unless the player rules it. `ModifierChoice` passes
  its answers to `locate`, and `SelectedModifier.selectable` and its
  overrides receive them. `EconomySources.relocated`, a sibling of
  `widened`, replaces the Trade source options with the new site's cards.
- **"Start" judged with the walk's answers** (slice 7, approved
  2026-10-14). The dry runs that judge a modifier pass's "Start" begin
  with the pass's answers (`ProcedureWalker.advance`'s `seed`), so they see
  the as-if site as the walk will.
- **The modifier decision names its start selection** (slice 7, added at
  plan time). `ModifierChoice` is a `ModifierHost`, which carries what the
  player selected when the action started, so a modifier judged there can
  tell a Trade for secrets. The walk's state does not hold the selection yet
  on the command that starts the action.
- **An effective suit** (slice 8, detailed at plan time).
  `EffectiveSuit.of` gives a card's suit as the rules see it: Discord for a
  faceup adviser of a player who has Pledge to Discord faceup, otherwise
  its printed suit. `EffectiveSuit.traded` gives a Trade's or Muster's
  source suit: Discord when the walk's answers pick Spiteful Mirror,
  otherwise `of`. Every reader of an adviser's suit goes through `of`,
  including the bank that favor on an adviser returns to, and every reader
  of a traded or mustered card's suit goes through `traded`.
  `EffectiveSuit` replaces `AdviserSuits` and keeps Marriage's weight. It
  lives in `gameplay`, because the operation pipeline reads it too: an
  off-turn payment onto a Pledge holder's adviser settles to the Discord
  bank. The projection still sends printed suits.
- **Family Wagon asks when Pledge is buried** (slice 8, added at plan
  time). `SuitOnlyAdvisers` becomes a trigger on executed operations, so
  that the active player can order the burials when Pledge to Discord sits
  beside advisers that keep the rule by their printed suit.
- **One Darkest Secret check for card powers** (slice 9, detailed at plan
  time). `CardPowerBanners.holdsDarkestSecret(ready, player)` says whether
  a player holds the Darkest Secret as a card power they use sees it: they
  hold the banner, or a power that overrides the check
  (`CardPowerBanners.DarkestSecretOverride`) says they act as if they do.
  Yew Staff is the one override, listed once in `CardPowerBanners`. The
  card powers that ask (Disciples, Terror Spells, The Red Seer, Fae
  Battalion, Rusting Ray, Fire Talkers, Unstable Summon, and Armed Mob for
  its user) call it and never name Yew Staff. Challenge, scoring, the
  Oathkeeper goal and every other reader keep `BannerRules.holder`, so the
  check needs no flag.
- **A power-use filter** (slice 10, detailed at plan time). A `PowerUse`
  says who uses a power, its card, whether it is printed persistent,
  whether it costs a secret, and whether a refusal may be asked of a
  ruler. A walker power declares its `powerUseRestrictions`, which
  `WalkerPowers` and `PhasePowers` both carry, and states its own use
  through `ContributingPower.use`. The filter is read by:
  - the phase menu and `PhasePowerProcedure.check`, for phase powers and
    Hunger's forced Wake;
  - the modifier picker, for modifiers;
  - the battle-plan window and the reroll choice, which withhold a refused
    offer with its reason;
  - the `AutomaticUse` node, which judges an automatic power where its
    effect runs, and the contribution collector, which judges Keep where
    its window folds (part 2), as the
    [automatic power consent design](2026-10-15-automatic-power-consent-design.md)
    describes.

  A grant is remembered under the key `"power.use"` and the card, so it
  covers the card's use by that player for the turn on every path.
- **Placement bans** (slice 10, added at plan time). Forest Council, City
  Wall and Giant Python refuse a placement of favor or secrets onto a card
  through their `operationRestrictions`: a `PayCost` that places onto the card,
  or a `Give` onto it, with its payer or giver as the placer. An off-turn
  payment settles to the bank and is not a placement.
- **A slot question for moving a site card** (slice 10, added at plan
  time). Giant Python and Roving Terror ask one question over site slots,
  as card play does: an empty slot, or a card marked Replace.
- **An actor-aware operation restriction** (slice 11). An
  `OperationRestriction` learns the acting player, so a lock can hold
  against enemies only. Slice 5 names only a burn's actor, on the `Burn`
  itself, so slice 11 settles how a restriction learns its own.
- **A card-play site** (slice 12). `SearchPlacement.Site` names its site.
  `CardPlay.plan`, `operationsOf`, `atSite` and `CardSlots.siteSlots` read
  it instead of the pawn site. `CardPlayRules` lets a power widen the slots
  to other sites, and the Replace path accepts a bury in place of the
  discard.
- **A queued Campaign's origin** (slice 13). A queued Campaign may carry an
  origin site in its start arguments. `CampaignSetup.originOf` reads it
  before the pawn site, and the force rule adds the actor's warbands at the
  origin.
- **An off-turn actor** (slice 14). `WalkCtx.activePlayer` comes from the
  queue entry's actor, not the turn. The starts that require the active
  player accept a queued actor, and Campaign powers that read
  `ctx.activePlayer` as the attacker read the Campaign's attacker instead.
- **A queued Travel's destination** (slice 15). A queued Travel may carry a
  destination in its start arguments, and its actor may be another player.

The serialization codec gains `SetOathkeeperGoal`, a burn's actor, a
card-play site, and the origin and destination start arguments.

### Catalog and roadmap corrections

- **The Oathkeeper goal is modelled.** The roadmap listed True Oath and The
  Red Seer as blocked on it, but `oathkeeperGoal` is in the campaign state
  and `OathkeeperRules` reads it. Only the mutating operation is missing.
- **Vow of Union is not blocked on an off-turn actor.** Its holder answers
  a question inside another player's Campaign, as a defender does. The
  walker already gathers a power whoever holds its card.
- **Hearts and Minds** landed in batch 7 and leaves the engine-blocked list.

### Out of scope

- **Negotiation**: The Gathering, Whispering Stone, Council Arbiter, Deed
  Writer and Traveling Negotiator. About four slices of heavy UI for five
  cards; they belong with the Negotiation terms phase.
- **Challenge**: Sigil of the Eye, Sigil of the Heart, Favored Son and
  Magician's Code.
- **New target kinds** (Relic Hunter, Glamor), **discard destinations**
  (Bracken, Cracked Horn, Ring of Devotion) and the one-offs (Search Party,
  Pilgrimage, Autumn Wind, Tracker, Obsidian Cage, Master of Disguise,
  Acting Troupe): each costs a slice or more per card.
- **False Prophet** needs the off-turn actor and a player-aware card play.
  It waits for a later batch.

## Slice 1: No new engine

- **Ward of Silence** is an ACTION power with a free printed cost. Its
  `build` chooses the target and asks X with a `ChooseAmount`. It then
  pays X secrets onto its card and flips X of the target's secrets facedown
  with `FlipSecrets`, in one batch. Sleight of Hand gives the target choice
  and Blood Pact the amount. Its own `usable` checks the empty-card rule,
  which a free printed cost skips.
- **Hospitality** runs after the pawn's move in Travel, as Tyrant does. It
  asks which bank when more than one matches, which parks a decision after
  the move.
- **Vow of Wisdom** is an `OperationRewrite` at `SearchPlayAdviser`. It
  follows its holder's `Play` to a site with a gain of 1 secret, and drops
  the play's favor gain.

### Plan-time rulings

- **Rewrites reach into a card play.** The operation rewrite design left
  custom composites alone, and the site play's favor gain is built inside
  one. A rewrite that ran before the first `adjust` was also lost. The
  `RewritableComposite` trait fixes both for `PlacementBody`, and
  `PlacementTree` always holds one body. Every card play's node path gains
  a level, so a game parked inside a card play when the server updates may
  not resume.
- **Vow of Wisdom always gains a secret.** Its holder's play to a site
  gains 1 secret whether or not the suit bank holds favor, and not the
  play's own favor. A favor another power gives for the play, such as
  Welcoming Party's, is still gained.
- **Ward of Silence pays inside its action.** Its `usable` checks that its
  card could take one secret (a faceup secret and an empty card), and that
  another player at the site holds a faceup secret. Otherwise the Act menu
  shows the engine's "Nothing to do now".
- **Ward of Silence's line** is the action's own: "Flipped 2 secrets on
  {Blue}'s board facedown." Neither question posts a "Chose" line.
- **Hospitality's banks.** A matching bank is the suit of a card at the
  destination and of one of the traveller's faceup advisers, Marriage as
  `AdviserSuits` reads it. One matching bank with favor is used without
  asking. With two or more it asks, listing every matching bank, and an
  empty one is shown unavailable with "This bank is empty".
- **Hospitality writes no line.** Its question posts no "Chose" line, and
  the generic gain line names the bank, and the traveller when they are not
  the active player. The rule follows the pawn that moves, so it covers an
  off-turn Travel; slice 15 tests that with Palanquin.

## Slice 2: Oathkeeper goal

True Oath is a WHEN PLAYED power and The Red Seer an ACTION power. Both ask
the new goal with a `ChooseOne` and apply `SetOathkeeperGoal`.

### Plan-time rulings

- **One goal question.** Both cards list the four goals in printed order.
  The current goal is shown unavailable with "The current goal". The Red
  Seer also shows Devotion unavailable, with "The Red Seer cannot choose
  Devotion". True Oath adds "Keep the current goal", which declines.
- **The title follows at the action's end.** The Oathkeeper check at every
  action boundary reads the new goal, so the title may pass in the same
  action. The log's "Oathkeeper passed to {Blue}" line tells it.
- **The Red Seer without the Darkest Secret** is shown unavailable with the
  engine's "Nothing to do now", and its cost is not paid.
- **True Oath counts live,** when the power fires, with True Oath already
  among its player's advisers. With fewer than three other Beast cards it
  asks nothing.
- **Lines.** Both cards write "Changed the Oathkeeper goal to Protection."
  True Oath writes "Kept the Oathkeeper goal." when declined, and "Ruled
  fewer than three other Beast cards." when it cannot ask. Neither question
  posts a "Chose" line.
- **An unchanged goal is rejected.** `SetOathkeeperGoal` with the goal
  already set fails with `oathkeeper-goal-unchanged`, as `SetOathkeeper`
  treats an unchanged holder.

## Slice 3: Warbands at a Campaign's start

Vow of Union's Campaign half is a `Transform` at `CampaignBeforeTargets`
that prepends a `Decide` owned by the holder, with an id under
`CampaignProcedure.decisionPrefix`, then the moves. It fires in every
Campaign, so the holder may be the attacker, the defender or neither. Its
restriction half clones Family Wagon: other advisers are buried when the
card arrives and when one would be gained.

### Plan-time rulings

- **When it asks.** After the Campaign's Supply is spent, before the kind,
  the defender and the targets.
- **Two questions.** A direction ("Move warbands to your board", "Move
  warbands to your sites" or "Move none"), then one distribution in that
  direction. Its slot bounds enforce the direction, and each slot opens on
  what it holds now.
- **"Your region"** is the region of the holder's pawn when the Campaign
  starts.
- **When nothing is asked.** Nothing is asked when the holder rules no site
  in their region. "Move warbands to your sites" with an empty board is
  shown unavailable: "No warbands on your board".
- **A site may empty** (product owner). If no Campaign kind is legal
  afterwards, the Campaign ends: the Supply stays spent, and there is no
  battle, result or after-Campaign effect. The log says "Nothing left to
  attack: the Campaign ended."
- **Skip Campaign** (product owner). When Vow of Union moved at least one
  warband, the attacker's kind question is asked even with one legal kind
  and adds "Skip Campaign" last, which ends the Campaign the same way. The
  log says "Skipped the Campaign." Nothing moved, no Skip.
- **Lines.** "Moved 2 warbands from sites to the board." or "Moved 3
  warbands from the board to sites." on the holder's turn; "{Blue} moved 2
  warbands from sites to their board." or "{Blue} moved 3 warbands from
  their board to sites." off turn. Nothing moved, no line. Neither question
  posts a "Chose" line.
- **The restriction half** is Family Wagon's code with Beasts and no limit
  lift, shared through `SuitOnlyAdvisers`. A play it would bury carries
  "Vow of Union will bury it".
- **The force question reads the moved warbands.** The walker rebuilds the
  Campaign from live state on every command, so an attacker may move
  warbands to their board and commit them.

## Slice 4: A Raid's defeat replaced

Keep transforms `CampaignRaidConsequences` and `CampaignRaidRelocation`
when the defeated defender rules Keep's site. The takes stay. The
consequences are dropped, and the relocation question is replaced by
placing the defender's pawn at Keep's site. Nothing else in the Raid
follows the relocation. The losses before the Raid, and the effects at the
Campaign's end, run as usual.

### Plan-time rulings

- **When.** Keep is faceup at a site the defeated defender rules, judged
  after the battle's losses. The losses and the takes never change who
  rules a site, so this holds for the rest of the Raid.
- **Automatic.** Keep asks nothing, and the defender cannot decline it.
- **What stays.** The battle's losses, the attacker's takes, and the tokens
  that leave with a taken banner: the People's Favor's favor returns to
  the banks, and the Darkest Secret's secrets are burnt.
- **What is dropped.** The half-favor burn, the facedown advisers'
  discard (a Conspiracy's return to the box too), the facedown relics set
  aside, and the relocation question.
- **The pawn** moves to Keep's site, or stays when it is already there.
- **"End the action"** ends the Raid. The effects at the Campaign's end
  still run: Book Burning still burns, Herald still pays, and battle plans
  still discard.
- **Lines.** "Ended the Raid with {Blue}'s pawn at {site}." whether the
  pawn moved or stayed. The closing line no longer says the pawn "was sent
  to" Keep's site.
- **Saved games.** The relocation keeps its tree address. Old journals
  replay their one transfer batch unchanged.

## Slice 5: Burns taken

Each `Burn` names its burner. Before a batch runs, Vow of Silence and Vow of
Division claim the secrets or favor that their holder's enemies burn, costs
included, and each claimed burn becomes a `Take` to the holder. Each card's
"cannot challenge" half refuses the Challenge at the Challenge's own
windows.

### Plan-time rulings

- **A taken burn becomes a `Take`**, so a restriction on a `Take` holds
  against it. The count is cut to what is there, as a burn's is.
- **Secrets keep their side.** A burn from a board takes faceup secrets
  unless it names the facedown side. Taken secrets land on the side they
  left. Book Burning and Ancient Binding burn facedown secrets by naming
  that side.
- **The holder's own tokens stay.** When an enemy burns from the holder's
  board, nothing moves, and the Vow's line still says what it took. The
  burning card's own line then reports nothing burnt.
- **Costs:** a cost's burnt portion becomes a `Take` that must be paid in
  full.
- **Banners:** a burn inside a banner's `Take` is replaced there and rides
  with it.
- **A Raid's half-favor burn is the defeated defender's own.**
  - An attacker or a bystander who holds Vow of Division takes it.
  - A defender who holds it does not, which is the card's "unless it was
    burned by a raid targeting you".
- **Who burns.**
  - The attacker burns the Darkest Secret's secrets when taking it in a
    Raid, and so does Conspiracy's player when they take it.
  - **Book Burning:** the attacker.
  - **Ancient Binding:** each player burns their own board. A held card's
    secrets are its holder's burn, and the user burns a site card's.
  - **Revelation:** each player.
  - **Defame, Riots, Charlatan, Firebrand and Amber Flame:** the user.
  - **Unstable Summon:** the attacker.
  - **Costs:** the payer.
  - **Bandit Market's setup burn:** no player.
- **An enemy is every other player.** When two powers claim one burn, the
  first in the contribution order takes it.
- **Lines.**
  - One line per taker and burner per step, right after it: "Took {Red}'s
    2 burned secrets." on the holder's turn, "{Blue} took {Red}'s 2 burned
    secrets." off it.
  - A Raid's closing line no longer counts favor a Vow took.
- **Faceup only.** The holder holds the Vow faceup as an adviser.
- **Vow of Division:** the Challenge is unavailable to its holder: "Vow of
  Division prevents you from challenging".
- **Vow of Silence.**
  - The holder's banner choice shows the Darkest Secret unavailable:
    "Cannot challenge the Darkest Secret."
  - When it is the only banner they could challenge, the Challenge is
    unavailable: "Vow of Silence prevents you from challenging the Darkest
    Secret".
- **Not replaced:** an adviser arrival follow-up's burns, and a modifier
  menu's dry run of its costs.
- **No old journals:** a recorded burn always carries its burner.

## Slice 6: Operation triggers

- **Secret Testament** is a persistent Challenge modifier at
  `ChallengeRibbon`. When an enemy challenges for a banner its holder
  holds, the holder takes every favor or secret on the banner, in place of
  the ribbon's returns. A Campaign or a card that takes a banner walks no
  ribbon, so it changes nothing there.
- **Brass Army**'s restriction half is the first `ExecutedTrigger`. It
  watches its holder's pawn moves, on or off turn, and asks the holder to
  bury the relic or flip a faceup secret facedown. Its Campaign half
  already exists, and becomes a plan for either side, ±4 attack dice: the
  component catalog's "+4" is a typo.

## Slice 7: Trade at another site

Small Friends and Friendly Familiar are Trade modifiers. Picked in the
modifier loop, each asks which site its user acts as if at. Later modifier
passes and the Trade's source options read that site in place of the pawn
site. Secret Signal is an optional Trade modifier that gains one more favor
when the Trade's whole favor gain is one.

### Plan-time rulings

- **Pick order.** Once a picked modifier's card stands at the unruled pawn
  site, Small Friends and Friendly Familiar are unavailable. Once one of
  them is picked, the other is.
- **Rules that read the pawn site follow the as-if site:** Skilled
  Merchants' and Map Library's region, and Map Library's own site.
- **Secret Signal** sums, at the Trade's end, every favor its trader gained
  in the Trade, Moving Market's included. Each records under one deferral
  key, `TradeFavor.key`.
- **Secret Signal's availability.** Unavailable only for a Trade for
  secrets: the Trade's source, and so its gain, is chosen after the
  modifiers.

## Slice 8: Effective suit

Pledge to Discord makes its holder's advisers Discord through the effective
suit. Spiteful Mirror, once paid in a turn, records its use in
`TurnState.usedPowers`; every trade and muster that turn then offers, as a
free modifier, to treat the cards used as Discord. No new turn field is
needed.

### Plan-time rulings

- **Pledge reaches every reader of its holder's faceup adviser suits,**
  the design's list and every other: Honor Guard, Second Chance, Resettle,
  Twin Brother, Military Parade, Rotting Fortress and the battle plans
  that read an enemy's advisers among them. Silver Tongue and the travel
  payments read only site cards.
- **Favor going back from a Pledge holder's adviser** goes to the Discord
  bank, at Rest, in an off-turn settlement, and on a discard or bury.
- **A card played faceup to a Pledge holder's advisers reads Discord,** so
  Wild Cry and Saddle Makers do not fire for it.
- **The client shows printed suits.**
- **Spiteful Mirror** turns only the source card Discord. Its first pick in
  a turn pays the secret and records the use; later picks are free.
- **Family Wagon with Pledge** buries at once what goes whatever the order,
  then asks the active player which advisers that keep the rule by their
  printed suit to bury before Pledge.

## Slice 9: Darkest Secret lens

Yew Staff makes `holdsDarkestSecret` true for its holder when the check is
made for a card power. Challenge, scoring and the Oathkeeper goal do not go
through it.

### Plan-time rulings

Settled 2026-10-14; the rulings appendix has them per card.

- Yew Staff works only while its holder uses the card power. It applies
  automatically and writes no line.
- Unstable Summon burns from the Darkest Secret wherever the banner is.
- The Yew Staff holder may target themselves with their own Armed Mob.
  With two targets, Armed Mob first asks whose adviser to discard.
- Slice 9 stays separate from slice 10. Giant Python, Ancient Pact and
  High Priest read the check when they land; Tome Guardians reads the real
  holder.

## Slice 10: Power-use restrictions

Spell Breaker, Forest Council and City Wall read the power-use filter;
Forest Council and City Wall also refuse placing favor or secrets on cards
through `operationRestrictions`. Giant Python's restriction half reads both,
for every player. Its ACTION moves the card to another site, discarding a
card there first when the user chooses or the site is full.

Automatic powers go through a new `AutomaticUse(user, power, card, effect)`
node, which judges the filter against the live state when the walk reaches
it: the effect runs when allowed, and nothing runs when refused. Judging at
the window's fold would see Tyrant's traveller before the pawn moves, and a
resume after the move could flip the verdict and move a parked position.
Only the powers the rulings appendix lists as used are wrapped: WHEN PLAYED
(the `WhenPlayedPower` base and Conspiracy), Tyrant, Hospitality, and Vow
of Beastkin and Vow of Wandering's Muster, which append their extra
warbands instead of rewriting the Muster's gain. Keep, which replaces a
Raid's consequences, is judged where its window folds. Hunger's forced
Wake is left out where it is due. Slice 10 refuses silently; roadmap item
10b ([automatic power consent design](2026-10-15-automatic-power-consent-design.md))
adds the waivable branch, where the refusing card's ruler decides through
consent, and wraps Hunger.

### Plan-time rulings

- **Using a non-persistent power** means using any power printed
  `persistent: false`: phase powers, Hunger's forced Wake, modifiers and
  Catacombs, battle plans and their rerolls, When Played powers, and the
  automatic rules that make something happen (Tyrant, Hospitality, Keep,
  Vow of Beastkin's and Vow of Wandering's extra warbands). Restrictions,
  limits and cost changes are not uses, so they still hold: a lasting limit
  printed under a phase power's id (Assassin, Insomnia, Silver Tongue, Pied
  Piper, Vow of Obedience, Vow of Poverty), Vow of Beastkin's source limit
  and Initiation Rite. The edifice setup rules run during Setup, where
  nobody uses a power.
- **A power that costs a secret** has a secret or a burnt secret in its
  printed cost, Jinx and Spiteful Mirror's free later picks included. Ward
  of Silence and Arcane Armor pay X secrets, at least one, and count too, as
  does every battle plan Gleaming Armor taxes, the title's included: the
  title's plan is a power, though no card prints it. Initiation Rite costs no
  secret, and mustering is not a power. Unstable Summon burns from the
  Darkest Secret, which is not a cost.
- **A Beast card** is judged by `EffectiveSuit.of` for a faceup adviser,
  and by its printed suit at a site.
- **Placing favor or secrets on a card** is a cost paid onto it (a power's
  cost, a Trade's or Muster's source card, Forge, a battle plan's cost,
  Jinx) or a `Give` onto it (Whispering Leaves' target, Dissent). The placer
  is the payer or the giver. An off-turn payment settles to the bank and
  places nothing.
- **A use that places** (part 2). A use whose cost places favor or secrets
  on its card is a placement too, made by its user on their own turn. So a
  persistent power with a placed cost, such as Jinx, is refused where
  placing is.
- **One grant per card** (part 2). A placement refusal is remembered under
  the same key as a power-use refusal: the restriction, the placer,
  `"power.use"` and the card. Asking to use a card covers using its power
  and placing on it, for the turn.
- **Where a placement is askable** (part 2). A placement is askable on a
  choose-one whose option leads to it, such as a Muster's or a Trade's
  source card. The walker's search finds it: the option's path meets only
  waivable operation refusals, and granting them opens it. The Hall of
  Ministers is askable the same way. A placement that no choose-one offers
  is refused without a question: Forge's partition, Whispering Leaves' and
  Dissent's gifts.
- **An unexplained placement refusal** (part 2). Under bandits, a source
  card whose placement is refused is left out of its question, not shown
  with a reason. The walker cannot word an operation refusal that nobody
  may waive. This is a known gap.
- **A Forge that cannot place** (part 2). The walker tries each split of a
  small partition, so a Forge whose every split places on a Beast card,
  under an enemy's Forest Council, does not start: it is shown unavailable
  with its generic reason, as an unexplained placement refusal is. City
  Wall cannot cause it, since a Forge's site is its actor's.
- **An empty choice shows Continue** (part 2b). A question passed when
  empty is no longer passed silently. With nothing that may be chosen it
  shows each candidate unavailable with its reason and one Continue
  button, which passes it. An askable candidate still asks, so a ruler's
  grant opens it, and after a refusal Continue remains. A power with no
  candidate at all asks too, so its player sees that nothing can be done.
  The cost stays paid and nothing happens, as before; the menus do not
  change. When a matching card was there but refused, Great Feast's and
  Taming Charm's line says it could not be discarded ("No Beast card at
  {site} could be discarded."), not that the site held none. This closes the gap where such a question passed before its
  player could ask.
- **When an automatic power is judged** (part 2). An automatic power is
  judged where its effect runs, inside an `AutomaticUse` node, against the
  state when the walk reaches it. Tyrant's kill runs after the pawn moves,
  so City Wall stops it when its holder travels to City Wall's site, not
  from it. Keep, which replaces a Raid's consequences, is judged where its
  window folds.
- **Enemies.** Every other player is an enemy of a player ruler. At a site
  bandits rule, every player is an enemy and nobody may consent. At an
  unruled site, or one the Empire rules, nobody is restricted. City Wall's
  "while at this site" reads the physical pawn, not an as-if site.
- **Consent.** Spell Breaker, Forest Council and City Wall are waivable;
  Giant Python is not ("Other cards"). Outside a Campaign every refusal they
  make is askable of the ruler: the phase menu, the modifier picker, the
  placements and the rerolls. Inside a Campaign (its modifiers, its battle
  plans and the rerolls of its dice) nobody is asked.
- **A refused automatic power** does not apply. Nobody is asked and no
  line is written. It is judged where its effect runs: Tyrant arriving at
  City Wall's site is refused. The roadmap's item 10b replaces this: the
  restriction's ruler chooses, each time, whether to deactivate the power
  ("Use City Wall to deactivate Tyrant?"), and the power's user is not
  asked.
- **Hover reasons**, in the consent design's format:
  - "Spell Breaker: Cannot use powers that cost secrets."
  - "Forest Council: Cannot use non-persistent powers of Beast cards." and
    "Forest Council: Cannot place favor or secrets on Beast cards."
  - "City Wall: Cannot use non-persistent powers while at its site." and
    "City Wall: Cannot place favor or secrets on cards while at its site."
  - "Giant Python: Cards at its site cannot be used for non-persistent
    powers." and "Giant Python: Cannot place favor or secrets on cards at
    its site."
  - An askable reason ends " without ruler's consent." instead of ".".
- **Giant Python's ACTION** is usable when its user holds the Darkest
  Secret through `CardPowerBanners.holdsDarkestSecret` and some other site
  can take it. It asks one question, "Giant Python: move to which slot?":
  each other site's first empty slot when it has room, and each faceup
  denizen and ruined edifice there marked Replace, judged by
  `CardSlots.judge`. A Replace discards the card the standard way; Giant
  Python then takes the site's last slot with its tokens. Its lines are
  "Moved Giant Python to {site}." and "Replaced {card} at {site}.".
- **Roving Terror** asks the same slot question with Replace options only.
- **Three plans.** Part 1 builds the filter with Spell Breaker. Part 2
  adds the placement bans and the automatic powers with Forest Council and
  City Wall. Part 3 adds Giant Python and moves Roving Terror.

## Slice 11: Actor-aware lock

Ancient Bloodline is a lock that holds against enemies of its holder only,
over the denizens and relics at sites the holder rules. Its main effect is
that an enemy cannot Recover a relic there.

## Slice 12: Card play at another site

Baron, New Growth and Forest Warden widen a card play's slots to other
sites. The slots keep their refs; only the site varies. Baron's discard and
Forest Warden's bury use the Replace path, the bury labelled "Bury and
replace".

## Slice 13: Campaign from another site

Wild Allies and Captains are ACTION powers that queue a Campaign with the
chosen site as its origin, spending no Supply. Targets and force are judged
from the origin, and the actor's warbands there join the force. The
physical pawn does not move.

## Slice 14: Off-turn actor

- **Sneak Attack** offers its holder a Campaign, spending no Supply, after
  every effect and follow-up of another player's Campaign, with that player
  declared the defender. `CampaignSetup.pin` fixes the defender.
- **Second Wind** is offered to a victorious defender too. The defender's
  Travel and Campaign run off turn, after the attacker's follow-ups.

## Slice 15: Palanquin

Palanquin's ACTION moves its user's pawn to a site the chosen player could
travel to, then queues a Travel for that player to the same site, spending
no Supply. The target is the Travel's actor and owns its decisions.

## Seams used for the first time

Each gets a test through its first power before the power relies on it. If
a seam needs an engine change beyond those approved, stop and ask the
product owner.

- A variable X paid inside an ACTION's `build` (Ward of Silence).
- A non-attacker's question at the start of a Campaign (Vow of Union).
- A walk ended early, and a Campaign skipped or left with nothing to attack
  (Vow of Union).
- A Raid's consequences dropped while its takes stay (Keep).
- A burn rewritten into a take, on a cost (Vow of Silence).
- A trigger on executed operations that asks a question (Brass Army).
- A modifier that changes the site later modifiers are read from (Small
  Friends).
- A per-turn effect read from `usedPowers` (Spiteful Mirror).
- A power refused by another card's restriction on both power paths (Spell
  Breaker).
- A restriction that knows the acting player (Ancient Bloodline).
- A card played to a site other than the pawn site (New Growth).
- A queued Campaign with an origin other than the pawn site (Captains).
- A Campaign walked by a player who is not the active player (Sneak Attack).
- A Travel walked by a player who is not the active player (Palanquin).

## Testing

- Each card gets a suite through the walker, from a board stated in Table
  steps, with its Game Log lines.
- Every refused or unavailable option is tested for its hover reason.
- Slices that add an operation or start argument test a replayed game
  reaching the same state.
- Slice 14 tests Sneak Attack after a Campaign with and without follow-ups,
  two holders in turn order, and a Second Wind defender; slice 15 tests
  Palanquin with Toll Roads refused and accepted, and with Hospitality and
  Brass Army on the target.
- The verification gate is the roadmap's standing rule.

# Catalog Batch 7: Campaign Dice

**Status:** designed 2026-10-09. Slice 1 is planned; its plan-time rulings
are under "Slice 1". The per-card rulings are in
[the rulings appendix](2026-10-09-catalog-batch-7-rulings.md).

**Builds on** [Catalog batch 4](2026-10-05-catalog-batch-4-design.md),
[Catalog batch 5](2026-10-08-catalog-batch-5-design.md) and
[Catalog batch 6](2026-10-09-catalog-batch-6-design.md), whose vocabulary,
cost rules, walker shapes and log-line rules apply unchanged.

## Goal and scope

**Phase - Catalog batch - engine-blocked** in the roadmap groups its cards by
the engine change each needs. This batch takes the cards that change a
Campaign's dice or how its battle is resolved: the reroll group, the
variable X and set-face cards that are battle plans, and the battle rewrites
filed under "Overriding scoring or the victor", "Lowering the attacker's
force" and "Other".

All of them work inside the Campaign tree, Recover's roll loop or the roll of
an ACTION power. None of them touches the menus, follow-ups, phase-power
offering or start legality that the
[walker-driven game loop](2026-10-09-walker-game-loop-design.md) is
rewriting, so the batch can run beside that phase.

| Slice | Count | Cards |
|---|---|---|
| 1. Rerolls | 2 | 68 Jinx, 249 Master at Arms |
| 2. Recover rerolls | 1 | R31 Silver Charm |
| 3. Amounts and fixed faces | 3 | 206 Arcane Armor, 225 Unstable Summon, 155 Mountain Giant |
| 4. Battle rewrites | 6 | 254 Careful Plans, R42 Weeping Banner, 87 Zealots, 3 Bear Traps, 217 Signal Trees, 138 Hearts and Minds |

Slice 2 needs slice 1. Slices 3 and 4 need neither and may run beside them.

### The bar

The approved changes outside `gameplay/powers/`:

- **A roll step with rerolls** (slice 1). `RollStep` builds a `Roll`
  followed by a `Repeat` of `RerollChoice` passes. A `RerollChoice` is a
  `RerollHost` at a new `PowerWindow.Reroll`: the reroll counterpart of the
  battle-plan `OfferHost`, fed by the powers' `OfferReroll` contributions
  and reading the same `OfferHost.Pass`. All six roll sites use it: the
  Campaign's attack and defense rolls, Recover, Gambling Hall, Relic Thief,
  Bone Dice and Murky Fountain.
- **A reroll operation** (slice 1). `Reroll(pool, indices, faces)`
  replaces the faces at `indices` in the pool's `RollOutcome`, re-deriving
  the skulls and the score. A tree declares it without faces; the walker
  fills them from its dice source, as it fills a `Shuffle`'s order, so the
  journal records them and replay applies them. It is a hidden outcome: the
  legality search stops at it, and undo does not take it back.
- **A fixed-faces operation** (slice 3). `SetDieFaces(pool, faces)` appends
  `faces` to the pool's outcome and lowers the pool's count by as many, so
  the roll that follows rolls only the rest.
- **Amounts and choices inside a battle plan** (slice 3). A plan's
  application may ask a `ChooseAmount` or a `ChooseOne` of its user before it
  pays, as Sticky Fire and Wild Mounts ask a `ChooseOne` after.
- **Attack scoring fields** (slice 4). `AttackRules` gains the warbands a
  skull kills (Weeping Banner) and the worth of a sacrificed warband
  (Zealots). `CampaignBattle.result` reads the sacrifice's worth from the
  chosen plans.
- **A reordered battle** (slice 4). Careful Plans moves the defense roll and
  its result ahead of the attack roll through an `OperationRewrite`.

The serialization codec gains `Reroll` and `SetDieFaces`. Its existing
omission of `Roll`'s mode and window is left alone.

### Catalog corrections

- **Silver Charm** is a Recover modifier, and its printed `[attack-die]` is
  a misprint for `[defense-die]` (product owner, 2026-10-09). Slice 2
  corrects the text in the holding catalog.
- **Weeping Banner**'s power id is `relic.keeping-banner` in the holding
  catalog and the reference JSON. Slice 4 renames it to
  `relic.weeping-banner`.

### Out of scope

- **Ward of Silence** has a variable X but is an ACTION power, so it joins
  the Act menu of the game loop's slice 4. It stays engine-blocked.
- Every other engine-blocked group stays where it is.

## Slice 1: Rerolls

### The roll step

`RollStep(pool, dice, roller, window)` is the one way a tree rolls dice:

1. the `Roll`, with the `window` the site already gives it (Mounted Patrol
   keeps wrapping the Campaign's attack roll);
2. a `RerollChoice` for `roller`, an `OfferHost` at `PowerWindow.Reroll`
   that knows the pool and the dice of this throw.

The choice gathers each power's reroll offer, priced like any offer, and
adds "Keep the roll". A power used on this roll is no longer offered.
Picking an offer runs its reroll and asks again; "Keep the roll" ends it.
When no power offers anything, nothing is asked. An unusable offer is shown
unavailable with its reason, as every other offer is.

`roller` is whoever rolls by the rules:

| Roll | Roller |
|---|---|
| Campaign attack | the attacker |
| Campaign defense | a player defender; none for bandits |
| Recover | the actor |
| Gambling Hall, Relic Thief, Bone Dice, Murky Fountain | the power's user |

With no roller, the step is the bare `Roll`. A defender's choice is owned by
the defender inside the attacker's walk, as the defender's plan decision is.

The once-per-roll limit is read from the walk's answers in this
`RerollChoice`, so it needs no state. Each choice's decision ids name the
pool and the roll, so Recover's repeated rolls each get their own.

### The powers

- **Jinx** offers itself on every roll its ruler rolls, for one secret, paid
  when used. It rerolls every die of the throw.
- **Master at Arms** is an attacker battle plan. Once chosen, it offers
  itself on that Campaign's attack roll. Picking it asks which dice showing a
  hollow sword to reroll.

The two may both reroll one roll, in either order.

### Plan-time rulings

- **The throw.** A throw is the last faces of the pool's outcome, as many
  as the pool holds dice. The reroll decision's id is `{pool}.reroll.{n}`,
  where `n` counts the pool's throws so far (`campaign.attack.reroll.1`,
  `recover.reroll.3`). Its heading reads "Reroll the attack dice, or keep
  the roll" ("the defense dice" for a defense roll), and its button "Keep
  the roll". A parked reroll decision shows its pool's dice.
- **What is asked.** The roller is asked only when some offer is usable.
  An unusable offer, such as Jinx without a secret ("Needs 1 secret; you
  have 0"), is shown unavailable when the decision is asked for another.
- **Master at Arms asks how many.** Every hollow sword is the same face,
  so it asks "Reroll how many hollow swords?", from one to all of them, and
  rerolls the first that many. With one hollow sword it asks nothing. On a
  throw with none it is shown unavailable: "No die shows a hollow sword".
- **Jinx needs a faceup card**: a faceup adviser of the roller, or a card
  faceup at a site the roller rules.
- **The Game Log.** The roll's own line is shown before the roller decides.
  Each reroll writes its power's line, then a roll line restating the whole
  throw: "Rolled {faces} for the attack". The reroll decisions write no
  line.

## Slice 2: Recover rerolls

Silver Charm is a Recover modifier. On each payment after the first, before
the roll, a `ChooseMany` over the earlier Recover faces asks which to reroll.
The chosen faces are rerolled by `Reroll` and the two new dice are rolled,
and together they are one throw: Jinx's offer that follows rerolls all of
them. It is skipped on the first payment, which has no earlier faces.

Recover's cost now sits in its own window (game loop slice 1). The plan
reads where the payment and the roll sit from there.

## Slice 3: Amounts and fixed faces

- **Arcane Armor** is a defender plan. Its user chooses X, from one to the
  smaller of their secrets and the defense pool, and pays X secrets. At the
  defense roll, `SetDieFaces` records X double shields before the `Roll`,
  which rolls the rest. If the pool has shrunk below X by then, only as many
  as the pool holds are set.
- **Unstable Summon** is an attacker plan, offered while its user holds the
  Darkest Secret with a secret on it. Its user chooses X, from one to the
  secrets on the banner. It burns X from the banner with the existing
  `Burn` and adds X attack dice.
- **Mountain Giant** is a plan for either side, costing one secret. Its user
  chooses one or three. The attacker adds that many attack dice; the
  defender removes them. It is discarded at the end of the Campaign only
  when three was chosen.

The plan window's dry run prices an offer before its amount is known, so an
X plan's price is shown as X secrets, and it is withheld only when no X is
possible.

## Slice 4: Battle rewrites

- **Careful Plans** is an attacker plan. Its `OperationRewrite` at
  `CampaignActionEligibility` moves the defense roll and its result step
  ahead of the attack roll. The sacrifice stays after the attack roll, so it
  sees both rolls. The defense result adds the defender's force in either
  order. If the rewrite cannot move the block cleanly, the fallback is an
  order hook on the Campaign tree that reads the chosen plans.
- **Weeping Banner** is a defender plan. Each skull kills two warbands:
  `AttackRules` counts two losses per skull, capped by the force, and a
  skull face scores its swords only when both are paid. It adds no swords.
- **Zealots** is an attacker plan. Its condition is checked when it is
  chosen, and the outcome is recorded then, so later skull losses do not
  change it. While it holds, each sacrificed warband adds three.
- **Bear Traps** is a free defender plan: one attack die fewer, and one
  warband killed on the attacker's board.
- **Signal Trees** is a plan for either side. On application it reveals the
  top three cards of its user's region's discard pile, scores two attack
  dice per Beast, and discards the revealed cards.
- **Hearts and Minds** is a defender plan that reuses `VictoriousNow`. At
  the end of the Campaign it is discarded unless its user holds the People's
  Favor then.

## Seams used for the first time

Each gets a test through its first power before the power relies on it. If
a seam needs an engine change beyond those approved, stop and ask the
product owner.

- A `RerollChoice` after a roll, with and without offers, at each roll site
  (Jinx).
- A reroll of chosen dice, replayed from the journal (Master at Arms).
- One throw made of rerolled earlier faces and new dice (Silver Charm).
- Fixed faces before a roll (Arcane Armor).
- A `ChooseAmount` inside a battle plan, priced before its amount is known
  (Arcane Armor, Unstable Summon).
- A plan that reorders the battle's steps (Careful Plans).
- A plan whose effect is decided by state at its use, recorded then
  (Zealots).
- A plan that reveals hidden cards on application only (Signal Trees).

## Testing

- Each card gets a suite through the walker, from a board stated in Table
  steps, with its Game Log lines.
- Slice 1 tests Jinx at every roll site, as attacker and as defender, with
  bandits rolling for nobody; Jinx and Master at Arms on one roll in both
  orders; each power refused a second use on one roll; and a replayed game
  reaching the same faces.
- Slice 2 tests Silver Charm on the first payment (nothing asked), on a later
  payment with and without chosen faces, and Jinx after it.
- Slice 3 tests each X at its bounds, Arcane Armor capped by a shrunk pool,
  and Mountain Giant's discard on three and not on one.
- Slice 4 tests Careful Plans with a sacrifice, Weeping Banner with an odd
  force and with Fearsome General, Zealots true and false at use, Bear Traps
  against an empty board and for bandits, Signal Trees with a short pile and
  what each viewer sees, and Hearts and Minds with and without the People's
  Favor.
- The verification gate is the roadmap's standing rule.

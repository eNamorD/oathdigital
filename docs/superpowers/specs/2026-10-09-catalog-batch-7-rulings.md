# Catalog Batch 7: Per-Power Rulings

> Appendix to [the design](2026-10-09-catalog-batch-7-design.md). Each entry
> states the ruling agreed with the product owner on 2026-10-09. Card ids are
> catalog ids. Every rule that applies to the whole of
> [batch 4](2026-10-05-catalog-batch-4-rulings.md#rules-that-apply-to-the-whole-batch),
> [batch 5](2026-10-08-catalog-batch-5-rulings.md#rules-that-apply-to-the-whole-batch)
> and [batch 6](2026-10-09-catalog-batch-6-rulings.md#rules-that-apply-to-the-whole-batch)
> applies here too.

## Rules that apply to the whole batch

- **"Once" limits each power on each roll.** A power that rerolls "once"
  rerolls a given roll at most once. Two different powers may both reroll
  one roll, in either order (Jinx FAQ: the same dice cannot be rerolled
  twice by Jinx).
- **Who rolls.** The attacker rolls the Campaign's attack dice and a player
  defender its defense dice. Bandits roll for nobody, so no power rerolls a
  bandit's defense. The actor rolls Recover's dice, and the user of Gambling
  Hall, Relic Thief, Bone Dice or Murky Fountain rolls that power's dice.
- **The reroll decision.** Right after a roll, its roller is asked once with
  every reroll they may use and "Keep the roll". After a reroll the decision
  asks again without the power just used. Nothing is asked when no power
  applies. An unusable reroll is shown unavailable with its reason.
- **Battle plans with costs are never applied by bandits.** Of this batch's
  defender plans, only Bear Traps is free.
- **A reason or warning reads as the Game Log words it:** the card's name, a
  colon, then the sentence.
- **Log lines omit the actor.** A roll's own line shows the faces as
  rolled, since the roller sees them before deciding. Each reroll writes its
  power's line, then a roll line restating the throw as it now stands.

## Slice 1: Rerolls

- **68 Jinx.** A persistent power of a card its ruler rules, adviser or
  site denizen. After its ruler rolls attack or defense dice for any reason,
  they may pay one secret to reroll every die of that throw, once. The
  secret is paid after the roll, when the power is used (FAQ). It reaches
  every roll in the engine: the Campaign's attack and defense rolls, Recover,
  Gambling Hall, Relic Thief, Bone Dice and Murky Fountain (FAQ). Dice set by
  Arcane Armor were not rolled and are not rerolled.
  Line: "Jinx: Rerolled the attack dice." ("the defense dice" for a defense
  roll).
- **249 Master at Arms.** A free attacker battle plan. Once chosen, after
  the attack roll its user may reroll any number of the dice showing
  a hollow sword, once. It asks how many, since the dice show the same face,
  and asks nothing when only one shows a hollow sword. Line: "Master at
  Arms: Rerolled 2 hollow swords."

## Slice 2: Recover rerolls

- **R31 Silver Charm.** A free Recover modifier. Its text reads
  `[defense-die]` where the card misprints `[attack-die]` (product owner).
  Each time its user pays Supply to roll, after the first, they may choose
  any number of earlier Recover dice to reroll with the two new dice. The
  rerolled faces replace the old ones. There is no once-limit: a die may be
  rerolled on every payment. Jinx's reroll after such a payment rerolls
  every die thrown in it, the new two and the rerolled earlier ones.
  It is asked after the payment and before the roll. Line: "Silver Charm:
  Rerolled shield, blank, two shields.", naming the faces of the chosen
  dice before the reroll as a roll line names dice. It takes the place of
  the roll's own line, and the throw restated after it shows every die.

## Slice 3: Amounts and fixed faces

- **206 Arcane Armor.** A defender battle plan costing X secrets. Its user
  chooses X, from one to the smaller of their secrets and the dice in the
  defense pool then. At the defense roll, X dice are set to their double
  shield face and the rest are rolled. If the pool holds fewer than X dice
  by then, only as many as it holds are set, and the secrets stay spent.
  Its user counts only faceup secrets. The set dice are not part of the
  roll's throw, so no reroll changes them. Line: "Arcane Armor: Set 2
  defense dice to double shields."
- **225 Unstable Summon.** A free attacker battle plan, usable only while
  its user holds the Darkest Secret with at least one secret on it, and
  shown unavailable otherwise ("Needs the Darkest Secret with a secret on
  it"). Its user chooses X, from one to the secrets on the banner, burns X
  of them from the banner, and adds X attack dice. Line: "Unstable Summon:
  Burned 3 secrets from the Darkest Secret for 3 attack dice."
- **155 Mountain Giant.** A battle plan for either side, costing one
  secret. Its user chooses one or three (the card's missing icon after "±3"
  is `[attack-die]`, product owner). An attacker adds that many attack dice;
  a defender removes that many. It is discarded at the end of the Campaign
  only when three was chosen (FAQ). It pays its secret, then asks one or
  three. Line: "Mountain Giant: Added 3 attack dice." ("Removed 3 attack dice." for
  a defender), and the discard line only on three.

## Slice 4: Battle rewrites

- **254 Careful Plans.** An attacker battle plan costing one favor. The
  defense roll and its result come before the attack roll. The sacrifice
  stays after the attack roll, so it is decided with both rolls known. It
  writes no line of its own.
- **R42 Weeping Banner.** A defender battle plan costing one secret. Each
  skull the attacker rolls kills two warbands instead of one (product owner,
  revised 2026-10-09):
  - the kills are capped by the force;
  - a skull face scores its swords only when both of its warbands are paid,
    so with an odd force the last warband dies and scores nothing;
  - it adds no swords: with Fearsome General, a paid skull face is worth
    four swords, as it is without Weeping Banner;
  - when the attacker's skulls are ignored (Outriders and the like), nothing
    dies and every face scores as usual.

  The Game Log's attack line counts the warbands the skulls kill, so
  "Attack: 3 with 3 skulls" may follow two skulls rolled (plan-time ruling).
  It writes no line of its own.
- **87 Zealots.** An attacker battle plan costing one favor. Its condition,
  the defending force larger than its user's force, is checked when the plan
  is chosen, comparing the committed force with the defending warbands then
  (FAQ: later skull losses do not trigger it). While it holds, the
  plan records a worth of three when it is applied, so each sacrificed
  warband adds three to the attack, and the sacrifice heading says so:
  "Sacrifice up to 2 warbands for three attack each". When the condition
  fails, the plan may still be chosen and does nothing; its option carries
  the warning "Zealots: Has no effect unless defending force is larger than
  yours", and stays choosable. It writes no line of its own.
- **3 Bear Traps.** A free defender battle plan. It removes one attack die
  and kills one warband on the attacker's board, not in the committed force.
  The committed force stays on the board until the losses, so with no
  warband beyond it, nothing dies and no line is written. A bandit
  defender applies it at a site it rules. Line: "Bear Traps: Killed 1 warband on Blue's board."
- **217 Signal Trees.** A battle plan for either side, costing one secret.
  - Its offer shows no dice change, since the cards it reads are hidden. The
    preview reads "±2 [attack-die] per revealed Beast".
  - When the plan is chosen and confirmed, the top three cards of the
    discard pile of its user's pawn region are revealed to every player. A
    pile with fewer cards reveals what it has.
  - An attacker adds two attack dice per revealed Beast card; a defender
    removes two.
  - The revealed cards are then discarded to the next region's discard
    pile, as every discard is.

  Line (plan-time ruling, one line for the reveal and the discard):
  "Signal Trees: Revealed and discarded Wolves, Tavern Songs and Sacred
  Ground; added 4 attack dice." ("removed" for a defender), "Signal Trees:
  Revealed and discarded …." with no Beast, and "Signal Trees: Nothing was
  revealed." for an empty pile.
- **138 Hearts and Minds.** A defender battle plan costing three favor. Its user is victorious now: nothing is rolled or
  sacrificed and the defender wins, through `VictoriousNow`. The defeated
  attacker's losses follow the printed rules: half the surviving force. At
  the end of the Campaign, it is discarded unless its user holds the
  People's Favor then, including a People's Favor won in this Campaign. It
  writes no line of its own.

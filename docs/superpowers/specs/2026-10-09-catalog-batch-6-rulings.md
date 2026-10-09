# Catalog Batch 6: Per-Power Rulings

> Appendix to [the design](2026-10-09-catalog-batch-6-design.md). Each entry
> states the ruling agreed with the product owner on 2026-10-09. Card ids are
> catalog ids. Every rule that applies to the whole of
> [batch 4](2026-10-05-catalog-batch-4-rulings.md#rules-that-apply-to-the-whole-batch)
> and of [batch 5](2026-10-08-catalog-batch-5-rulings.md#rules-that-apply-to-the-whole-batch)
> applies here too.

## Rules that apply to the whole batch

- **A plan's suit is its source card's suit.** A relic and the title have no
  suit, so no suit rule withholds them.
- **The title's plan is a battle plan.** "Cannot use battle plans" and
  "cannot use other battle plans" withhold it.
- **A withheld plan stays listed.** It is shown unavailable with its reason
  on hover, never hidden.
- **Bandits are enemies.** A rule against its holder's enemy applies to a
  bandit defender too, as Gleaming Armor's does.
- **A reason reads as the Game Log words it:** the card's name, a colon, then
  the sentence. The reasons below are written that way.

## Slice 1: Plan restrictions

- **114 Specialist.** The attacker's battle plan, costing two favor. Once it
  is chosen, every defender plan is withheld with the reason "Specialist:
  The defender cannot use battle plans.", so the defender is not asked.
- **192 Marsh Spirit.** A persistent site rule. While its site is among a
  Campaign's targets, every attacker plan is withheld with the reason "Marsh
  Spirit: Players who target its site cannot use battle plans." Who defends
  does not matter. The defender's plans are not affected. A Raid targets no
  site, so it never applies to one.
- **90 Beast Tamer.** A persistent rule of a faceup adviser. While its
  holder is the attacker or the defender, the enemy's Beast and Nomad plans
  are withheld with the reason "Beast Tamer: No Beast or Nomad battle
  plans."
- **41 True Names.** A persistent rule of a faceup adviser. While its holder
  is the attacker or the defender, every enemy plan whose suit matches one of
  the holder's faceup advisers is withheld with the reason "True Names: No
  battle plans matching {Blue}'s advisers." Facedown advisers do not count,
  because their suits are hidden. An adviser turned faceup during plan
  selection counts from the next pass of the window. A plan chosen before
  that stays chosen.
- **103 Code of Honor.** A free battle plan for either side. An attacker
  adds two attack dice; a defender removes two. It is withheld once its user
  has chosen another plan in the window, with the reason "Code of Honor:
  Cannot be used with other battle plans." Once chosen, the user's other
  plans are withheld with the reason "Code of Honor: No other battle plans."
  A bandit defender that would apply it applies it and no other plan
  (FAQ). When another power withholds it, the bandits apply their other
  plans as usual.

## Slice 2: Plan rewrites

- **125 Peace Envoy.** A battle plan for either side, costing one favor.
  - It is withheld unless the enemy's pawn is at the user's pawn site, with
    the reason "Your enemy's pawn must be at your site". A bandit defender
    has no pawn.
  - Giving the enemy one favor per defense die in the pool is part of its
    cost, counted when the plan is offered. A user who cannot give the full
    amount sees the plan unavailable with the shortfall as its reason.
  - Once chosen, the user's other plans are withheld, as Code of Honor's
    are.
  - "You're victorious now": when the attacker used it, the defender's plans
    are withheld with the reason "Peace Envoy: The battle is decided.".
    Nothing is rolled and nothing is sacrificed. The user's side wins.
  - "Ignore killing warbands": the losses kill no warband on either side.
    After a Conquest the attacker wins with it, the defender's warbands at
    the targets all return to the defender's board. Victory then resolves
    as usual.
  - Its price includes the gift: with two defense dice, 3 favor.
  - Its reasons and lines: "Peace Envoy: Cannot be used with other battle
    plans.", "Peace Envoy: No other battle plans.", "Peace Envoy: The
    battle is decided.", "Gave {2 favor} to {Blue}." and "Won the battle
    without a roll.". A defender's lines name them: "{Blue} gave {2 favor}
    to {Red}." and "{Blue} won the battle without a roll.".
- **153 Wild Mounts.** A free battle plan for either side, with no effect
  when chosen.
  - At the end of the Campaign, when any of the user's Nomad plans would be
    discarded, the user is asked: discard one Beast card they rule instead,
    or "Skip Wild Mounts". Wild Mounts prints no discard of its own.
  - Bandits (FAQ): "If they would discard two or more Nomads, they discard
    the first Beast card they rule starting at the top of the Cradle and
    going down through the regions." With one Nomad discard, they discard
    it as usual.
  - Its line: "Discarded {card} instead of the battle plans.", or, for a
    defender, "{Blue} discarded {card} instead of the battle plans.".
  - Cards the user rules are their advisers and the denizens at sites they
    rule.
  - No question is asked when nothing would be discarded, or when the user
    rules no Beast card.

## Slice 3: Search sources

- **64 Observatory.** A selected Search modifier, usable while the actor's
  pawn is at its site. Every region's discard pile is offered, each at 2
  Supply.
- **11 Errand Boy.** A selected Search modifier, costing one favor. The
  other regions' discard piles are offered, each at 2 Supply. The actor's own
  region's pile and the world deck stay available, warned with "Errand Boy
  does nothing here".
- **183 Mushrooms.** A selected Search modifier, usable while the actor's
  pawn is at its site, costing one secret. The world deck is unavailable,
  with the reason "Mushrooms draws from a discard pile". The Search costs no
  Supply and draws one card from the bottom of the pile picked. It stacks
  with Observatory and Errand Boy (any pile they offer) and with draw
  extensions such as Augury (more cards, still from the bottom).
- **R33 Wine of Welcome.** A selected Search modifier. The world deck is
  drawn from the bottom, stopping at the first Vision as usual. While it is
  picked, each discard pile is warned "Wine of Welcome needs the world
  deck" and stays choosable.
- **R23 Painted Trumpet.** A selected Search modifier. Picking any discard
  pile draws the top card of each discard pile instead. Draw extensions do
  not apply. The world deck is unaffected. While it is picked, the world
  deck is warned "Painted Trumpet needs a discard pile" and stays
  choosable. The pile picked may be empty while another pile holds a card,
  and it costs its usual 2 Supply. Truthful Harp still reveals the cards
  drawn.
- **Modifier rules.** Each card states its own, through `offerReason`.
  Painted Trumpet is unavailable while Mushrooms is picked, with the
  reason "Cannot be used with Mushrooms". Mushrooms is unavailable while
  Painted Trumpet is picked, with "Cannot be used with Painted Trumpet".
  Wine of Welcome is warned while Mushrooms is picked, and Mushrooms
  while Wine of Welcome is, each with "Wine of Welcome does nothing with
  Mushrooms".
- **Plan-time rulings** (2026-10-09):
  - Any region's discard pile costs 2 Supply. The source decision is the
    only gate.
  - A power adds piles after the printed two, in region order (Cradle,
    Provinces, Hinterland), each once. Painted Trumpet draws the piles
    in the same order.
  - Mushrooms waives only Supply. Forced Labor's toll is still paid, as
    with News from Afar.
  - The Search line reads "Drew {cards} from the bottom of the Cradle
    discard and kept {card}", "… from the bottom of the World Deck …",
    and for Painted Trumpet "Drew {cards} from each discard pile and
    kept {card}".

## Slice 4: Search draws

- **232 Spinning Bee.** A selected Search modifier.
  - It excludes every other Search modifier, both ways.
  - The source decision is skipped: the Search draws from the actor's
    region's discard pile at the usual 2 Supply.
  - A decision, "Declare a suit", offers the six suits as favor bank refs.
  - Cards are drawn from the top until one matches the suit, the match
    included. The normal keep-one choice then runs over every drawn card.
    When none match, every drawn card is discarded and nothing is kept.
- **130 Land Warden.** A selected Search modifier, costing one favor.
  - The Keep section allows one or two cards.
  - The kept cards are played in the order kept. When the first did not go
    to a site, the second may only go to a site; its other options are
    unavailable with the reason "Land Warden: one card must go to a site".
    When no site has room, the second card is discarded.
  - Its power id, `denizen.land-garden`, is a typo. It becomes
    `denizen.land-warden`; no event holds the old id.

## Moved out of the batch

- **Hearts and Minds** stays in the engine-blocked phase, to reuse Peace
  Envoy's victory rewrite in a later batch.

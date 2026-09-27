# Catalog Batch 2: Per-Power Rulings

> Appendix to [the design](2026-09-26-catalog-batch-2-design.md). Each entry
> states the ruling agreed with the product owner in five rounds on
> 2026-09-26. Card ids are catalog ids. "Placed" means placed onto the card,
> per the cost rules of [batch 1](2026-09-20-powers-design.md). Every rule in
> [batch 1's rulings](2026-09-20-powers-rulings.md#rules-that-apply-to-every-power)
> that applies to every power applies here too: access, facedown cards,
> activation, repeat use, usability, secrets, movement and bury.

## Slice 1: modifiers and restrictions

| Card | Ruling |
|---|---|
| 40 Animal Playmates | Adviser-only. A selected Muster modifier with no cost. When the card mustered on, a denizen or an edifice, is beast, the Muster spends no Supply. Otherwise the Muster pays as usual and the modifier does nothing. It may be selected whatever the card, as Cup of Plenty may. |
| 176 Birdsong | Adviser-only. A selected Trade modifier with no cost, as Cup of Plenty: when the card traded with is beast or nomad, the Trade spends no Supply. |
| 245 Royal Stables | Site-only. A selected Travel modifier with no cost, usable at the pawn's site or a site the player rules. It lowers the Travel's `SpendSupply` by 1, never below 1. Terrain and other modifiers apply first. Tents removes the `SpendSupply` entirely, so with both the Travel is free. |
| 75 Forgotten Vault | Site-only, persistent. Players other than the Vault's ruler cannot target relics its ruler holds, as the Circlet of Command protects relics. It hides them from a Raid's target options and from a played Conspiracy's target list. In an all-Exile game every other player is an enemy. Ruled by bandits, it does nothing, because bandits hold no relics. The Empire clause does nothing until Empire rules exist. |

## Slice 2: battle plans

Batch 1's plan rules apply: a plan is chosen at the plan step, only by its
source's ruler, once per Campaign, and it is not offered when it cannot be
paid. Two further rules:

- **Sign.** "±" is fixed by the side, as for Mercenaries. An attacker adds
  attack dice. A defender removes them from the attacker's pool, never below
  zero. A plain "+" belongs to one side: attack dice to the attacker, defense
  dice to the defender.
- **Conditions.** A plan with a condition is offered only when the condition
  holds at the plan step, as the Towering Rampart is.

Only faceup cards have a suit, so a condition on a player's advisers counts
faceup advisers.

| Card | Side | Ruling |
|---|---|---|
| 31 Fire Talkers | either | Cost 1 secret placed. ±3 attack dice. Offered only while its user holds the Darkest Secret. |
| 175 Nature Worship | either | Cost 1 secret placed. ±1 attack die per faceup beast adviser its user has. It counts itself: a facedown adviser chosen as a plan is revealed when chosen. |
| 83 Cracked Sage | either | Cost 1 secret placed and 1 favor burnt. ±4 attack dice. Offered only when the enemy has a faceup arcane adviser, so never against bandits. |
| 24 Horse Archers | either | Free. ±3 attack dice. Discarded after the Campaign whoever won, through the standard discard, as Warning Signals is. |
| 167 Storm Caller | defender | Free. +2 defense dice. Discarded after the Campaign whoever won. |
| 4 Longbows | either | Free. ±1 attack die. |
| R35 Black Sword | attacker | Cost 2 secrets burnt. +5 attack dice. |
| R37 Bag of Siegeworks | attacker | Cost 1 secret placed. Conquest only ("targeting sites"). Each single-shield defense die scores 0. Two shields and doublers are unaffected, so Blank, OneShield and Doubler score 0. The reviewed-catalog stub retires. |
| 149 Hospital | either | Site-only. A battle plan, not a modifier: its gradient marks it as one. Free, used by the ruler of Hospital's site. For the rest of the Campaign, each of the user's warbands that would be killed is placed on Hospital's site instead, while the user still rules that site at the kill. Kills at Hospital's own site when it is a Conquest target the attacker won stay kills. |

## Slice 3: actions on yourself

| Card | Ruling |
|---|---|
| 69 Tutor | Adviser-only. Cost 1 favor and 1 secret placed. Gain 1 secret from the shared bank. |
| 33 Spirit Snare | Cost 1 secret placed. Take 1 favor from a bank the player chooses. One stocked bank is taken without asking. With every bank empty, nothing happens. |
| 34 Wizard School | Site-only. Cost 1 favor placed. Gain 1 secret, then the Act phase ends with `EnterPhase(Rest)`, as Murky Fountain ends it. |
| 19 Scryer | Cost 1 secret placed. Choose one region's discard pile. Every card in it is recorded as a `Peek`, then an `Inspect` decision owned by the player shows them in pile order, top first. An empty pile shows nothing and asks nothing. |
| R14 Oracular Pig | Free. The top 3 cards of the world deck, or fewer if it holds fewer, are recorded as `Peek`s and shown in an `Inspect` decision, top first. |
| 160 Oracle | Site-only. Cost 2 secrets placed. Draw the first Vision from the top of the world deck. The cards above it stay in place, unseen. Play or discard it as at the end of a Search: faceup, subject to Vow of Obedience, Secret Police and Sacred Ground; facedown as an adviser; or discarded. The draw advances the Visions Drawn track. A deck with no Vision: the cost is paid and nothing happens. |
| R17 Shifting Map | Cost 1 secret placed. Gain 1 Supply, up to the track's maximum. |
| R46 Demon Tail | Cost 3 secrets burnt. Gain 2 Supply, up to the track's maximum. |
| R47 Clay Rattle | Cost 2 secrets placed. Choose the world deck or one region's discard pile and shuffle it with the new `Shuffle` operation. The server generates the order and the journal records it. |

## Slice 4: actions on others

| Card | Ruling |
|---|---|
| 228 Spoiled Supplies | Cost 1 favor placed. Every other player whose pawn is at the player's site loses 1 Supply, never below zero. Nothing is asked. |
| 131 Charming Friend | Adviser-only. Cost 1 secret placed. Choose another player whose pawn is at the player's site and `Take` 1 favor from their board, so other powers may restrict it. With no such player, the cost is paid and nothing happens, as for Sleight of Hand. A chosen player with no favor gives nothing. |
| 116 Siege Engines | Cost 1 favor placed. Choose a site in the region of the player's pawn, any ruler, and kill up to 2 warbands there, the player's own and bandits included. A site left empty is refilled with bandits by the existing refill after the action. |
| R36 Barbed Net | Cost 3 secrets burnt. Peek at every relic at the player's site, then take one facedown, as Recover does. The existing minor action that reveals an owned relic covers "you may keep it facedown". A site with no relic: the cost is paid and nothing happens. |
| R19 Book of Records | Cost 1 secret placed and 2 secrets burnt. Choose a banner held by a player whose pawn is at the player's site, the player's own banner included. Take up to 2 of what it holds: favor from the People's Favor, secrets from the Darkest Secret. |

## Slice 5: triggers and when played

| Card | Ruling |
|---|---|
| 214 Shifting Fog | When played. Every bank's favor moves at once to the next bank in the order Discord, Arcane, Order, Hearth, Beast, Nomad, and Nomad's goes to Discord. |
| 216 Hunger | Adviser-only, locked. A forced step at the start of its holder's Wake, not an optional Wake power. The holder chooses an adviser held by a player whose pawn is at their site, their own included and Hunger excluded, and buries it with the standard returns. With no candidate, nothing happens. |
| 170 Twin Brother | Adviser-only. When played faceup only. The player may swap it with a faceup nomad adviser of another player that is not locked. Both cards land faceup, and the favor and secrets on each card move with it. |
| 101 Chaos Cult | Adviser-only, persistent, faceup. When the title procedure gives the Oathkeeper title to a player other than Chaos Cult's holder, the holder `Take`s 1 favor from that player's board. With no favor there, nothing happens. The title turning between its Oathkeeper and Usurper sides without changing hands is not a take. |

## Deferred

- **Warnings before a choice with no effect.** A modifier selected for an
  action it will not change, such as Animal Playmates with a card of another
  suit, is allowed. The game should warn before the player commits. Recorded
  in the ROADMAP.

# Catalog Batch 4: Per-Power Rulings

> Appendix to [the design](2026-10-05-catalog-batch-4-design.md). Each entry
> states the ruling agreed with the product owner on 2026-10-05. Card ids are
> catalog ids. "Placed" means placed onto the card and "burnt" means paid to
> the shared bank, per the cost rules of [batch 1](2026-09-20-powers-design.md).
> Every rule that applies to the whole of
> [batch 3](2026-09-29-catalog-batch-3-rulings.md#rules-that-apply-to-the-whole-batch)
> applies here too: all-Exile, suits, cards a player rules, cards counting
> themselves, acting on yourself, Locked and the Grand Scepter, "X to gain Y",
> no target, and best-effort amounts. So do batch 2's plan rules, **Sign** and
> **Conditions**, and batch 3's plan rules: **Enemy**, **Zero**, **"At end,
> discard"**, **"If you're victorious"** and **Bandit defenders**.

## Rules that apply to the whole batch

- **Restriction icons govern play, moves and swaps.** An adviser-only card
  cannot be moved or swapped to a site, and a site-only card cannot be moved
  or swapped into a player's advisers. A global operation restriction beside
  `LockedCards` refuses the `Move` or `Swap`, and the search hides the option.
  A facedown adviser has no restrictions. Edifices are never advisers, so the
  rule does not touch them. Already-implemented powers change with it; the
  design lists them.
- **Card backs are public.** Every player can see that a facedown adviser is
  a Vision, so offering a facedown Vision's slot discloses nothing.
- **Site capacity.** Operations never check capacity, so a power that moves
  a card to a site offers only sites with room.
- **"Your site" and "your region"** mean the pawn's site and region, as in
  batch 3. "This site" and "this region" would name the card's own.
- **Turn order** for a WHEN PLAYED power that asks every player starts with
  the first player (`FinishRestProcedure.turnOrder`).

## Slice 1a: battle scoring

### The attack score

One scorer computes the attack from the faces rolled, the committed force and
the plans each side chose. Every plan that changes the attack writes its
total, so plans no longer overwrite each other by the order their power ids
sort. The steps, in order:

1. **Face values.** A hollow sword is worth ½ (pairs, rounded down), 1 with
   Fae Battalion, 0 with Rusting Ray. Only one side can hold the Darkest
   Secret, so the two never meet. The two-swords-and-skull face is worth 2,
   or 4 with Fearsome General: the face becomes four swords and a skull and
   follows every rule the face follows.
2. **Lancers** doubles the count of every rolled face, skulls included.
3. **The skull cap** runs after doubling. Each skull needs a warband from the
   force. An unpayable skull face scores 0, with Fearsome General too, and
   kills nothing more. An ignored skull (Outriders, The Great Levy, Kindred
   Warriors, Rangers) scores its swords and kills nothing.
4. **War Tortoise**, used by the defender: skull faces score 0. Payable
   skulls still kill.
5. **Total:** the sum of the face values. The sacrifice is added afterwards
   and is never doubled.

Example: a force of 3 with Lancers and Fearsome General rolls 2 skulls and
1 sword. Doubled, that is 4 skulls and 2 swords. 3 skulls are payable and
score 12, the fourth scores 0, the swords add 2, so the attack is 14 and 3
warbands die.

### The defense score

The defense scorer filters faces, then applies doublers to what is left, then
adds the defender's force. A single shield scores 0 with Rain Boots or Bag of
Siegeworks. A double shield scores 0 with War Tortoise used by the attacker.
Doublers still multiply the remaining shields.

### Cards

| Card | Side | Ruling |
|---|---|---|
| 57 Rusting Ray | defender | Cost 1 secret placed. Offered only when its user holds the Darkest Secret. The attacker's hollow swords score 0. |
| 215 Fae Battalion | attacker | Free. Offered only when its user holds the Darkest Secret. Each hollow sword scores 1. Choosing the plan is the "may"; it always applies. |
| 187 War Tortoise | either | Cost 1 favor placed. Used by the defender, the attacker's skull faces score 0, Fearsome General's 4 included, and payable skulls still kill. Used by the attacker, the defender's double shields score 0. Bandits never use it. |
| 154 Lancers | attacker | Free. Doubles the count of every rolled face, skulls included, before the skull cap. Discarded after the Campaign. |
| 163 Mounted Patrol | defender | Free. The attack pool is halved, rounded down, just before the attack roll, so defender plans applied after it in the same window count. Offered even when the pool is 0 or 1. A bandit defender applies it and discards it after the Campaign too. |
| 253 Fearsome General | attacker | Free. Each skull face is worth 4 swords instead of 2, under every rule the face follows: an unpayable skull scores 0, an ignored one scores 4. |
| 60 Kindred Warriors | either | Cost 1 secret placed. For the attacker, every skull is ignored. ±X attack dice, always the full X: no amount is asked. X is the number of distinct suits other than Arcane among the cards its user rules. The attacker is offered it at X = 0 for the skull ignore; a defender is not, by the Zero rule. |
| 45 Rangers | attacker | Cost 1 favor placed. Every skull is ignored. +2 attack dice when the defense pool holds 4 or more dice when Rangers is chosen. Dice the defender adds later do not count. Offered even when the condition fails, for the skull ignore. |
| 8 Scouts | attacker | Free. The attacker gains 1 Supply when the plan is applied, up to the Supply cap. The printed card's action icon is the red battle plan. |

## Slice 1b: losses and after the Campaign

### Losses

On a defeat today, the attacker loses skulls, sacrifice and half the
surviving force. A Conquest defender's target warbands are all killed and
half, rounded up, return to their board. A Raid defender loses half their
board.

Loss powers fold in this order: Shield Wall's "kill all" first, then Billowing
Fog and Traveling Doctor, which ignore it and Sticky Fire's kill, then
Hospital, which still saves warbands from Shield Wall's kill.

| Card | Side | Ruling |
|---|---|---|
| 59 Billowing Fog | either | Cost 1 secret placed. If its user is defeated, the defeat losses are cancelled: an attacker still loses its skulls and sacrifice, and a Conquest defender's target warbands all return to their board. Powers that kill all of its user's force (Shield Wall, Sticky Fire) are ignored. |
| 51 Traveling Doctor | either | Free. As Billowing Fog, and it is discarded after the Campaign when its user was defeated. A bandit defender applies it, still loses every targeted warband, and discards it. |
| 108 Shield Wall | defender | Cost 1 favor placed. +2 defense dice. If its user is defeated, a Conquest defender's returned half dies too, so none return, and a Raid defender's whole board dies. |
| R02 Cursed Cauldron | either | Free, from a faceup relic. If its user is victorious, they gain 1 warband per enemy warband that died in the Campaign, up to their supply. The count is net: a Conquest defender's returned warbands did not die. When the defender holds it, the attacker's skull losses and sacrifice count. Warbands Hospital saved did not die. |
| 89 Salt the Earth | attacker | Free. Offered only in a Conquest. If the attacker is victorious, every card at one targeted site is buried: denizens, edifices and relics, facedown relics included, with `Bury.standard`'s returns. The attacker is asked which site only when the Campaign targeted two or more. |

### Persistent Campaign powers

| Card | Ruling |
|---|---|
| 72 Sealing Ward | Adviser-only, locked, persistent. +1 defense die for each of its holder's relics targeted in a Raid, added when the pools are gathered. |
| 143 Herald | Adviser-only, persistent. After a Campaign whose attacker is another player and whose defender is a player, the holder gains 1 favor from a bank they choose. It fires when the holder is the defender, and for a Campaign taken inside or after another action. One stocked bank is taken without asking. |
| 230 Diplomat | Adviser-only, persistent. A player who rules no hearth card cannot Raid or Conquer the holder: those options are hidden, and the Campaign is not offered when every kind is blocked. Targets need no filter, since every target belongs to the defender. |

## Slice 2: card moves

| Card | Ruling |
|---|---|
| 46 Roving Terror | Site-only. Cost 1 secret placed. Choose a denizen or ruined edifice at any other site and discard it with the standard discard, then move Roving Terror into its place. The secret travels with the card. A locked card is not offered. |
| 127 Homesteaders | Free. Move one of the player's faceup advisers, Homesteaders included, to the pawn's site. Not offered when that site is full. Adviser-only and locked advisers are not offered. |
| 159 Resettle | Site-only. Free. Move a faceup nomad adviser of any player, the actor's own included, to any site in play with room, Resettle's own site included. Adviser-only and locked advisers are not offered. |
| 241 Moving Market | Site-only. A selected Trade modifier with no cost. After a Trade with Moving Market as its source, one question offers every empty slot at the other sites, plus Skip. A chosen slot moves the card there with its tokens, and the player gains 1 favor from the Nomad bank. |

## Slice 3: ACTION powers

| Card | Ruling |
|---|---|
| 61 Terror Spells | Cost 1 secret placed. Without the Darkest Secret, the power is still offered, pays its cost and does nothing. With it, kill exactly two warbands among the sites of the pawn's region and the boards of players whose pawn is in that region, the actor's own included. When only one split is possible, they are killed without asking. A site left empty is refilled with bandits. |
| 77 Witch's Bargain | Cost 1 secret placed. An outer loop chooses a player at the actor's site, not the actor. An inner loop offers "Give a secret, take 2 favor", "Give 2 favor, take a secret", "Pick a different player" (when another player is at the site) and Stop. The actor's give must be paid in full, so an exchange is offered only when it can be. The take is best effort. A faceup secret moves before a facedown one. Nobody is asked to consent. |
| 38 Inquisitor | Cost 1 secret placed. Choose a facedown Vision held by a player whose pawn is at the actor's site, the actor included, by its adviser slot. Then guess one of the five Vision names, the Conspiracy included. A correct guess discards that Vision. |
| 95 Relic Thief | Adviser-only. Cost 1 favor and 1 secret placed. Choose a faceup relic held by another player whose pawn is at the actor's site. Roll as many defense dice as its defense. If no shield is rolled, take it with a `Take`; a relic with defense 0 rolls nothing and is taken. A relic Lost Tongue protects is not offered. |
| 23 Ancient Binding | Cost 1 secret placed and 1 secret burnt. The actor burns every secret on their board and on cards, except the one placed on Ancient Binding. Every other player burns all but one secret from their board, keeping a faceup one if they have one, as Book Burning does. The Darkest Secret's own secrets are not burnt. |
| 151 Convoys | Site-only. Cost 1 favor placed. Choose another region's discard pile and put it, in order, on top of the pawn's region's pile, even if it is empty. The piles are offered as buttons with their own labels. |
| R28 Singing Mask | Cost 1 secret placed. Choose a denizen or ruined edifice at the pawn's site, a direction, and an amount from 0 to 2, between that card's suit bank and the People's Favor, whoever holds it. Nothing is asked when both are empty. |
| 80 Assassin | Adviser-only, locked. While a faceup adviser, its holder's adviser limit is 2, as for Silver Tongue and Insomnia. ACTION, cost 1 favor placed: discard a faceup adviser of a player whose pawn is at the actor's site, the actor included. Locked advisers are not offered, Assassin among them. |

## Slice 4: when played

| Card | Ruling |
|---|---|
| 63 Revelation | Each player in turn order, starting with the first player, chooses an amount from 0 to their favor, burns it, and gains as many secrets. A player with no favor is not asked. |
| 82 Blackmail | Choose a relic held by another player whose pawn is at the actor's site, faceup or facedown, by its slot. Its holder chooses "Give 3 favor" or "Hand over the relic". A holder with fewer than 3 favor is not asked and the relic is taken. The relic moves by `Take`, so a relic Lost Tongue protects is not offered. |
| 84 Dissent | Each player except the holder of the People's Favor, the actor included, places 1 favor on Dissent per suit of card they rule, or all they have. The favor returns to the Discord bank at Rest. |
| 208 Wizard's Conclave | The player who rules the most arcane cards takes the Darkest Secret, with its secrets. The actor chooses among tied players, themselves included. When nobody rules an arcane card, every player ties. When the winner already holds it, or Lost Tongue refuses the `Take`, nothing happens. |
| 100 Bandit Chief | Site-only. Kill one warband at every site in play not ruled by bandits, the actor's own included. A site left empty is refilled with bandits. |
| 129 A Round of Ale | Site-only. Run the Rest cleanup with the actor as the resting player: favor on every card in play returns to its suit's bank, secrets on cards go to the actor, and the actor's facedown secrets are flipped faceup. The Rest favor window is not declared, so League Treaty does not apply. Cards cleared of tokens may be used again; powers used this turn stay used. |
| 246 Call for Help | Adviser-only, locked. The actor chooses an amount from 0 to the smaller of what is left of 6 and the size of the pawn's region's discard pile. Those cards are revealed to every player and discarded one at a time, each to the top of the next region's pile, so the first drawn ends deepest. While fewer than 6 have been drawn and the pile is not empty, the actor chooses "Keep drawing", which asks for another amount, or "Stop". The actor gains 2 warbands per nomad card revealed, up to their supply. |

## Slice 5: modifiers and restrictions

| Card | Ruling |
|---|---|
| 193 Vow of Poverty | Adviser-only, locked. Trade for favor is not offered to its holder. Favor a Trade modifier grants, such as Moving Market's, is not from Trade and is still gained. REST, optional: if the holder's board holds no favor, take 2 favor from a bank they choose, or what it holds. |
| 196 Vow of Beastkin | Adviser-only, locked. An automatic rule. Its holder may Muster only on a card of the same suit as one of their faceup advisers, so Beast cards always qualify. Other sources are hidden. Every Muster gains 1 more warband. |
| 102 Defame | A selected Trade modifier with no cost. After a Trade whose source is discord, an edifice by its suit, choose a player with favor, the actor included, and burn 1 of their favor. Skip is offered only when some player has no favor, since burning from an empty board is legal. When nobody has favor, nothing is asked. |
| 248 Mounted Library | Site-only. A selected Trade modifier with no cost. On a Trade for secrets with Mounted Library as its source, the secrets gained are doubled. Once selected, it is discarded at the end of the Trade whatever the Trade was. |
| 119 Curfew | Site-only, persistent. An enemy trading with a card its ruler rules, advisers included, pays 1 favor to that ruler, or burns it when the ruler is bandits, on top of the Trade's cost. A source the trader cannot pay for is not offered. |
| 150 Awaited Return | A selected Trade modifier. The Trade spends no Supply. Selectable only with a warband on the player's board; one of them is killed at the Trade's cost step. The printed card's action icon is Trade. |
| 158 Special Envoy | A selected Travel modifier with no cost. Travel spends no Supply, and after every other Travel effect the Act Phase ends. |
| 111 Tyrant | Adviser-only, locked. An automatic rule. After its holder Travels, one warband at the destination is killed, whoever's, if there is one. Other pawn moves (Quick Exit, a Raid's relocation) are not Travel. |
| 172 A Fast Steed | A selected Travel modifier. Cost 1 favor placed. Travel spends no Supply when the player has 3 or fewer warbands on their board, read when the cost is paid. The favor is paid even when the condition fails, as for Tents. The printed card's action icon is Travel. |
| 112 Forced Labor | Site-only, persistent. An enemy whose pawn is at a site its ruler rules pays 1 favor to that ruler, or burns it when the ruler is bandits, on top of a Search's cost. A Search the searcher cannot pay for is not offered. |
| 134 News from Afar | A selected Search modifier. Cost 2 favor placed. The Search spends no Supply, whatever its source. The printed card's action icon is Search. |

## Moved out of the batch

| Card | Where | Why |
|---|---|---|
| 240 Search Party | Catalog batch - engine-blocked, its own spec | A card played inside a WHEN PLAYED power fires neither its own WHEN PLAYED nor the card-play triggers (`WalkerHooks.gathered` is keyed by window), and Search re-reads its live hand on a single-card draw. |
| 122 Hunting Party | Implemented by the [follow-up actions design](2026-10-08-follow-up-actions-design.md) | Built as a Campaign queued after the Search rather than nested in it. The queued Campaign is a full action and may take modifiers. |
| 32 Magician's Code | Catalog batch - engine-blocked, Challenge | Its 2 secrets count toward placing more than the banner holds ("even none"), but `ChallengeProcedure.legalBanners` gates the start before any modifier acts. Selectable only when the Darkest Secret is reachable with its help. |
| 169 Way Station | Catalog batch - engine-blocked, Travel | The non-ruler's choice should appear only when Way Station is selected, which needs Travel's destination chosen inside the walker. Implemented with the travel destination design. |

# Catalog Batch 3: Per-Power Rulings

> Appendix to [the design](2026-09-29-catalog-batch-3-design.md). Each entry
> states the ruling agreed with the product owner on 2026-09-29. Card ids are
> catalog ids. "Placed" means placed onto the card and "burnt" means paid to
> the shared bank, per the cost rules of [batch 1](2026-09-20-powers-design.md).
> Every rule in
> [batch 1's rulings](2026-09-20-powers-rulings.md#rules-that-apply-to-every-power)
> that applies to every power applies here too: access, facedown cards,
> activation, repeat use, usability, secrets, movement and bury. The catalog
> has no FAQ entry for any card here, so every ruling comes from the printed
> text and the existing precedents.

## Rules that apply to the whole batch

- **All-Exile.** Every game is all-Exile, so every other player is an enemy.
  An Empire or Imperial clause does nothing until Empire rules exist.
- **Suits.** Only faceup cards have a suit (batch 2). Relics and banners have
  none. An edifice has its suit on both faces (`catalog.suitOf`).
- **Cards a player rules.** A player rules their faceup advisers and the
  denizens and edifices, on either face, at the sites they rule. Bandits rule
  the cards at the sites they rule, as `SiteRulers.rulerOfCard` already treats
  them for Toll Roads.
- **Cards counting themselves.** An italic reminder such as "(including
  Animal Host)" gives way to the main text it explains (rulebook p.42,
  "Interpreting rules"). A card counts itself only where the main text reaches
  it: Animal Host played as an adviser is not at a site, and Fabled Feast at a
  site its player does not rule is not ruled.
- **Acting on yourself.** When the text allows the acting player as a target,
  they may be chosen, as for Wolves and Hunger. Armed Mob, Second Chance and
  Whispering Leaves follow this.
- **Locked and the Grand Scepter.** The
  [Global operation restrictions](../../ROADMAP.md#phase---global-operation-restrictions)
  phase comes first. After it, a locked card refuses Move, Flip and Swap, and
  the Grand Scepter cannot be discarded or buried. No power in this batch
  filters locked cards or the Grand Scepter itself: the restriction refuses
  the operation, and the prerequisite's search hides the option. `Bury`
  still ignores locked.
- **"X to gain Y".** Y is gained only when X happened: Taming Charm, Arcane
  Brokers, Bog, Second Chance, Bandit Paymaster, Relic Breaker and Great Feast.
- **No target.** A paid power with no legal target pays its cost and does
  nothing, as Charming Friend does.
- **Amounts are best effort.** A gain, take or burn resolves to what its
  source holds.

## Slice 1: battle plans

Batch 2's plan rules apply: **Sign** ("±" is fixed by the side) and
**Conditions** (a plan with a condition is offered only when it holds at the
plan step). Further:

- **Enemy.** For an attacker's plan, the defender, a player or bandits. For a
  defender's plan, the attacker.
- **Zero.** A dice plan whose count would be zero is not offered, as Nature
  Worship is not.
- **"At end, discard".** Once the Campaign resolves, whoever won, the card is
  discarded through `PlanDiscard.afterCampaign`, as Horse Archers is. A bandit
  defender that applied the card discards it too.
- **"If you're victorious".** Resolved at the end of the Campaign, as Battle
  Honors is. A plan that also names a Campaign kind is offered only in that
  kind.
- **Bandit defenders** apply their free plans without choosing, as today, and
  never use a plan that costs.

### Slice 1a: dice and conditions

| Card | Side | Ruling |
|---|---|---|
| 71 Cracking Ground | either | Free. ±1 attack die per site the Campaign targets. A Raid targets no site, so it is not offered in a Raid. |
| 195 Walled Garden | defender | Site-only. Free. Used by the ruler of its site, and offered only when its site is a Conquest target. +1 defense die per beast denizen or beast edifice at any site in play, whoever rules that site. Walled Garden counts itself. |
| 222 Banner Breakers | attacker | Cost 1 favor placed and 1 favor burnt. +3 attack dice. Offered only when the defender holds the Darkest Secret or the People's Favor, so never against bandits. |
| 48 Extra Provisions | defender | Cost 1 favor placed. +1 defense die. |
| 132 Village Constable | either | Site-only. Free. ±2 attack dice. Offered only when the enemy does not hold the People's Favor. Bandits hold no banner, so it is always offered against them. |
| 124 Encirclement | either | Cost 1 favor placed. ±2 attack dice. Offered only when its user's force is strictly larger than the enemy's at the plan step. The attacker's force is the committed warbands; Brass Army's dice are not force. The defender's force is the warbands at every target in a Conquest, bandits included, or the defender's board in a Raid, as the defense counts it. A plan chosen earlier in the same window counts, such as Wrestlers' sacrifice. |
| R30 Bandit Standard | attacker | Free. The relic must be faceup in the attacker's play area. +1 attack die per bandit warband on the sites of the region of the attacker's pawn. Not offered in a Conquest against bandits, and not offered when that region holds no bandit. Usable in a Raid. |
| 156 Rival Khan | either | Free. ±4 attack dice. Offered only when the enemy has a faceup nomad adviser, so never against bandits. Discarded after the Campaign. |

### Slice 1b: ruled cards and rescoring

| Card | Side | Ruling |
|---|---|---|
| 20 Disgraced Captain | either | Cost 1 favor placed and 1 favor burnt. ±4 attack dice. Offered only when the Campaign's defender rules a faceup order card. It reads the defender whichever side uses it, as printed, so a defender's use checks their own cards. Against bandits, it holds when an order card stands at any site bandits rule. |
| 256 Battle Axes | either | Free. ±2 attack dice. Offered only when the enemy rules a beast card. Against bandits, it holds when a beast card stands at any site bandits rule. |
| 164 Great Crusade | either | Free. ±1 attack die per nomad card its user rules, counting itself, so always at least 1. A bandit defender counts the nomad cards at every site bandits rule. Discarded after the Campaign. |
| 243 Pledge of Defense | defender | Free. +1 defense die per nomad card its user rules, counting itself. A bandit defender counts as for Great Crusade. Discarded after the Campaign. |
| 137 The Great Levy | either | Cost 2 favor placed. ±3 attack dice. Offered only when the enemy does not hold the People's Favor. For an attacker it also ignores every skull the attack rolls, exactly as Outriders does. A defender rolls no attack dice, so for a defender it only removes 3 dice. With Outriders also chosen, the attack is scored the same. |
| 14 Rain Boots | attacker | Free. Only the defender rolls shields, so it is the attacker's plan. Each single-shield defense die scores 0, exactly as for Bag of Siegeworks. Two shields and doublers are unaffected. With Bag of Siegeworks also chosen, both write the same score. Discarded after the Campaign. |
| 255 Garrison Armory | defender | Cost 1 favor placed. Offered only in a Conquest. When the defense is scored, the warbands at the targets are added once more, so each adds 2. This comes after any single-shield rescoring, which applies to the dice only. The doubler multiplies the dice only. |

### Slice 1c: after the Campaign

| Card | Side | Ruling |
|---|---|---|
| 22 Book Burning | attacker | Free. Offered only in a Raid. If the attacker won, every secret on the defender's board is burnt except one. Facedown secrets are burnt first: they are turned faceup, then burnt. The defender keeps one secret, faceup if any remains. A defender with one secret or none loses nothing. |
| 239 Tribute Spoils | either | Cost 1 favor placed. Offered only in a Conquest. If its user won, they gain 1 favor for each denizen and edifice at the targeted sites, from that card's suit bank. Relics count nothing. |
| 106 Field Promotion | either | Cost 1 favor placed. If its user won, they gain 3 warbands, or what their supply holds. |
| 109 Military Parade | either | Free. If its user won, they gain 1 favor from the matching bank for each faceup adviser the enemy holds then. Bandits hold no advisers, so an attacker is not offered it against bandits. A bandit defender applies it, and if the bandits win, the favor moves from the banks to the shared bank, as for Battle Honors. |
| 184 Insect Swarm | (persistent) | Adviser-only, persistent, faceup. While its holder takes part in a Campaign, every plan the opposing side chooses costs 1 more favor, burnt. This is Gleaming Armor's rule with a different cost. The title's plan burns the favor from its user's board. A plan whose price, extra cost included, cannot be paid is not offered, so a bandit defender applies no plan. With Gleaming Armor on the same side, both extra costs apply. |

## Slice 2: modifiers, triggers, Rest and Wake

| Card | Ruling |
|---|---|
| 73 Initiation Rite | Adviser-only, locked. An automatic rule of a faceup adviser, as Vow of Obedience's is, although the catalog marks the power `persistent: false`: "must" is not a choice. When its holder Musters, the card mustered on receives 1 faceup secret from the holder's board instead of 1 favor. The Supply payment is unchanged. With no faceup secret, the holder cannot Muster. The secret makes the card not token-free, as a placed favor does. Trade is unaffected. |
| 205 Disciples | A selected Search modifier with no cost. When its user holds the Darkest Secret and Searches the world deck, the Search's Supply cost becomes 2. A cost of 2 or less is unchanged. The condition is read when the cost is paid. Otherwise the modifier does nothing. It may be selected whatever the source, as Cup of Plenty may. |
| 42 The Old Oak | Site-only. A selected Trade modifier with no cost. When the player Trades for secrets with The Old Oak as the source and has a faceup beast adviser, they gain 1 more secret. |
| 81 Downtrodden | A selected Muster modifier with no cost. When the card mustered on is of a suit whose bank holds strictly less favor than each of the other five, gain 2 more warbands. A tie for least, including several empty banks, gives nothing. The banks are read when the gain runs. |
| 231 Village Idiot | Site-only. A selected Muster modifier with no cost. When Village Idiot is the card mustered on, as for Rowdy Pub, gain 1 favor from the Hearth bank. |
| 142 Saddle Makers | Adviser-only, persistent, faceup. When another player plays a nomad or order denizen faceup, to a site or as a faceup adviser, the holder gains 2 favor from that suit's bank. Turning a facedown adviser faceup through the card-play procedure counts, as for Book Binders. A facedown play has no suit and does not count. A swap or a take is not a play. |
| 128 Crop Rotation | A selected Search modifier with no cost. It applies to a Search's play and to a facedown-adviser play, whose modifier window is Search's. When the player plays a card to a site, they may first discard one card there, exactly as the People's Favor Mob face permits (`PlacementRules.siteDiscardFirst`). At a full site the play is legal only with a discard. The generic discard rules decide which cards may go, and Crop Rotation itself, being selected, may not. |
| 157 Lost Tongue | Adviser-only, persistent, faceup. Unless the acting player rules a nomad card: (1) they cannot target the holder's relics or banners, which hides them at `CampaignTargetSelection`, as the Circlet of Command does; (2) they cannot `Take` the holder's relics or banners, which a registered restriction refuses. "Take" means the `Take` operation only. A `Give`, such as an agreed Negotiation transfer, is allowed, and so is taking the contents of a banner (Book of Records, Amber Flame). The exception is checked when the operation runs. Bandits target nothing. |
| 97 Insomnia | Adviser-only, locked. As a faceup adviser it lowers its holder's adviser limit to 2, exactly as Silver Tongue does, and playing it faceup needs room under that limit. With Silver Tongue as well, the limit stays 2. Horned Mask reads the same limit. REST: gain 1 secret. An optional Rest power, once per turn. |
| 258 Quartermaster | Site-only. WAKE: gain 1 Supply. An optional Wake power, once per turn, as Marble Fountains is. It is usable only by the ruler of its site, wherever their pawn is. |

### Target protections corrected

"Target" on a card refers only to a Campaign's target selection. Batch 2 and
earlier ruled otherwise for two cards, and this batch corrects them:

| Card | Ruling |
|---|---|
| R15 Circlet of Command | Persistent Campaign modifier. It hides its holder's banners and other relics at `CampaignTargetSelection` only. It no longer narrows a played Conspiracy's targets or a Challenge. |
| 75 Forgotten Vault | Persistent Campaign modifier. It hides its ruler's relics from enemies at `CampaignTargetSelection` only. It no longer narrows a played Conspiracy's targets. This replaces the batch-2 ruling. |

## Slice 3: ACTION powers

### Slice 3a: actions on yourself

| Card | Ruling |
|---|---|
| 62 Blood Pact | Cost 1 secret placed. With 2 or more warbands on the board, the player chooses a number of pairs from 0 to half their warbands, rounded down. They sacrifice twice that many and gain that many secrets. With fewer than 2, nothing is asked and nothing happens. |
| 204 Arcane Brokers | Cost 1 favor placed. Choose a relic the player holds, faceup or facedown, discard it, then gain 2 secrets. The choice is asked whenever there is a candidate, even one. |
| 210 Bog | Site-only. Free. Choose a relic the player holds and discard it, as for Arcane Brokers. Then gain 3 favor from the Beast bank. |
| 212 Bed of Roots | Site-only. Cost 3 favor burnt. Choose a faceup denizen adviser of the player's, locked or not, and bury it with the standard returns. Then gain 2 secrets. A faceup Vision is not an adviser. |
| 54 Tavern Songs | Free. The top 3 cards of the discard pile of the pawn's region, or fewer, are recorded as `Peek`s and shown in an `Inspect` decision, top first. An empty pile shows nothing and asks nothing. |
| 13 Tinker's Fair | Site-only. Cost 3 favor placed. Draw a relic and take it facedown, as Dowsing Sticks does. |
| 139 Relic Breaker | Free. Choose a relic the player holds, faceup or facedown, bury it with the standard returns, then gain 1 secret. |
| 105 Messenger | Cost 1 favor placed. The player arranges their warbands again over their board and every site they rule, as Warning Signals does: one exact distribution that keeps the total and leaves each site at least one warband. It is asked only when a warband can move. An answer that changes nothing is allowed. |
| R13 Skeleton Key | Cost 1 secret placed and 1 secret burnt. If the pawn's site is in the Hinterland, peek at every relic there and take one facedown, as Barbed Net does. Otherwise, or with no relic there, nothing happens. |

### Slice 3b: actions on other players

| Card | Ruling |
|---|---|
| 58 Quick Exit | Cost 1 secret placed. Choose another player whose pawn is at the player's site, then any other site in play. Place the pawn there with a plain `Move`, not a Travel. |
| 70 Dream Thief | Cost 2 favor placed. Choose one facedown adviser (a denizen or a Vision) of any player, the player's own included, then a facedown adviser of a different player, and swap them. Both questions use adviser slots, so no card is named. Each owner sees the card they receive and still knows the card they gave up. Asked only when at least two players hold facedown advisers. |
| 181 Second Chance | Cost 1 secret placed. Choose a player with a faceup order or discord adviser, the acting player included. Kill one warband on their board. When a warband was killed, the acting player gains 1 warband. |
| 211 Whispering Leaves | Adviser-only. Cost 1 secret placed. Choose a player whose pawn is at the player's site, the acting player included. That player places 2 favor from their board onto Whispering Leaves, with a `Give`, or all they have. Nothing is asked of them. The favor returns to the Beast bank at Rest, as any favor on a card does. |
| 96 Enchantress | Adviser-only. Cost 1 secret placed. Choose a faceup denizen adviser of another player and swap it with Enchantress. Both stay faceup and carry their favor and secrets. A locked adviser is not offered, because the Locked restriction refuses its swap. |
| 53 Armed Mob | Site-only. Cost 1 favor placed. The target is the player who holds the Darkest Secret, when they do not also hold the People's Favor. The acting player may be that player. Choose one of the target's faceup advisers and discard it with the standard discard. With no target, or no adviser that may be discarded, nothing happens. |
| 251 Honor Guard | Adviser-only. Cost 2 favor placed and 1 favor burnt. One question over the faceup advisers of every player whose pawn is at the player's site and who has no faceup order adviser, as Hunger asks. The user holds Honor Guard, an order adviser, so is never a candidate. Bury the chosen adviser with the standard returns. |
| R32 Amber Flame | Cost 1 secret placed. Choose a banner held by a player whose pawn is at the player's site, the player's own included, as for Book of Records. Burn 1 of what it holds: favor from the People's Favor, a secret from the Darkest Secret. An empty banner may be chosen and burns nothing. |

### Slice 3c: actions on sites, banks and banners

| Card | Ruling |
|---|---|
| 37 Taming Charm | Cost 1 secret placed. Choose a beast or nomad denizen or ruined edifice at the player's site that may be discarded. Discard it with the standard returns, then gain 2 favor from the bank of its suit. |
| 227 Dark Enforcer | Cost 1 favor burnt. Discard every order and hearth denizen and ruined edifice at the player's site, as far as the discard rules permit. Nothing is asked. |
| 257 Great Feast | Cost 1 favor placed. Choose a beast denizen or ruined beast edifice at the player's site that may be discarded. Discard it with the standard discard, then gain 3 Supply. |
| 65 Plague Engines | Cost 1 secret placed and 1 secret burnt. In seat order, each player, the acting player included, puts 1 favor per site they rule from their board into the Arcane bank, or all they have. Nothing is asked. |
| 191 Memory of Nature | Cost 1 secret placed. X is the number of beast denizens and edifices at sites in play, Memory of Nature included when at a site. Move X favor, or what the other five banks hold, into the Beast bank. The player splits it across banks only when two or more other banks hold favor and they hold more than X together, as Alchemist asks. |
| 219 Bandit Paymaster | Cost 1 favor placed. When the player's site holds 2 or more bandit warbands, remove one and gain 3 warbands. Otherwise nothing happens. Nothing is asked. |
| 52 Storyteller | Cost 1 favor placed. Move 1 secret from the shared bank onto the Darkest Secret, whoever holds it, or nobody. |
| 135 Levelers | Cost 1 secret placed. Move up to 2 favor from the bank with the most favor to the bank with the least, read after the cost. The player chooses among tied banks, the source first, then the destination. When every bank holds the same amount, the player chooses a source and a different destination. |
| 49 Memory of Home | Cost 1 secret placed and 1 secret burnt. Choose a bank other than Hearth that holds favor and move all of its favor to the Hearth bank. One stocked bank is chosen without asking. |
| 233 Firebrand | Cost 1 secret placed. Choose one: move 1 favor from a bank that holds favor to the People's Favor, or burn 1 favor from the People's Favor. Whoever holds the banner, or nobody, does not matter. A single option runs without asking. |
| 141 Ballot Box | Site-only. Cost 2 favor placed. "This site" is Ballot Box's site. When the player has a faceup adviser whose suit matches a denizen or edifice at this site, Ballot Box included, every warband there that is not theirs, bandits included, is replaced with their warbands from their supply. Warbands the supply cannot cover are killed. A site left empty is refilled with bandits after the action. |

## Slice 4: when played

A WHEN PLAYED power runs only when its card is played faceup, to a site or as
a faceup adviser. The card is already in place when the power runs. For an
adviser, "this site" and "this region" mean the pawn's site and region, as for
Catacombs.

| Card | Ruling |
|---|---|
| 179 Threatening Roar | Discard every nomad and beast denizen and ruined edifice at sites in the pawn's region, as far as the discard rules permit, as Dazzle does. Played to a site, it discards itself. |
| 91 Riots | Discard every denizen and ruined edifice at sites in the pawn's region, as far as the discard rules permit. Played to a site, it discards itself and counts itself. Then burn as many favor from the People's Favor as cards were discarded, whoever holds it, or nobody. |
| 190 Animal Host | Gain a warband for each beast denizen and edifice at every site in play, whoever rules it. Played to a site, it counts itself; as an adviser, it does not. |
| 18 Key to the City | Site-only. The site's ruler is read when the power runs. If the ruler's pawn is at the site, nothing happens; this includes the actor ruling it. Otherwise kill every warband there, gain 1 warband, and move 1 warband from the actor's board to the site. With no warband on the board, nothing is placed, and the refill puts bandits on the emptied site. |
| 79 Charlatan | If the Darkest Secret holds more than 1 secret, burn all but 1, whoever holds it, or nobody. |
| 226 Bandit Prince | Adviser-only, locked. Choose any number of bandit-ruled sites, or none. At each, `Replace` all its bandits with the actor's warbands from their supply. An answer whose sites hold more bandits than the supply holds is refused, so a site is never half replaced. |
| 147 Salad Days | Gain 1 favor from each of three different banks. With more than three stocked banks, the actor chooses three. With three or fewer, the actor gains 1 from each and is not asked. |
| 136 Fabled Feast | X is the number of hearth cards the actor rules, Fabled Feast included where ruled. Take up to X favor from one stocked bank the actor chooses. |
| 236 Town Meeting | X is counted as for Fabled Feast. Gain X favor, split across the stocked banks as Alchemist splits. **Unresolved ruling:** the text may instead mean one bank for all, as Fabled Feast's does. The split is used until the ruling is settled. |
| 30 Great Herd | Site-only. The actor may swap it with a nomad denizen or ruined nomad edifice at another site in play, whoever rules it. Each card keeps its favor and secrets. The card moved in does not run its own WHEN PLAYED. |
| 117 Royal Tax | Every other player whose pawn is at a site the actor rules in the pawn's region gives 2 favor to the actor, as a `Take`. Nothing is asked. |

## Dropped

| Card | Missing mechanism |
|---|---|
| 3 Bear Traps | A way to lower the attacker's force after it is answered. The committed warbands stay on the board, and the skull cap, the sacrifice maximum and the losses read the answered force. |
| 171 Hospitality | A Travel that can pause on a decision. Travel rebuilds from the pawn's site, and its destination preview rejects a tree that pauses. |

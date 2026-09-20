# Powers Batch 1: Per-Power Rulings

> Appendix to [the design](2026-09-20-powers-design.md). Each entry states the ruling agreed with the product owner. Card ids are catalog ids. "Cost placed" means placed onto the card, per the cost rules in the design. Amounts of gain are best-effort: the operation resolves to what the bank or track can give.

## Rules that apply to every power

- **Access.** You can use cards at your site (the site with your pawn), cards at sites you rule, and everything in your play area: relics, banners, advisers and legacies. A ruler can use a ruled site's card whatever site their pawn is on.
- **Facedown cards.** Denizens at sites are always faceup. Relics at sites are always facedown, so a site relic never grants access. A relic must be faceup in your play area to be used. Relics taken by a power draw are taken facedown.
- **Activation.** Modifiers are selected at the start of a major action. Persistent powers are automatic. Battle plans are chosen at the Campaign plan step. When Played powers fire on the play.
- **Repeat use.** Act-phase powers have no once-each limit. A costed one is limited only because its card holds resources afterwards. Wake and Rest powers are once per turn.
- **Usability.** The only gate is that the cost is payable. Choosing an empty target, or a bank with no stock, is allowed and does nothing.
- **Secrets.** The shared bank has unlimited secrets.
- **Movement.** A pawn relocation is a plain `Move`. No component restricts a pawn's `Move`. Relocations that are not Travel run no Travel windows and trigger no Travel powers.
- **Bury.** Bury ignores the locked restriction. Denizens and Visions go to the bottom of the world deck, edifices to the edifice deck, relics to the bottom of the relic deck. Resources on a buried card return as for a discard: favor to the suit bank, secrets to the acting player, facedown.
- **Giving to bandits** equals burning.
- **Dice.** Non-battle rolls are automatic. Defense dice score with `DefenseDieFace.score` (a Doubler multiplies the total). Attack dice score with `AttackDieFace.score` (hollow swords one per pair, a skull face counts two swords) and a skull is any skull face.

## Slice 0: verify only

| Card | Check |
| --- | --- |
| 35 Dazzle | Discards every Hearth and Order card at sites in your region, as far as the generic discard rules permit. |
| 201 Catacombs | Recover modifier, 1 secret placed, relic drawn and placed facedown at the card's site if it has an empty relic slot. Likely mismatch: `applicable` reads only the pawn's site, not ruled sites. |
| 237 League Treaty | Off-turn Rest decision by the ruler; moves favor from cards in its region to one bank. |

## Slice 1: When Played

| Card | Ruling |
| --- | --- |
| 15 A Small Favor | Gain four warbands, capped by the warband bank. |
| 28 Faithful Friend | Gain 4 Supply, clamped at the track maximum. |
| 7 Garrison | Count the sites you rule once, when played. Gain that many warbands, then put one warband from your board on each ruled site. If your board is short, you choose which sites receive one. Otherwise no decision is asked. |
| 133 Family Heirloom | Draw a relic. Only you see it. Choose take it facedown, or put it on the bottom of the relic deck. An empty relic deck does nothing. |

Locked and adviser-only are card restrictions in the data, not part of the power.

## Slice 1: ACTION powers

| Card | Ruling |
| --- | --- |
| 47 Wayside Inn | Cost 1 favor placed. Gain 2 Supply. |
| 26 Elders | Cost 2 favor placed. Gain 1 secret from the shared bank. |
| 9 Alchemist | Cost 1 secret placed and 1 secret burnt. Gain 4 favor from any bank or banks: a `Distribute` with a total of exactly min(4, favor available across all banks). No decision is asked when one bank holds all the available favor, or when 4 or fewer are available (you take everything). The favor goes to your board. |
| 39 Wolves | Cost 1 secret placed. Choose one player board, yours included, and kill one warband there. If it has none, nothing happens. Only player boards count. |
| 180 Fae Merchant | Cost 1 secret placed. Draw a relic and take it (assumed facedown). Then put exactly one relic you hold, except the Grand Scepter, on the bottom of the relic deck. The just-taken relic is eligible. A decision is asked only when there is more than one candidate. |
| 17 Sleight of Hand | Cost 1 favor placed. Targets are other players whose pawn is at your site and who have 2 or more secrets on their board (faceup and facedown together). Take one secret, faceup first, otherwise facedown. It arrives with the same orientation. With no legal target the cost is paid and nothing else happens. The take is a `Take`, so other powers may restrict it. |
| 93 Gambling Hall | Cost 2 favor placed. Roll 4 defense dice. When the total X is above zero, choose any favor bank, even an empty one, and take min(X, its stock). |
| R09 Dowsing Sticks | Cost 1 secret placed and 2 secrets burnt. Draw a relic from the relic deck and take it facedown. An empty deck does nothing. |
| R21 Crystal Vial | Cost 1 secret placed and 1 secret burnt. Choose an adviser you hold (denizen or Vision, either orientation) or a card in the site's card list at your pawn's site (denizens and the edifice, intact or ruined). Bury it with the standard returns. The choice is required when a candidate exists. |
| R24 Bone Dice | Cost 1 secret placed. Roll 2 attack dice. Gain Supply equal to the sword score. If any skull face rolled, bury this relic afterwards with the standard returns, so the secret you just placed returns to you facedown. |
| E15 Murky Fountain (ruined) | Cost 1 secret placed on the edifice card. If your pawn is at this site: roll 2 defense dice and gain Supply equal to the total. A total of zero also ends your Act phase with `EnterPhase(Rest)`, without the Begin Rest validation gate. If your pawn is elsewhere, the cost is paid and nothing else happens. |
| R08 Whistle | Cost 1 secret placed on the Whistle. Choose another player whose pawn is at a different site. Move their pawn to your site, then move the secret from the Whistle to their board. With no eligible player the cost is paid, nothing else happens and the secret stays. |
| R03 Brass Horse | Cost 1 secret placed. "Your region" is the region of your pawn's site. Reveal the top card of that region's discard pile, then turn it facedown again. Place your pawn at a different site holding a card of the same suit (denizen or edifice). No decision is asked when exactly one site matches. If the pile is empty, the top is a Vision, or no site matches, place it at any other site. |
| R16 Ivory Eye | Cost 1 secret placed. Choose any facedown adviser of any player, yours included, and `Peek` at it. The peek is private. Other players see only a log line saying who peeked at whose adviser. |
| R39 Magic Carpet | No cost. Place your pawn at any site, including your current one, in which case the move is skipped. Then choose one: discard the Carpet with `Discard.Relic` (to the set-aside relic pile), or give it, faceup, to a player whose pawn is at a site different from your new one. With no eligible player the only choice is to discard. |
| R45 Magic Waterskin | The relic must be faceup in your play area. Bury it first, with the standard returns, then gain 4 Supply. |

## Slice 1: WAKE powers

| Card | Ruling |
| --- | --- |
| E15 Marble Fountains (intact) | Wake. If your pawn is at this site, refresh Supply to the leftmost space: `GainSupply` up to the track maximum of 7. Once per turn. |
| R06 Horned Mask | Wake. Take a non-edifice denizen from your pawn's site as a facedown adviser. `site-only` denizens are eligible, and locked ones are decided by the `Take` restrictions. If you already have 3 advisers you choose one of yours to discard, as in card play. Resources on the taken card return by the standard returns. |

## Slice 2: modifiers

All are selected at the start of the major action, and once selected they apply for free.

| Card | Ruling |
| --- | --- |
| 56 Augury | Search from the world deck or a regional discard draws one more card. The draw still stops after a Vision. |
| R04 Truthful Harp | Search draws 2 more cards. Every drawn card is revealed while in your hand. Harp and Augury stack. |
| 29 Tents | Cost 1 favor placed. If the destination is in the region of your pawn's current site, Travel costs no Supply. |
| 43 Forest Paths | Cost 1 favor placed. If the destination holds a beast-suit denizen or edifice, Travel costs no Supply, and the powers of sites are ignored for that Travel (`shouldIgnore` on site-sourced Travel powers). |
| R07 Cup of Plenty | Non-persistent, so a Trade modifier. Trading with a card whose suit differs from every faceup adviser you hold costs no Supply. Facedown advisers do not count. |
| 144 Rowdy Pub | Muster from Rowdy Pub as the source gains one more warband, on top of the matching-adviser bonus. The source is read from the answered `muster.source` decision through a `BuildOps`. |
| R20 Dragonskin Drum | After Travel, gain one warband, appended after the Move. |
| 173 Relic Worship | Non-persistent after the catalog fix. `applicable` requires a secret and an empty card. After the relic is taken (`RecoverAfterRelic`), pay 1 secret placed with a required `PayCost` and gain 2 Supply. Limitation: another selected modifier spending your only secret first makes the payment fail late. |
| 120 Knights Errant | After Muster you may Campaign for no Supply. An appended `Decide` is offered only if a Campaign is legal, and a `Branch` builds the Campaign tree at walk time from live state, so its force sees Muster's warbands. A hook on `CampaignCost` drops the `SpendSupply` for the nested Campaign only. One action boundary runs after Muster. |

## Slice 2: persistent rules

| Card | Ruling |
| --- | --- |
| 118 Toll Roads | Enemies (every player except the ruler) cannot travel to a site ruled by Toll Roads' ruler unless they pay 1 favor. This covers all the ruler's sites, including Toll Roads' own. The payment is a `Give` with `required = true` to a player ruler, and a required `PayCost` with a burnt favor for a bandit ruler. A traveller who cannot pay does not get that destination. Empire rulers are unsupported. |
| 178 Grasping Vines | An enemy traveling from a site ruled by the Vines' ruler kills one warband on their own board if able. The ruler is exempt. It is an unconditional, non-required `Kill(1)` inserted before the Move, so stacked kills resolve against live state. |
| R15 Circlet of Command | Faceup. Players other than the holder cannot target the holder's banners or their relics other than the Circlet. It restricts Raid target options, Challenge banner selection and Conspiracy's target list. |
| E28 Oaken Fortress (intact) | While its ruler is at this site, they cannot be targeted by a Challenge or a Raid. The Empire clause is unsupported. |
| E28 Rotting Fortress (ruined) | Players at this site cannot be targeted by a Challenge or a Raid unless the targeting player has a faceup beast adviser. |

For both Fortress faces, a Raid removes the protected player from the defender decision, and a Challenge removes the banner they hold from banner selection. Conquest is unaffected.

## Slice 2: card-play triggers

| Card | Ruling |
| --- | --- |
| 189 Wild Cry | Selected modifier. When you play a beast denizen faceup (to a site or as a faceup adviser), gain 1 Supply and 2 warbands. Facedown plays do not trigger it. A card does not trigger on its own play. |
| 50 Welcoming Party | Selected modifier. When you play a denizen that is not a facedown adviser, gain 1 favor from the Hearth bank with `Gain.Favor`. A card does not trigger on its own play. |
| 99 Gossip | Persistent, faceup, adviser-only. When any other player places an adviser facedown, a denizen or a Vision, the holder gains 1 favor from the Discord bank with `Gain.Favor`. |

## Slice 3: Campaign battle plans

Plans are chosen at the plan step, only by the source's ruler, and may pay onto an occupied card. Off-turn payments settle immediately. Each source is used once per Campaign. Unaffordable plans are not offered and the preview shows the total cost.

| Card | Side | Ruling |
| --- | --- | --- |
| 12 Mercenaries | either | Cost 1 favor placed. An attacker adds 3 attack dice. A defender removes 3 from the attacker's pool, minimum 0. If its user is defeated (`!attackerWins` for the attacker, `attackerWins` for the defender), it is discarded through the standard denizen discard. Player-chosen sign is deferred. |
| 1 Wrestlers | defender | Cost: sacrifice one warband from the defender's force, which is their board in a Raid or a warband at a target site they rule in a Conquest (chosen if several). It lowers the recorded force by 1. Effect: +1 defense die. |
| R27 Fearsome Shield | defender | Cost 2 secrets burnt. +2 defense dice. |
| E20 Towering Rampart | defender | +2 defense dice if the ruler's pawn is at this site or this site is a Conquest target. |
| E20 Cracked Rampart | defender | +1 defense die if this site is a Conquest target. A Raid never targets a site. |
| 25 Warning Signals | defender | A `Distribute.exactly` over the defender's board and every site they rule, total conserved, each ruled site's minimum 1. It applies when chosen, before the defender's force is recorded. Player defenders only. After the Campaign fully resolves it is discarded, unconditionally. |
| 2 Battle Honors | either | Chosen at the plan step. After the result, if its user won (the attacker when `attackerWins`, else the defender), gain 2 favor from the Order bank with `Gain.Favor`. |
| R01 Sticky Fire | either | If its user wins, a second prompt at `CampaignLosses`, owned by the winner, asks whether to kill all warbands in the enemy's force. Attacker wins a Conquest: the defender's half-return is cancelled. Attacker wins a Raid: every warband on the defender's board dies, not half. Defender wins: every warband on the attacker's board dies, committed or not. Then the winner gives the loser 1 favor if able, a non-required `Give`. Against bandits it burns the favor. |

Persistent modifier, not a plan:

| Card | Ruling |
| --- | --- |
| 66 Gleaming Armor | While its holder, faceup as an adviser, is a Campaign participant, every plan chosen by the opposing side costs 1 more secret, placed on the plan's source card. Unaffordable plans are not offered and the preview includes it. The Oathkeeper title plan has no card, so its added cost is flipping a faceup secret facedown. |

Off-turn settlement for a defender's plan payment: favor moves directly to the matching suit bank, and a secret becomes a `FlipSecrets(FaceUp, FaceDown)`. Nothing rests on the card.

## Slice 4: banner faces

| Power | Ruling |
| --- | --- |
| Darkest Secret: Wandering Flame, move | An Act `PhasePower` sourced from the banner, usable by the holder only, no cost, unlimited. Place your pawn at any other site with a secret on the site itself (`SiteState.tokens.secrets`), not on its cards. A plain `Move`, not Travel. |
| Darkest Secret: Wandering Flame, place a secret | A second `PhasePower` with its own id, holder only, no cost, unlimited. Move 1 secret from your board onto the site your pawn is at. No secret is a no-op. |
| People's Favor: Mob | Holder only. When you play a card to a site you may first discard a card from the site's card list. It is enabled through `PlacementRules.siteDiscardFirst`. At a full site, where a play is normally impossible, it makes the play legal. The card goes through the standard discard: facedown to the next region's discard, favor to the suit bank, secrets to you facedown. `DiscardRestrictions` decide what is discardable, so an intact edifice is refused as locked. |

## Deferred and parked

- **Deferred**: card-slot redesign of card play; Mercenaries' player-chosen sign; defender-side activation of non-plan modifiers (Battle Honors already works for a defender, since it is a plan); Empire rulers; Peace Envoy and other plan-restricting powers.
- **Parked**: Great Market, Bandit Market, Great Forge, Broken Forge, Proving Grounds and Empty Grounds (SETUP / WHEN EXPLORED).

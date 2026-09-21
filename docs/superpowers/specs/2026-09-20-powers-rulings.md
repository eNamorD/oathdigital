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
- **Active modifiers.** Active modifiers cannot be discarded. A modifier cannot be discarded during the major action it is modifying.
- **Bury.** Bury ignores the locked restriction. Denizens and Visions go to the bottom of the world deck, edifices to the edifice deck, relics to the bottom of the relic deck. Resources on a buried card return as for a discard: favor to the suit bank, secrets to the acting player, facedown.
- **Giving to bandits** equals burning.
- **Dice.** Non-battle rolls are automatic. Defense dice score with `DefenseDieFace.score` (a Doubler multiplies the total). Attack dice score with `AttackDieFace.score` (hollow swords one per pair, a skull face counts two swords) and a skull is any skull face.

## Slice 0: verify only

| Card | Check |
| --- | --- |
| 35 Dazzle | Discards every Hearth and Order denizen and every ruined Hearth or Order edifice at sites in your region, as far as the generic discard rules permit. Intact edifices are locked. |
| 201 Catacombs | Recover modifier, 1 secret placed, relic drawn and placed facedown at the card's site if it has an empty relic slot. Usable from a card at your site, at a site you rule, or held as an adviser. Recover continues at the pawn's site, and if no relic is recovered as a result, that is permitted. |
| 237 League Treaty | Off-turn Rest decision by the ruler; moves favor from cards in its region to one bank. |

### Slice 0 verification results

- **35 Dazzle:** did not match. It discarded denizens only. It now also discards ruined Hearth and Order edifices in the region (`Discard.RuinedEdifice`, in card order with the denizens), and intact edifices, other suits and other regions stay. A missing catalog edifice is `OathViolation.UnknownEdifice`. Tests in `DazzleSuite`.
- **201 Catacombs:** matches the ruling. It was pawn-site only, so it now runs through `PowerAccess` and is usable from a card at your site, at a site you rule, or held as an adviser. The relic goes to the card's own site (the pawn's site for an adviser), and its payment is `Costs.onCard`. A site the actor neither rules nor stands on makes the modifier not applicable. Tests in `CatacombsContributionSuite`.
- **237 League Treaty:** matches the ruling. Favor on an edifice in the region, intact or ruined, moves with the rest. Its resolution now comes from the catalog flag (`persistent: true`, so automatic, as before). Test in `LeagueTreatySuite`.

## Slice 1: When Played

| Card | Ruling |
| --- | --- |
| 15 A Small Favor | Gain four warbands, capped by the warband bank. Implemented (slice 1a). |
| 28 Faithful Friend | Gain 4 Supply, clamped at the track maximum. Implemented (slice 1a). |
| 7 Garrison | Count the sites you rule once, when played. Gain that many warbands, then put one warband from your board on each ruled site. If your board is short, you choose which sites receive one. Otherwise no decision is asked. Implemented (slice 1a). |
| 133 Family Heirloom | Draw a relic. Only you see it. Choose take it facedown, or put it on the bottom of the relic deck. An empty relic deck does nothing. Implemented (slice 1a). |

Locked and adviser-only are card restrictions in the data, not part of the power.

## Slice 1: ACTION powers

| Card | Ruling |
| --- | --- |
| 47 Wayside Inn | Cost 1 favor placed. Gain 2 Supply. Implemented (slice 1a). |
| 26 Elders | Cost 2 favor placed. Gain 1 secret from the shared bank. Implemented (slice 1a). |
| 9 Alchemist | Cost 1 secret placed and 1 secret burnt. Gain 4 favor from any bank or banks: a `Distribute` with a total of exactly min(4, favor available across all banks). No decision is asked when one bank holds all the available favor, or when 4 or fewer are available (you take everything). The favor goes to your board. Implemented (slice 1c). |
| 39 Wolves | Cost 1 secret placed. Choose one player board, yours included, and kill one warband there. If it has none, nothing happens. Only player boards count. The kill is not the player's option: it always runs, as a best-effort (non-required) `Kill`, which an empty board skips. Implemented (slice 1c). |
| 180 Fae Merchant | Cost 1 secret placed. Draw a relic and take it (assumed facedown). Then put exactly one relic you hold, except the Grand Scepter, on the bottom of the relic deck. The just-taken relic is eligible. A decision is asked only when there is more than one candidate. Implemented (slice 1b). |
| 17 Sleight of Hand | Cost 1 favor placed. Targets are other players whose pawn is at your site and who have 2 or more secrets on their board (faceup and facedown together). Take one secret, faceup first, otherwise facedown. It arrives with the same orientation. With no legal target the cost is paid and nothing else happens. The take is a `Take`, so other powers may restrict it. Implemented (slice 1c). |
| 93 Gambling Hall | Cost 2 favor placed. Roll 4 defense dice. When the total X is above zero, choose any favor bank, even an empty one, and take min(X, its stock). Implemented (slice 1b). |
| R09 Dowsing Sticks | Cost 1 secret placed and 2 secrets burnt. Draw a relic from the relic deck and take it facedown. An empty deck does nothing. Implemented (slice 1b). |
| R21 Crystal Vial | Cost 1 secret placed and 1 secret burnt. Choose an adviser you hold (denizen or Vision, either orientation) or a card in the site's card list at your pawn's site (denizens and the edifice, intact or ruined). Bury it with the standard returns. The choice is required when a candidate exists. Implemented (slice 1c). |
| R24 Bone Dice | Cost 1 secret placed. Roll 2 attack dice. Gain Supply equal to the sword score. If any skull face rolled, bury this relic afterwards with the standard returns, so the secret you just placed returns to you facedown. Implemented (slice 1b). |
| E15 Murky Fountain (ruined) | Cost 1 secret placed on the edifice card. If your pawn is at this site: roll 2 defense dice and gain Supply equal to the total. A total of zero also ends your Act phase with `EnterPhase(Rest)`, without the Begin Rest validation gate. If your pawn is elsewhere, the cost is paid and nothing else happens. Implemented (slice 1b). |
| R08 Whistle | Cost 1 secret placed on the Whistle. Choose another player whose pawn is at a different site. Move their pawn to your site, then move the secret from the Whistle to their board. With no eligible player the cost is paid, nothing else happens and the secret stays. Implemented (slice 1d). |
| R03 Brass Horse | Cost 1 secret placed. "Your region" is the region of your pawn's site. Reveal the top card of that region's discard pile, then turn it facedown again. Place your pawn at a different site holding a card of the same suit (denizen or edifice). No decision is asked when exactly one site matches. If the pile is empty, the top is a Vision, or no site matches, place it at any other site. Implemented (slice 1d). |
| R16 Ivory Eye | Cost 1 secret placed. Choose any facedown adviser of any player, yours included, and `Peek` at it. The peek is private. Other players see only a log line saying who peeked at whose adviser. Implemented (slice 1c), privacy half only: the log line waits for the action log. |
| R39 Magic Carpet | No cost. Place your pawn at any site, including your current one, in which case the move is skipped. Then choose one: discard the Carpet with `Discard.Relic` (to the set-aside relic pile), or give it, faceup, to a player whose pawn is at a site different from your new one. With no eligible player the only choice is to discard. Implemented (slice 1d). |
| R45 Magic Waterskin | The relic must be faceup in your play area. Bury it first, with the standard returns, then gain 4 Supply. Implemented (slice 1a). |

## Slice 1: WAKE powers

| Card | Ruling |
| --- | --- |
| E15 Marble Fountains (intact) | Wake. If your pawn is at this site, refresh Supply to the leftmost space: `GainSupply` up to the track maximum of 7. Once per turn. Implemented (slice 1a). |
| R06 Horned Mask | Wake. Take a non-edifice denizen from your pawn's site as a facedown adviser. `site-only` denizens are eligible, and locked ones are decided by the `Take` restrictions. If you already have 3 advisers you choose one of yours to discard, as in card play. Resources on the taken card return by the standard returns. "Already have 3 advisers" is the player's adviser limit, so a faceup Silver Tongue holder is full at 2. Implemented (slice 1c). |

### Slice 1a implementation notes

- **Family Heirloom:** holds the drawn relic facedown in the player's play area during the choice, because a temporary hand cannot hold a relic. "Put it on the bottom" buries it again.
- **Garrison:** the site decision appears only when the warband bank was too short to fill the board, which needs a bank that the ruled sites and boards have nearly emptied.
- **Warband gains:** `Gain.Warbands` reduces to what the bank holds. A Small Favor and Garrison tests pin this.
- **Test staging:** the first game deals only some denizens. Cards 7, 26, 28 and 47 are not in its world deck, so the test fixture adds them where a test needs them.
- **Marble Fountains:** E15 was in the edifice deck of the first game, so the fixture places it from there.
- **Magic Waterskin:** a secret on the relic returns to its holder facedown before the relic goes to the bottom of the relic deck.

### Slice 1b implementation notes

- **Shape:** each power is a `PaidAction` in `gameplay/powers/action`, registered through `DiceAndRelicDrawPowers`. Rolls are automatic, one pool key per power. Gambling Hall's bank choice is a live `Branch` after the roll.
- **Bone Dice:** reads the relic's tokens after the engine pays the cost, so the placed secret returns facedown on the bury.
- **Murky Fountain:** a zero total emits `EnterPhase(Rest)` and leaves the player awaiting a Rest action. It does not auto-finish Rest when no REST power is usable, as Begin Rest does (product decision).
- **Fae Merchant:** the put-back is independent of the draw, so with an empty relic deck a held relic still goes to the bottom (product decision). Eligibility is read after the draw and a decision is asked only for more than one candidate. The Grand Scepter is read from the catalog's relic role. The taken relic is facedown (confirmed).
- **Dowsing Sticks:** needs three faceup secrets, one placed and two burnt.
- **Test staging:** card 93 is not dealt in the first game and the fixture adds it, card 180 is in the world deck and is removed, E15 is placed from the edifice deck, and R09 and R24 are taken from the relic deck.

### Slice 1c implementation notes

- **Shape:** each ACTION power is a `PaidAction` in `gameplay/powers/action` and Horned Mask is a `PhasePower` in `gameplay/powers/wake`, all registered through `TargetPowers`. Every decision that depends on state is a live `Branch` after the cost, with a `BuildOps` for the effect, because `rebuild` derives the tree again against the state after the cost was paid.
- **Wolves:** the kill is a best-effort (non-required) `Kill`, so it always runs and an empty board is skipped. The decision is a plain `Decide` over all players.
- **Alchemist:** the distribution is asked only when two or more banks hold favor and more than 4 is available in all. Then the query is well formed. Otherwise the power takes `min(stock, 4)` from each bank without asking.
- **Sleight of Hand:** a one-secret `Take` from a board holding both orientations is ambiguous (`AmbiguousSecretOrientation`), so a mixed target gets `FlipSecrets` (facedown up), the `Take`, and `FlipSecrets` (back down) as one batch. It nets to one faceup secret moving.
- **Crystal Vial:** the printed text says "a denizen at your site"; the ruling adds the edifice, and the power follows the ruling. It holds the catalog, because the standard returns need the buried card's suit.
- **Ivory Eye:** the options are `Button`s keyed `adviser:<owner>:<slot>`, because an option naming another player's facedown adviser makes the projector drop the whole decision. A recorded `Peek` replays into `knowledge.advisers`, which the presentation layer reads, so only the peeker is told the card. An `AdviserSlot` option is on the ROADMAP.
- **Horned Mask:** the adviser limit is read by `AdviserLimit.of` (3, or 2 for a faceup Silver Tongue holder), a new helper in `gameplay/powers` that repeats Silver Tongue's rule as a read of state and that card play does not use. The taken card is a `Take` followed by a `Flip`, and the returns are the returns half of `Bury.standard`. The discard goes to the next region's pile, as in `CardPlay`, and a `LockedAdviserOnly` adviser is never offered. A use with nothing to take still records the Wake use (confirmed).
- **Locked denizens:** every locked denizen is adviser-only, so none can be at a site and no `Take` restriction is needed for Horned Mask.
- **Test staging:** cards 9, 17, 26, 39 and 47 are added or removed by the fixtures as needed, and R06, R16 and R21 are taken from the relic deck. `TargetsFixture` holds the helpers.

### Slice 1d implementation notes

- **Shape:** each ACTION power is a `PaidAction` in `gameplay/powers/action`, registered through `MovementPowers`. Shared reads and writes are in `PawnMoves`. A decision that depends on state is a live `Branch`, and the effect is a `BuildOps` that reads the recorded answer through `PowerAnswers`. All three relocate a pawn with a plain `Move`, so no Travel window runs.
- **Brass Horse reveal:** the reveal is a public `Reveal` of the top card of the pile. A card in a regional discard has no orientation state, so `Reveal` (a faceup `Flip`) was rejected. It is now accepted at `Location.RegionalDiscard` as a no-op (`OperationStateAdapter.isDiscardLook`, used by `OperationValidator` and `OperationStateMutation`), and "turn it facedown again" needs no operation. A facedown `Flip` there is still unsupported. Brass Horse holds the catalog, because a card's suit is a catalog fact.
- **Brass Horse matching:** a ruined edifice counts as a card of its suit, as does an intact one. The pawn's own site never matches. The decision is asked only when more than one site qualifies.
- **Whistle:** the decision is asked even with one eligible player. The given secret arrives faceup on the target's board, and with no eligible player the secret stays on the Whistle, which keeps it unusable.
- **Magic Carpet:** with no eligible player the Carpet is discarded without a second question. A secret resting on a given Carpet goes with it, and a discarded Carpet returns its secrets to its holder facedown.
- **Test staging:** R03, R08 and R39 are taken from the relic deck. `MovementFixture` holds the helpers and builds on `TargetsFixture`.

## Slice 2: modifiers

All are selected at the start of the major action. A modifier's cost is paid at the very start of the action, whether or not the modifier then has an effect, and the costs of all selected modifiers must be payable together (refused at selection otherwise). Once selected they apply for free.

| Card | Ruling |
| --- | --- |
| 56 Augury | Implemented (slice 2d). Search from the world deck or a regional discard draws one more card. The draw still stops after a Vision. |
| R04 Truthful Harp | Implemented (slice 2d). Search draws 2 more cards. Every drawn card is revealed while in your hand. Harp and Augury stack. |
| 29 Tents | Implemented (slice 2c). Cost 1 favor placed. If the destination is in the region of your pawn's current site, Travel costs no Supply. |
| 43 Forest Paths | Implemented (slice 2c). Cost 1 favor placed. If the destination holds a beast-suit denizen or edifice, Travel costs no Supply, and the powers of sites are ignored for that Travel (`shouldIgnore` on site-sourced Travel powers). |
| R07 Cup of Plenty | Implemented (slice 2d). Non-persistent, so a Trade modifier. Trading with a card whose suit differs from every faceup adviser you hold costs no Supply. Facedown advisers do not count. |
| 144 Rowdy Pub | Implemented (slice 2d). Muster from Rowdy Pub as the source gains one more warband, on top of the matching-adviser bonus. The source is read from the answered `muster.source` decision through a `BuildOps`. |
| R20 Dragonskin Drum | Implemented (slice 2c). After Travel, gain one warband, appended after the Move. |
| 173 Relic Worship | Implemented (slice 2d). Non-persistent after the catalog fix. `applicable` requires a secret and an empty card. Its cost, 1 secret placed, is paid at the start of the Recover with the other selected modifiers' costs, and is refused at selection if they cannot all be paid together (Catacombs with one faceup secret). After the relic is taken (`RecoverAfterRelic`), gain 2 Supply. A Recover that ends without a relic has still paid the secret. |
| 120 Knights Errant | Implemented (slice 2f). After Muster you may Campaign for no Supply. An appended `Decide` is offered only if a Campaign is legal, and a `Branch` builds the Campaign tree at walk time from live state, so its force sees Muster's warbands. A hook on `CampaignCost` drops the `SpendSupply` for the nested Campaign only. One action boundary runs after Muster. |

## Slice 2: persistent rules

| Card | Ruling |
| --- | --- |
| 118 Toll Roads | Implemented (slice 2c). Enemies (every player except the ruler) cannot travel to a site ruled by Toll Roads' ruler unless they pay 1 favor. This covers all the ruler's sites, including Toll Roads' own. The payment is a `Give` with `required = true` to a player ruler, and a required `PayCost` with a burnt favor for a bandit ruler. A traveller who cannot pay does not get that destination. Empire rulers are unsupported. |
| 178 Grasping Vines | Implemented (slice 2c). An enemy traveling from a site ruled by the Vines' ruler kills one warband on their own board if able. The ruler is exempt. It is an unconditional, non-required `Kill(1)` inserted before the Move, so stacked kills resolve against live state. |
| R15 Circlet of Command | Implemented (slice 2e). Faceup. Players other than the holder cannot target the holder's banners or their relics other than the Circlet. It restricts Raid target options, Challenge banner selection and Conspiracy's target list. |
| E28 Oaken Fortress (intact) | Implemented (slice 2e). While its ruler is at this site, they cannot be targeted by a Challenge or a Raid. The Empire clause is unsupported. |
| E28 Rotting Fortress (ruined) | Implemented (slice 2e). Players at this site cannot be targeted by a Challenge or a Raid unless the targeting player has a faceup beast adviser. |

For both Fortress faces, a Raid removes the protected player from the defender decision, and a Challenge removes the banner they hold from banner selection. Conquest is unaffected.

## Slice 2: card-play triggers

| Card | Ruling |
| --- | --- |
| 189 Wild Cry | Implemented (slice 2b). Selected modifier. When you play a beast denizen faceup (to a site or as a faceup adviser), gain 1 Supply and 2 warbands. Facedown plays do not trigger it. A card does not trigger on its own play. |
| 50 Welcoming Party | Implemented (slice 2b). Selected modifier. If you play a denizen face up when first drawn, gain 1 favor from the Hearth bank with `Gain.Favor`. A card does not trigger on its own play. |
| 99 Gossip | Implemented (slice 2b). Persistent, faceup, adviser-only. When any other player places an adviser facedown, a denizen or a Vision, the holder gains 1 favor from the Discord bank with `Gain.Favor`. |

### Slice 2 implementation notes

- **2a:** `PlacementRules` replaces the adviser limits and composes; the tree with no contributor is unchanged, and with one the placement path gains a level. Generic discard rules (product decisions): a faceup locked adviser, an intact edifice and a card that prints a power selected for the running action cannot be discarded by any path, and `DiscardRestrictions` is where that is enforced. A Homeland replacement discards an edifice and no longer buries it. An intact edifice is refused by the generic rule for Mob's discard too. `AdviserLimit.of` and Horned Mask no longer repeat Silver Tongue's or card play's rules.
- **2b:** every selected modifier's cost is paid at the start of its action and all are validated together at selection (`ContributingPower.selectionPayments`); Catacombs states its secret. `SelectedModifier` checks a modifier's action, access and cost at selection. Welcoming Party is a denizen played faceup straight from the Search's draw, to a site or as a faceup adviser; a facedown placement and a card that was already a facedown adviser do not trigger it. Wild Cry cannot be discarded while selected. `PowerCtx.procedure` exists (E9).
- **2c:** a selected modifier's cost is paid at the start of every Travel, whatever the route (permissive, product decision); the Supply saving and Forest Paths' ignore apply only when the condition holds. A free Travel is still a destination candidate, with cost 0. Toll Roads and Grasping Vines find their ruler as the ruler of the site the card stands at and ignore a facedown copy.
- **2d:** the Truthful Harp reveals by recording a `Peek` for every other player and restricts nothing; the hand itself stays private in projections, and the other players remember a revealed card played facedown. Augury and the Harp stack. Relic Worship pays its secret at the start of the Recover and gains its 2 Supply after the relic is taken; Catacombs plus Relic Worship with one faceup secret is refused at selection. The Cup of Plenty is free for a player with no faceup adviser. The reviewed entry for Relic Worship is now a selected, implemented handler.
- **2e:** Conspiracy's target decision has a window and is dropped when a power removes every option. The Fortress start refusal applies until the Campaign has answered one of its decisions. Circlet's protection covers Raid targets, Challenge banners and Conspiracy targets and never the Circlet itself.
- **2f:** restrictions are checked against the tree a power adds and the answers a command carries, so Vow of Peace and the Fortress apply to Knights Errant's nested Campaign, which is refused when the player answers "campaign". The Muster registry entry recognises the Campaign's decision ids and the `muster.` prefix.

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
| R01 Sticky Fire | either | If its user wins, a second prompt at `CampaignLosses`, owned by the winner, asks whether to kill all warbands in the enemy's force. Attacker wins a Conquest: the defender's half-return is cancelled. Attacker wins a Raid: every warband on the defender's board dies, not half. Defender wins: every warband on the attacker's board dies, committed or not. Then the winner gives the loser 1 favor if able, a non-required `Give`. Against bandits it burns the favor, as a `Give` to `Location.SharedBank`. |

Persistent modifier, not a plan:

| Card | Ruling |
| --- | --- |
| 66 Gleaming Armor | While its holder, faceup as an adviser, is a Campaign participant, every plan chosen by the opposing side costs 1 more secret, placed on the plan's source card. Unaffordable plans are not offered and the preview includes it. The Oathkeeper title plan has no card, so its added cost is flipping a faceup secret facedown. |

Off-turn settlement for a defender's plan payment: favor moves directly to the matching suit bank, and a secret becomes a `FlipSecrets(FaceUp, FaceDown)`. Nothing rests on the card.

### Slice 3 implementation notes

- **3a:** `CampaignResult.victorious` is now `attackerWins` in the model, the journal codec, the shared DTO and its codec, the result panel and the suites. It is true when the attacker prevailed and false when the defender did. The wire key changes with it, and journals are forward-only, so a game whose journal holds a recorded Campaign result cannot be read after this change.
- **3b:** every plan is a `BattlePlan` power declared as an `Offer` (where its card must stand, what it costs and does) and, when it acts later, a hook at a later window. The user must be the ruler of the source: the origin-site offer to a non-ruler is gone, and Brass Army no longer needs an empty relic. A defender's plan may carry a cost, paid at once. Costs are `Favor` and `Secret` (placed onto the card, which may be occupied), `FavorBurnt`, `SecretBurnt` and `SacrificeWarband` (a defender only: the board in a Raid, or a target site the defender rules in a Conquest, asking which when several). A plan that cannot be paid, with every power's added cost, is not offered, and a window with nothing to offer is skipped. The option states its price. A source is chosen once. A bandit defender applies every cost-free plan at a site Bandits rule that no power makes unpayable, pays nothing, and records what it applied in a pool marker (`campaign.plan-applied.<kind>.<id>`) that a later hook reads, because a bandit's plan is not an answer. A facedown adviser is revealed when it is chosen, and a card at a site is always faceup. Outriders scores the attack again without the skull cap, and Brass Army adds four dice to the pool but not to the force.

## Slice 4: banner faces

| Power | Ruling |
| --- | --- |
| Darkest Secret: Wandering Flame, move | An Act `PhasePower` sourced from the banner, usable by the holder only, no cost, unlimited. Place your pawn at any other site with a secret on the site itself (`SiteState.tokens.secrets`), not on its cards. A plain `Move`, not Travel. |
| Darkest Secret: Wandering Flame, place a secret | A second `PhasePower` with its own id, holder only, no cost, unlimited. Move 1 secret from your board onto the site your pawn is at. No secret is a no-op. |
| People's Favor: Mob | Holder only. When you play a card to a site you may first discard a card from the site's card list. It is enabled through `PlacementRules.siteDiscardFirst`. At a full site, where a play is normally impossible, it makes the play legal. The card goes through the standard discard: facedown to the next region's discard, favor to the suit bank, secrets to you facedown. `DiscardRestrictions` decide what is discardable, so an intact edifice is refused as locked. |

## Deferred and parked

- **Deferred**: card-slot redesign of card play; Mercenaries' player-chosen sign; defender-side activation of non-plan modifiers (Battle Honors already works for a defender, since it is a plan); Empire rulers; Peace Envoy and other plan-restricting powers.
- **Parked**: Great Market, Bandit Market, Great Forge, Broken Forge, Proving Grounds and Empty Grounds (SETUP / WHEN EXPLORED).

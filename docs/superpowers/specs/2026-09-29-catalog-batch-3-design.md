# Catalog Batch 3: Cards Without Engine Changes

**Status:** designed 2026-09-29. Implementation starts after the
[Global operation restrictions](../../ROADMAP.md#phase---global-operation-restrictions)
phase, which it depends on. The per-card rulings are in
[the rulings appendix](2026-09-29-catalog-batch-3-rulings.md).

**Builds on** [Powers batch 1](2026-09-20-powers-design.md) and
[Catalog batch 2](2026-09-26-catalog-batch-2-design.md), whose vocabulary,
cost rules, walker shapes and log-line rules apply unchanged.

## Goal and scope

Implement every unimplemented denizen and relic that copies the shape of an
implemented power, with no engine change. Before this batch, 60 of 255
denizens (10 per suit) and 23 of 48 relics are implemented. The first-game
generator orders implemented cards first, so each card added here gives games
more variety.

The bar for "no engine change": no new operation, walker window, decision
query kind, option kind, procedure step, `NoteArg` kind, protocol change or
frontend change. A refactor inside `gameplay/powers/` is allowed, such as
extracting a helper from an existing power or parameterising one.

In scope, 69 powers:

| Suit | Count | Cards |
|---|---|---|
| Arcane | 9 | 71 Cracking Ground, 73 Initiation Rite, 205 Disciples, 37 Taming Charm, 62 Blood Pact, 65 Plague Engines, 70 Dream Thief, 58 Quick Exit, 204 Arcane Brokers |
| Beast | 10 | 195 Walled Garden, 184 Insect Swarm, 42 The Old Oak, 181 Second Chance, 191 Memory of Nature, 210 Bog, 211 Whispering Leaves, 212 Bed of Roots, 179 Threatening Roar, 190 Animal Host |
| Discord | 12 | 20 Disgraced Captain, 222 Banner Breakers, 22 Book Burning, 81 Downtrodden, 97 Insomnia, 96 Enchantress, 219 Bandit Paymaster, 227 Dark Enforcer, 18 Key to the City, 79 Charlatan, 91 Riots, 226 Bandit Prince |
| Hearth | 18 | 48 Extra Provisions, 132 Village Constable, 137 The Great Levy, 231 Village Idiot, 142 Saddle Makers, 128 Crop Rotation, 52 Storyteller, 53 Armed Mob, 54 Tavern Songs, 135 Levelers, 13 Tinker's Fair, 49 Memory of Home, 139 Relic Breaker, 141 Ballot Box, 233 Firebrand, 147 Salad Days, 136 Fabled Feast, 236 Town Meeting |
| Nomad | 7 | 14 Rain Boots, 156 Rival Khan, 164 Great Crusade, 243 Pledge of Defense, 239 Tribute Spoils, 157 Lost Tongue, 30 Great Herd |
| Order | 10 | 124 Encirclement, 255 Garrison Armory, 256 Battle Axes, 106 Field Promotion, 109 Military Parade, 258 Quartermaster, 105 Messenger, 251 Honor Guard, 257 Great Feast, 117 Royal Tax |
| Relics | 3 | R30 Bandit Standard, R13 Skeleton Key, R32 Amber Flame |

After the batch, 126 of 255 denizens and 26 of 48 relics are implemented.

It also corrects two implemented cards: Circlet of Command and Forgotten
Vault stop narrowing a played Conspiracy's targets (see the rulings).

### How the cards were chosen

A survey of the 197 unimplemented denizens and relics, excluding those known
to be blocked, sorted each card into one of three groups. 71 copy an
implemented power's shape. 51 probably do, but need one point checked first.
75 need an engine change. Before this spec was written, each of the 71 was
checked against the code of the power it copies. Two failed and are dropped
(see the rulings): Bear Traps and Hospitality.

### Out of scope

- **The 51 cards to verify** are listed in the roadmap under
  **Catalog - to verify**, each with the point to check.
- **The 75 cards needing engine changes**, plus Bear Traps and Hospitality,
  are listed in the roadmap under **Phase - Catalog batch - engine-blocked**,
  grouped by the missing mechanism.
- **The Locked rule and the Grand Scepter rule** belong to the prerequisite
  phase. Powers here rely on them and do not filter locked cards or the
  Grand Scepter themselves.

## Prerequisite

The **Global operation restrictions** phase lets powers register an
`OperationRestriction` that every walker step, `MinorActions` and
`StateBasedEvaluation` apply. The same phase moves Locked onto that seam, so a
locked card refuses Move, Flip and Swap. It also adds the Grand Scepter's
"cannot be removed from play", which refuses its discard and bury. This batch
uses the seam in three places:

- **Lost Tongue** registers a restriction on `Take`.
- **Enchantress** relies on Locked to refuse swapping a locked adviser.
- **Arcane Brokers, Bog and Relic Breaker** rely on the Grand Scepter rule.

## Powers-side refactors

Each is built in the slice that first needs it, without changing the behavior
or the tests of the power it comes from.

| Id | Refactor | Users | Slice |
|---|---|---|---|
| P1 | A read of the cards of a suit a side rules: faceup advisers, plus denizens and edifices at the sites it rules, bandits included | Disgraced Captain, Battle Axes, Great Crusade, Pledge of Defense, Lost Tongue, Fabled Feast, Town Meeting | 1b |
| P2 | Bag of Siegeworks' single-shield rescoring as a shared helper | Rain Boots | 1b |
| P3 | Gleaming Armor generalised into a plan surcharge parameterised by cost and note | Insect Swarm | 1c |
| P4 | Silver Tongue's adviser limit as a class parameterised by card. `AdviserLimit.of` takes the lowest limit | Insomnia | 2 |
| P5 | `PowerAnswers.amount`, reading a `ChooseAmount` answer | Blood Pact | 3a |
| P6 | Warning Signals' warband distribution as a helper taking the site set | Messenger | 3a |
| P7 | Dazzle's region discard with a suit filter. Dazzle gains the `none` line its users log | Dazzle, Threatening Roar, Riots | 4 |
| P8 | Alchemist's favor split with the amount as a parameter | Town Meeting, Memory of Nature | 3c |

## Seams used for the first time

These exist in the model and the codec, but no power has used them yet. Each
gets a test through a power before the power relies on it:

- `Replace` (Ballot Box, Bandit Prince). Kill the warbands that cannot be
  replaced before the `Replace`, because a site cannot hold two kinds of
  warband.
- `Swap` of two site cards (Great Herd).
- `Burn` from a banner (Charlatan, Riots, Firebrand, Amber Flame) and a
  move from a bank onto a banner (Storyteller, Firebrand).
- `ChooseMany` with favor-bank options (Salad Days).
- `ChooseAmount` parked by a power (Blood Pact).
- `Give` whose giver is not the active player (Whispering Leaves, Plague
  Engines). Fall back to a plain `Move` if the pipeline refuses it.
- A mixed `ChooseOne` of favor banks and a button (Firebrand).

If a seam turns out to need an engine change, its card is dropped and moved to
the engine-blocked phase with the reason.

## Slicing

Slices are grouped by the kind of power, so each plan and each reviewer checks
one shape. They run in this order. Each plan is written when its slice starts.

| Slice | Cards | Refactors |
|---|---|---|
| 1a. Battle plans: dice and conditions | Cracking Ground, Walled Garden, Banner Breakers, Extra Provisions, Village Constable, Encirclement, Bandit Standard, Rival Khan | |
| 1b. Battle plans: ruled cards and rescoring | Disgraced Captain, Battle Axes, Great Crusade, Pledge of Defense, The Great Levy, Rain Boots, Garrison Armory | P1, P2 |
| 1c. Battle plans: after the Campaign | Book Burning, Tribute Spoils, Field Promotion, Military Parade, Insect Swarm | P3 |
| 2. Modifiers, triggers, Rest and Wake | Initiation Rite, Disciples, The Old Oak, Downtrodden, Village Idiot, Saddle Makers, Crop Rotation, Lost Tongue, Insomnia, Quartermaster; the Circlet of Command and Forgotten Vault fix | P4 |
| 3a. Actions on yourself | Blood Pact, Arcane Brokers, Bog, Bed of Roots, Tavern Songs, Tinker's Fair, Relic Breaker, Messenger, Skeleton Key | P5, P6 |
| 3b. Actions on other players | Quick Exit, Dream Thief, Second Chance, Whispering Leaves, Enchantress, Armed Mob, Honor Guard, Amber Flame | |
| 3c. Actions on sites, banks and banners | Taming Charm, Dark Enforcer, Great Feast, Plague Engines, Memory of Nature, Bandit Paymaster, Storyteller, Levelers, Memory of Home, Firebrand, Ballot Box | P8 |
| 4. When played | Threatening Roar, Riots, Animal Host, Key to the City, Charlatan, Bandit Prince, Salad Days, Fabled Feast, Town Meeting, Great Herd, Royal Tax | P7 |

The battle plans come first because most of them copy existing plans. A card
that proves to need an engine change mid-slice is dropped, its blocker is
recorded in the roadmap, and the slice continues.

## Log lines

Every power follows the
[Power log lines design](2026-09-26-power-log-lines-design.md): its wording
rules, its covering, and its "No line" categories. `{Red}` is the acting
player's chip and `{Blue}` and `{Green}` other players'. Amounts are what
happened, never the printed number. Only existing `NoteArg` kinds are used:
`Player`, `Card`, `Cards`, `Site`, `Amount`, `Number`, `Bank`, `Banner` and
`Pile`. No line names a region, because there is no region argument.

Each ACTION note is the power's `used` line, or `used.<variant>` where a key
is named. WHEN PLAYED and battle-plan notes use their own key names. "Covers"
names the generic lines the note replaces. Where it is marked "verify", the
plan confirms which generic line exists.

### Slice 1: battle plans

| Card | Line | Covers |
|---|---|---|
| Rival Khan, Great Crusade, Pledge of Defense, Rain Boots, key `discarded` | {Card}: Discarded after the Campaign. | |
| Book Burning, key `burned` | Book Burning: Burned {n secrets} from {Blue}'s board. | |
| Book Burning, key `none` | Book Burning: {Blue} had no secret to burn. | |
| Field Promotion, at least one warband gained | Field Promotion: {Red} gained {n warbands}. | |
| Insect Swarm, after the taxed plan | Insect Swarm: {Red}'s battle plans cost {1} extra favor, burnt. | |
| The Great Levy, attacker, a skull was rolled | The Great Levy: Skulls ignored. | |
| Rain Boots, a single shield was rolled | Rain Boots: Single shields ignored. | |
| Garrison Armory, the targets held a warband | Garrison Armory: Warbands on the targets added {n} more defense. | |

Insect Swarm copies Gleaming Armor's placement and deduplication.

**No line:** Cracking Ground, Walled Garden, Disgraced Captain, Banner
Breakers, Extra Provisions, Village Constable, Encirclement, Battle Axes and
Bandit Standard, whose effect is only a dice change. Neither do the dice of
The Great Levy, Rival Khan, Great Crusade and Pledge of Defense. Tribute
Spoils and Military Parade, whose favor the Gain lines show, as for Battle
Honors.

### Slice 2: modifiers, triggers, Rest and Wake

| Card | Line | Covers |
|---|---|---|
| Insomnia (REST) | Insomnia: {Red} gained {1} secret. | the Gain line |
| Quartermaster (WAKE) | Quartermaster: {Red} gained {n} Supply. | |
| Saddle Makers | Saddle Makers: {Blue} gained {n} favor from {the Nomad bank}. | the Gain line |
| Crop Rotation, after the discard answer | Crop Rotation: {Red} may discard a card at their site first. | |
| Lost Tongue, hide hook at a Campaign's targets | Lost Tongue: {Blue}'s banners and relics cannot be targeted. | |

Quartermaster writes nothing on a full track, falling back to "Used
Quartermaster", as for Wayside Inn. Lost Tongue's refused `Take` writes no
line: a refused operation is never offered. Explaining restricted options is
the roadmap's **Explain restricted options where they are offered** item.

**No line:** Initiation Rite and Disciples, whose cost changes the start
line's cost span shows. The Old Oak and Downtrodden, whose amount changes
the Gain lines show, as for Rowdy Pub. Village Idiot's favor gain, which the
generic Gain line shows. Insomnia's adviser limit, as for Silver Tongue.

### Slice 3: ACTION powers

| Card | Line | Covers |
|---|---|---|
| Blood Pact | Blood Pact: {Red} sacrificed {n warbands} and gained {m secrets}. | the Gain line |
| Blood Pact, key `used.none` | Blood Pact: {Red} sacrificed no warbands. | |
| Arcane Brokers | Arcane Brokers: {Red} discarded {relic} and gained {n secrets}. | the Discard and Gain lines (verify) |
| Arcane Brokers, key `used.none` | Arcane Brokers: {Red} held no relic. | |
| Bog | Bog: {Red} discarded {relic} and gained {n favor} from {the Beast bank}. | the Discard and Gain lines (verify) |
| Bog, key `used.discarded`, Beast bank empty | Bog: {Red} discarded {relic}. | the Discard line |
| Bog, key `used.none` | Bog: {Red} held no relic. | |
| Bed of Roots | Bed of Roots: {Red} buried {card} and gained {n secrets}. | the Buried and Gain lines (verify) |
| Bed of Roots, key `used.none` | Bed of Roots: {Red} had no faceup adviser. | |
| Tavern Songs | Tavern Songs: {Red} peeked at the top of the {Cradle discard pile}: {cards}. | the Peeked lines |
| Tavern Songs, key `used.empty` | Tavern Songs: {Red} peeked at the {Cradle discard pile}, which was empty. | |
| Tinker's Fair | Tinker's Fair: {Red} drew {relic} facedown. | |
| Tinker's Fair, key `used.empty` | Tinker's Fair: The relic deck was empty. | |
| Relic Breaker | Relic Breaker: {Red} buried {relic} and gained {1 secret}. | the Buried and Gain lines (verify) |
| Relic Breaker, key `used.none` | Relic Breaker: {Red} held no relic. | |
| Messenger | Messenger: {Red} redistributed their warbands. | nothing: the Moved lines stay, since they tell where warbands went |
| Messenger, key `used.none` | Messenger: No warband could be moved. | |
| Skeleton Key | Skeleton Key: {Red} took {relic} facedown from {site}. | the Peeked lines |
| Skeleton Key, key `used.none` | Skeleton Key: {site} held no relic. | |
| Skeleton Key, key `used.away` | Skeleton Key: {Red} was not at a Hinterland site. | |
| Quick Exit | Quick Exit: Placed {Blue} at {site}. | |
| Quick Exit, key `used.none` | Quick Exit: No other pawn was at {site}. | |
| Dream Thief | Dream Thief: {Red} swapped {Blue}'s {card} with {Green}'s {card}. | |
| Dream Thief, key `used.none` | Dream Thief: No two facedown advisers could be swapped. | |
| Second Chance | Second Chance: Killed {n} {Blue} warband, and {Red} gained {1 warband}. | |
| Second Chance, key `used.killed`, the player's supply was empty | Second Chance: Killed {n} {Blue} warband. | |
| Second Chance, key `used.spared` | Second Chance: {Blue} had no warband to kill. | |
| Second Chance, key `used.none` | Second Chance: No player had a faceup Order or Discord adviser. | |
| Whispering Leaves | Whispering Leaves: {Blue} placed {n favor} on it. | |
| Whispering Leaves, key `used.empty` | Whispering Leaves: {Blue} had no favor to place. | |
| Enchantress | Enchantress: {Red} swapped it for {Blue}'s {card}. | |
| Enchantress, key `used.none` | Enchantress: No faceup adviser could be swapped. | |
| Armed Mob | Armed Mob: {Red} discarded {card} from {Blue}'s advisers. | the Discarded line |
| Armed Mob, key `used.none` | Armed Mob: No player held the Darkest Secret without the People's Favor. | |
| Armed Mob, key `used.empty` | Armed Mob: {Blue} had no faceup adviser to discard. | |
| Honor Guard | Honor Guard: {Red} buried {card} from {Blue}'s advisers. | the Buried line |
| Honor Guard, key `used.none` | Honor Guard: No adviser could be buried. | |
| Amber Flame | Amber Flame: {Red} burned {1 favor} from {Blue}'s {People's Favor}. / Amber Flame: {Red} burned {1 secret} from {Blue}'s {Darkest Secret}. | |
| Amber Flame, key `used.empty` | Amber Flame: {Blue}'s {banner} held nothing to burn. | |
| Amber Flame, key `used.none` | Amber Flame: No player at the site held a banner. | |
| Taming Charm | Taming Charm: {Red} discarded {card} and gained {n favor} from {the Beast bank}. | the Gain line; the Discard line if one note can cover both steps (verify) |
| Taming Charm, key `used.discarded`, the bank was empty | Taming Charm: {Red} discarded {card}. | |
| Taming Charm, key `used.none` | Taming Charm: {site} held no Beast or Nomad card. | |
| Dark Enforcer | Dark Enforcer: Discarded {cards}. | the Discard lines, as for Dazzle |
| Dark Enforcer, key `used.none` | Dark Enforcer: {site} held no Order or Hearth card to discard. | |
| Great Feast | Great Feast: {Red} discarded {card} and gained {n} Supply. | the Discarded line |
| Great Feast, key `used.none` | Great Feast: {site} held no Beast card. | |
| Plague Engines, one line per player who paid | Plague Engines: {Blue} put {n favor} into {the Arcane bank}. | |
| Plague Engines, key `used.none` | Plague Engines: No player put favor into {the Arcane bank}. | |
| Memory of Nature | Memory of Nature: Moved {n favor} to {the Beast bank}. | |
| Memory of Nature, key `used.none` | Memory of Nature: No favor moved to {the Beast bank}. | |
| Bandit Paymaster | Bandit Paymaster: Removed {1} bandit warband from {site}, and {Red} gained {n warbands}. | |
| Bandit Paymaster, key `used.none` | Bandit Paymaster: {site} had no bandit to spare. | |
| Storyteller | Storyteller: {Red} placed {1 secret} on the {Darkest Secret}. | |
| Levelers | Levelers: Moved {n favor} from {the Beast bank} to {the Arcane bank}. | |
| Levelers, key `used.empty` | Levelers: Every favor bank was empty. | |
| Memory of Home | Memory of Home: Moved {n favor} from {the Order bank} to {the Hearth bank}. | |
| Memory of Home, key `used.empty` | Memory of Home: Every other favor bank was empty. | |
| Firebrand | Firebrand: {Red} moved {1 favor} from {the Order bank} to the {People's Favor}. | |
| Firebrand, key `used.burned` | Firebrand: {Red} burned {1 favor} from the {People's Favor}. | |
| Firebrand, key `used.empty` | Firebrand: Every favor bank and the People's Favor were empty. | |
| Ballot Box | Ballot Box: Replaced {n} {Blue} warband at {site}. | the Moved line of the `Replace` |
| Ballot Box, key `used.bandits` | Ballot Box: Replaced {n} bandit warband at {site}. | the Moved line |
| Ballot Box, keys `removed` and `removed.bandits`, the supply ran short | Ballot Box: Removed {n} {Blue} warband at {site}. / Ballot Box: Removed {n} bandit warband at {site}. | |
| Ballot Box, key `used.unmatched` | Ballot Box: {Red} had no adviser matching a card at {site}. | |
| Ballot Box, key `used.none` | Ballot Box: {site} held no warband to replace. | |

Card arguments read as a card back to a viewer who may not identify the card,
with no special case. `{n}` before "warband" and "bandit warband" is
`NoteArg.Number` with `Plural`, as for Siege Engines. Every decision whose
choice the note names (Arcane Brokers, Bog, Bed of Roots, Relic Breaker,
Taming Charm, Great Feast, Levelers, Memory of Home, Firebrand, Amber Flame) is
declared narrated, as Fae Merchant's is.

**No line:** Tavern Songs' `Inspect`, which the peek line tells, as for
Scryer.

### Slice 4: when played

| Card | Line | Covers |
|---|---|---|
| Dazzle, key `none` (new) | Dazzle: Nothing was discarded. | |
| Threatening Roar, key `discarded` | Threatening Roar: Discarded {cards}. | the Discard lines |
| Threatening Roar, key `none` | Threatening Roar: Nothing was discarded. | |
| Riots, key `discarded` | Riots: Discarded {cards}. | the Discard lines |
| Riots, key `burned` | Riots: Burned {n favor} from the {People's Favor}. | |
| Riots, key `unburned` | Riots: The {People's Favor} had no favor to burn. | |
| Riots, key `none` | Riots: Nothing was discarded. | |
| Animal Host, key `gained` | Animal Host: {Red} gained {n warbands}. | |
| Animal Host, key `none` | Animal Host: {Red} gained no warbands. | |
| Key to the City, key `killed` | Key to the City: Killed {n} {Blue} warband at {site}. | |
| Key to the City, key `bandits` | Key to the City: Killed {n} bandit warband at {site}. | |
| Key to the City, key `placed` | Key to the City: {Red} placed {1 warband} at {site}. | the Moved line |
| Key to the City, key `unplaced` | Key to the City: {Red} had no warband to place. | |
| Key to the City, key `guarded` | Key to the City: {Blue} was at {site}. | |
| Charlatan, key `burned` | Charlatan: Burned {n secrets} from the {Darkest Secret}. | |
| Charlatan, key `none` | Charlatan: The {Darkest Secret} had no secret to burn. | |
| Bandit Prince, key `replaced`, one line per site | Bandit Prince: Replaced {n} bandit at {site} with {Red}'s warbands. | that site's Moved line |
| Bandit Prince, key `none`, no bandit site | Bandit Prince: No site was ruled by bandits. | |
| Salad Days, key `none` | Salad Days: Every favor bank was empty. | |
| Fabled Feast, key `took` | Fabled Feast: {Red} took {n favor} from {the Hearth bank}. | the Gain line |
| Fabled Feast, key `empty` | Fabled Feast: Every favor bank was empty. | |
| Fabled Feast, key `none` | Fabled Feast: {Red} ruled no Hearth card. | |
| Town Meeting, key `gained` | Town Meeting: {Red} gained {n favor}. | nothing: each bank's Gain line stays, as for Alchemist |
| Town Meeting, key `empty` | Town Meeting: Every favor bank was empty. | |
| Town Meeting, key `none` | Town Meeting: {Red} ruled no Hearth card. | |
| Great Herd, key `swapped` | Great Herd: {Red} swapped it with {card} at {site}. | |
| Great Herd, key `none` | Great Herd: No Nomad card could be swapped. | |
| Royal Tax, key `took`, one line per player | Royal Tax: {Red} took {n favor} from {Blue}. | |
| Royal Tax, key `broke` | Royal Tax: {Blue} had no favor to take. | |
| Royal Tax, key `none` | Royal Tax: No player could be taxed. | |

Threatening Roar and Riots share Dazzle's read of the discarded cards, so a
card a restriction kept is not named. Key to the City's `guarded` line names
the ruler, the actor included.

**No line:** Salad Days' gains, which the Gain lines tell. Bandit Prince and
Great Herd declined, as for Twin Brother. Key to the City's gain, which the
`placed` line tells.

## Testing

- Every card gets a walker-driven suite that starts the action or reaches the
  plan step, answers its decisions, and asserts the resulting state, the
  recorded steps and its exact log line. Each suite covers its rulings' edge
  cases: a cost that cannot be paid, an empty target, an empty bank, pile or
  deck, a bandit enemy, and off-turn payment for a defender's plan.
- Each seam used for the first time gets a test through its first power:
  `Replace` with a full, short and empty supply; a site-to-site `Swap`; a
  banner burn with the banner held and unheld; `ChooseMany` of favor banks;
  a power's `ChooseAmount`; a `Give` by a non-active player.
- Frontend suites cover `ChooseMany` with favor-bank options and a mixed
  favor-bank and button `ChooseOne`, if the existing panels need no change.
  If they do, the card is dropped under the bar.
- Each powers-side refactor keeps the existing suites of the power it came
  from passing unchanged.
- The Circlet of Command and Forgotten Vault fix updates their suites: a
  Conspiracy targets their holder's relics and banners again.
- `PowerImplementationStatusSuite` and `PowerKindsCatalogSuite` pin the new
  cards.
- `BackendArchitectureSuite` still applies: files stay under 800 lines, no
  power names in walker sources, and powers do not import `gameplay.walker`.

## Verify at plan time

- **Prerequisite.** Which operation Conspiracy, Challenge and Campaign spoils
  use to move a relic or banner. Lost Tongue refuses only `Take`.
- **Restriction look-ahead.** That options a registered restriction refuses
  are hidden (Enchantress' locked advisers, the Grand Scepter for Arcane
  Brokers, Bog and Relic Breaker).
- **Bandit Prince.** That a `BuildOps` violation after the answer leaves the
  decision parked, so an over-large answer is refused. If it does not, stop
  and ask the product owner before choosing another ruling.
- **Garrison Armory.** That a `later` `BuildOps` at `CampaignDefenseResult`
  can resolve the Campaign setup, and that the fold order is: rescore the dice,
  then add the force, then add the force again. Nothing downstream may
  recompute the defense as dice plus force.
- **Rain Boots and Bag of Siegeworks.** That both rewrites compute from the
  faces, not the current score, so that they compose.
- **Insect Swarm.** That `Costs.onCard` with `favorBurnt` burns from the
  user's board, and that the off-turn settlement treats it as a burn.
- **Book Burning.** That `FlipSecrets` and `Burn.secrets` compose in one step.
- **Plan discards.** Whether a losing adviser's discard after the Campaign
  appears in the losses line, as for Horse Archers.
- **Disciples.** How to tell a world-deck Search at `SearchCost`. A regional
  discard Search always costs 2, so lowering a higher cost to 2 is exact
  today. Pin it with a test.
- **Initiation Rite.** That the Muster start line's cost span shows a secret,
  and that a holder with no faceup secret is offered no Muster source.
- **Saddle Makers.** Append a fixed number of nodes whatever the banks hold,
  as Gossip does, so a refold never shifts a parked sibling.
- **Crop Rotation with Mob.** Both set `siteDiscardNote`. Only one line is
  written when both apply.
- **Quartermaster.** That a Wake power is offered from a denizen at a ruled
  site the pawn is not at.
- **Same-bank gains.** A gain that follows a discard or kill returning to the
  same bank runs as its own step (Taming Charm, Second Chance on the player's
  own board).
- **Discard coverage.** Every file that builds a discard attaches
  `DiscardRestrictions` and filters its options with it, or
  `DiscardRestrictionsCoverageSuite` fails.
- **Generic lines.** Which generic lines exist for `Discard.Relic`,
  `Discard.RuinedEdifice`, `Bury`, a bandit `Kill`, a pawn `Move`, a `Give`
  to a bank or card, a bank-to-bank `Move` and `Gain.Warbands`, so each
  Covers column is exact. The batch-1 "No line" entry for A Small Favor may be
  stale.
- **Riots.** That the fold-time state holds the played card at its
  destination, and that the burn counts the cards actually discarded.
- **Great Herd.** A site-to-site `Swap` appends each card to the other site's
  list, so the order changes. Confirm the board renders that acceptably.
- **Reviewed catalog.** Retire each implemented card's reviewed stub, such as
  `MusterPowers.InitiationRite` and `RestPowers.Insomnia`.
- **Registration.** WHEN PLAYED cards flip their `ActionPowers.scala` entry
  and join `WalkerPowerCatalog.default`. ACTION cards join
  `SelfActionPowers`, `OtherActionPowers` or a new `WorldActionPowers` in
  `PhasePowerCatalog`.

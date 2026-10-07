# Catalog Batch 4: The Verified Cards

**Status:** designed 2026-10-05. The per-card rulings are in
[the rulings appendix](2026-10-05-catalog-batch-4-rulings.md).

**Builds on** [Powers batch 1](2026-09-20-powers-design.md),
[Catalog batch 2](2026-09-26-catalog-batch-2-design.md) and
[Catalog batch 3](2026-09-29-catalog-batch-3-design.md), whose vocabulary,
cost rules, walker shapes and log-line rules apply unchanged.

## Goal and scope

Batch 3's survey left 51 cards under **Catalog - to verify** in the roadmap,
each with one point to check. Each was checked against the code on
2026-10-05. 47 pass and are implemented here. Four move out (see the
rulings): Search Party and Hunting Party to a new **Phase - Follow-up
actions**, Magician's Code and Way Station to the engine-blocked phase.

| Slice | Count | Cards |
|---|---|---|
| 1a. Battle scoring | 9 | 57 Rusting Ray, 215 Fae Battalion, 187 War Tortoise, 154 Lancers, 253 Fearsome General, 60 Kindred Warriors, 45 Rangers, 163 Mounted Patrol, 8 Scouts |
| 1b. Losses and after the Campaign | 8 | 59 Billowing Fog, 51 Traveling Doctor, 108 Shield Wall, R02 Cursed Cauldron, 89 Salt the Earth, 72 Sealing Ward, 143 Herald, 230 Diplomat |
| 2. Restriction icons and card moves | 4 | 46 Roving Terror, 127 Homesteaders, 159 Resettle, 241 Moving Market |
| 3. ACTION powers | 8 | 61 Terror Spells, 77 Witch's Bargain, 38 Inquisitor, 95 Relic Thief, 23 Ancient Binding, 151 Convoys, R28 Singing Mask, 80 Assassin |
| 4. When played | 7 | 63 Revelation, 82 Blackmail, 84 Dissent, 208 Wizard's Conclave, 100 Bandit Chief, 129 A Round of Ale, 246 Call for Help |
| 5. Modifiers and restrictions | 11 | 193 Vow of Poverty, 196 Vow of Beastkin, 102 Defame, 248 Mounted Library, 119 Curfew, 150 Awaited Return, 158 Special Envoy, 111 Tyrant, 172 A Fast Steed, 112 Forced Labor, 134 News from Afar |

The catalog's reference JSON keeps only rules text, and four cards' text names
no action. The product owner read their action icons from the printed cards:
News from Afar is a Search modifier, Awaited Return a Trade modifier, A Fast
Steed a Travel modifier and Scouts an attacker's battle plan.

### The bar

Batch 3's bar holds: no new operation, walker window, decision query kind,
option kind, procedure step, `NoteArg` kind, protocol change or frontend
change, and refactors inside `gameplay/powers/` are allowed. The product
owner approved two changes outside `gameplay/powers/`:

- **The Rest cleanup** in `FinishRestProcedure.cleanup` is extracted beside
  `RestCleanupPlan` so that A Round of Ale can run it.
- **The restriction-icon rule** is a new `OperationRestriction` in
  `gameplay/operations/`, beside `LockedCards`.

### Out of scope

- **Phase - Follow-up actions**, a new roadmap phase: an action queued to run
  after the current one, as its own walker, with its costs waived and its own
  modifier selection. It takes Hunting Party and Search Party, migrates
  Knights Errant off its nested Campaign, and unblocks Second Wind.
- **Magician's Code** joins the engine-blocked Challenge group beside the
  Sigils, which need the same `legalBanners` hook.
- **Way Station** waits for Travel to choose its destination inside the
  walker (Phase - Board decision follow-ups).

## The restriction-icon rule

Restriction icons govern how a card is played, moved and swapped, not only
how it is played. Today `CardPlay` checks them on play only, and a power's
`Move` or `Swap` ignores them.

A new `OperationRestriction` joins `OperationRestrictions.printed`. It refuses
a `Move` or `Swap` that would put a faceup adviser-only card at a site, or a
site-only card into a player's advisers. Like `LockedCards`, it reads every
operation flattened once, and the search hides an option it refuses. A
facedown adviser has no restrictions, and edifices are never advisers.

The rule judges where and how a card lands: a move's `resultingOrientation`,
or else the orientation the card shows where it lies. It does not judge a
card leaving a hand, since `CardPlay` checks the icons for a play, nor a move
inside one play area, which only turns the card over.

Slice 2's audit found one implemented power the rule would have refused:
Horned Mask took a site card into the play area faceup and then flipped it
facedown, so a site-only denizen was refused between the two. It now lands
the card facedown in one `Move`, and its behavior is unchanged. Every other
implemented card move passes unchanged:

- Dream Thief, Enchantress and Twin Brother swap advisers between play areas.
- Great Herd swaps cards between sites. The rule also judges a move between
  two sites, so Great Herd's question no longer offers an adviser-only card
  that lies at a site: such a card has no legal landing at any site.
- The rest move relics or Visions, which print no restriction icons, or move
  cards to and from piles and hands.

## Powers-side refactors

Each is built in the slice that first needs it, without changing the behavior
or the tests of the powers it comes from.

| Id | Refactor | Users | Slice |
|---|---|---|---|
| R1 | An attack scorer: the attack total from the faces rolled, the committed force and the plans each side chose, in the order the rulings give. Outriders and The Great Levy write through it. | Rusting Ray, Fae Battalion, War Tortoise, Lancers, Fearsome General, Kindred Warriors, Rangers | 1a |
| R2 | A defense scorer generalising `SingleShields`: face filters, then doublers, then force. Rain Boots, Bag of Siegeworks and Garrison Armory write through it. | War Tortoise | 1a |
| R3 | Hospital's private `Tally` as a shared loss counter. Hospital moves from priority 1 to 2. | Billowing Fog, Traveling Doctor, Cursed Cauldron | 1b |
| R4 | Sticky Fire's private kill branches as a shared helper | Shield Wall | 1b |
| R5 | `PowerAnswers.last`, reading a `Repeat` loop's latest answer | Witch's Bargain, Call for Help | 3 |
| R6 | `SiteCards.discard` widened from `private[action]` | Mounted Library | 5 |

### Why the scorers are needed

Transforms fold in `(priority, power id, id)` order. Outriders, The Great Levy,
Rain Boots and Bag of Siegeworks each write a whole score, computed from the
faces. Two of them compose today only because each writes the same value. A
plan that changes the score relative to it would be overwritten by any of
them that sorts later: `denizen.lancers` and `denizen.fearsome-general` sort
before `denizen.outriders`. With the scorers, every plan that changes the
score writes the one total computed from every chosen plan, so the fold order
no longer matters.

### Fold order for losses

At `CampaignLosses`, Shield Wall and Sticky Fire stay at priority 0, Billowing
Fog and Traveling Doctor take priority 1, Hospital moves to 2 and Cursed
Cauldron takes 3. A defeated user of Fog or Doctor ignores Shield Wall and
Sticky Fire there, through the collector's named ignore, so neither asks nor
kills. Hospital saves from whatever kill is left, and Cursed Cauldron counts
after both, less what Hospital will place.

## Seams used for the first time

Each gets a test through its first power before the power relies on it. If a
seam needs an engine change, its card is dropped to the engine-blocked phase
with the reason.

- A `wrapping` Transform on the `CampaignAttackRoll` leaf (Mounted Patrol).
- A Transform at `CampaignGatherPools` (Sealing Ward).
- A `Move` of a card from a play area to a site (Homesteaders, Resettle) and
  from site to site (Roving Terror, Moving Market).
- A `ChooseOne` mixing `SiteSlot` options and a button (Moving Market). If
  the board panel needs a change to show it, stop and ask the product owner.
- A `Repeat` nested inside a `Repeat` (Witch's Bargain).
- An `AdviserSlot` option on a facedown Vision (Inquisitor) and a
  `RelicSlot` option on a facedown relic (Blackmail), projected to a viewer
  who cannot identify the card.
- A WHEN PLAYED power parking a decision owned by another player
  (Revelation, Blackmail). The engine supports it (League Treaty, the
  Campaign defender's plans); no WHEN PLAYED has done it yet.
- A `Bury` of a relic from a site (Salt the Earth).
- `EnterPhase(Rest)` inside Travel (Special Envoy), after Wizard School's and
  Murky Fountain's use in actions.
- An `OptionRestriction` at `MusterSourceSelection` (Vow of Beastkin).

## Implementation notes from the verification

- **Decisions after a Trade or Muster** append at the action's root
  (`TradeActionEligibility`), as Knights Errant does. A decision inside the
  Economy `Branch` fails on resume, because the source card holds tokens
  after the cost and `MusterSource.resolve` then refuses it. Defame and
  Moving Market follow this.
- **Moving one secret between boards** raises `AmbiguousSecretOrientation`
  when the giver holds faceup and facedown secrets. Witch's Bargain uses
  Sleight of Hand's flip.
- **`Distribute` refuses** a query with fewer than two variable slots or a
  forced total. Terror Spells kills without asking in those cases.
- **Inquisitor's guesses** are five `Button`s keyed by Vision id, since a
  card option the viewer cannot identify suppresses the decision. A wrong
  guess must be a legal empty result.
- **Convoys' piles** are buttons with their own labels. The existing
  `DiscardPile` option reads "Discard to the X discard".
- **Relic Thief checks Lost Tongue itself.** The search stops at a roll, so
  Lost Tongue's refusal of the `Take` would come after the dice and fail the
  command. `LostTongue.protects` leaves such a relic out of the question.
- **Ancient Binding leaves each player at most one secret** on their player
  UI (`PlayerSecretSummary`), which for the acting player also counts every
  secret on a card in play. So the actor burns their board and every secret
  on a card in play, except the one on Ancient Binding; every other player
  burns their board down to one. The Darkest Secret's secrets stay.
- **Inquisitor's right guess** reveals the discarded Vision in its pile. It
  stays facedown there, and every player knows it and can later browse it.
- **Questions another player owns.** Revelation's burns and Blackmail's
  holder answer belong to that player, and the walker refuses an answer
  from anyone else. The walker resumes at the parked node, expanding each
  `Branch` on the way with the live state, so Revelation gives every
  player the same place in its tree whether or not they are asked.
- **Blackmail's relics.** A faceup relic is offered as itself and a
  facedown one by its slot. A `RelicSlot` option always reads "{owner}
  facedown relic", so a faceup relic offered by slot would read wrongly.
- **Wizard's Conclave's take** is not required, so Lost Tongue's refusal
  skips it, and the line says the Darkest Secret did not move.
- **Call for Help's draws** are passes of a `Repeat`, each read with
  `PowerAnswers.amounts`. An amount of 0 ends the drawing.
- **Mounted Patrol halves at run time**, from the live pool, not at offer
  time: an offer's effects must not depend on what the plan changes.
- **Sealing Ward's dice** do not show in the Game Log's "Defense Pool" line,
  which reads the printed defense, so it writes its own line.
- **Special Envoy's `EnterPhase`** folds after every other Travel effect,
  above Royal Stables' priority, so Dragonskin Drum's gain comes first.
- **Vow of Poverty's rule** is a `Restriction` on the Trade's gain node,
  which refuses its holder's favor gain. The restriction look-ahead then
  hides every source of a Trade for favor, so that Trade is not offered.
  Moving Market's favor is gained at the Trade's root, outside the node.
- **Curfew and Forced Labor** charge through one helper, `RulerToll`, as Toll
  Roads charges for Travel: a required payment before the action's cost, so
  a source or a Search the enemy cannot pay for is hidden. Curfew reads the
  Trade's source when the payment runs, so the cost node's shape never
  depends on the answer.
- **Vow of Beastkin's extra warband** joins the Muster's own gain, and its
  line reads what the gain added beyond the base.
- **Mounted Library** folds at priority 1, so its discard ends the Trade
  after every other modifier's question. A Trade for favor with it leaves the
  paid secret on it, and the discard returns that secret to the trader
  facedown. The active-modifier rule refuses to discard a selected modifier,
  so a power that discards its own card declares `discardsItself`, and
  `WalkerPowers` leaves it out of that rule. This is the one engine-side
  change of the slice.
- **A Round of Ale** runs the extracted cleanup in one `BuildOps` with the
  actor as the resting player. It does not declare `RestReturnFavor`.
- **Test filler.** Tests that use Revelation as an unimplemented WHEN PLAYED
  card move to a Citizen-related one, Bewitch, since those are implemented
  last.
  `RestSuite` drops Vow of Poverty as its unimplemented Rest example.
  `CampaignFixture.raidBoard` replaces Cursed Cauldron, which becomes a plan.
  News from Afar is filler in seven suites and stays harmless while
  unselected; check each.
- **Catalog ids** carry two typos: `denizen.news-from-alfar` and
  `denizen.old-fast-steed`. Keep them; they are the catalog's ids.

## Slicing

Slices run in this order. Each plan is written when its slice starts.

| Slice | Cards | Refactors |
|---|---|---|
| 1a. Battle scoring | Rusting Ray, Fae Battalion, War Tortoise, Lancers, Fearsome General, Kindred Warriors, Rangers, Mounted Patrol, Scouts | R1, R2 |
| 1b. Losses and after the Campaign | Billowing Fog, Traveling Doctor, Shield Wall, Cursed Cauldron, Salt the Earth, Sealing Ward, Herald, Diplomat | R3, R4 |
| 2. Restriction icons and card moves | the restriction-icon rule and its audit; Roving Terror, Homesteaders, Resettle, Moving Market | |
| 3. ACTION powers | Terror Spells, Witch's Bargain, Inquisitor, Relic Thief, Ancient Binding, Convoys, Singing Mask, Assassin | R5 |
| 4. When played | Revelation, Blackmail, Dissent, Wizard's Conclave, Bandit Chief, A Round of Ale, Call for Help | the Rest cleanup extraction |
| 5. Modifiers and restrictions | Vow of Poverty, Vow of Beastkin, Defame, Mounted Library, Curfew, Awaited Return, Special Envoy, Tyrant, A Fast Steed, Forced Labor, News from Afar | R6 |

Slice 1a comes first because every rescoring plan needs the scorers. Slice 2
comes before slices 3 and 5 so that every later card move runs under the
restriction-icon rule.

## Log lines

Every power follows the
[Power log lines design](2026-09-26-power-log-lines-design.md). `{Blue}` and
`{Green}` are other players' chips. Amounts are what happened, never the
printed number. Only existing `NoteArg` kinds are used. "Covers" names the
generic lines a note replaces; "verify" means the plan confirms the generic
line exists.

**The acting player is not named** when the power runs on their own turn and
affects only them: the line follows the action's own line, so "Gained {n}
more secrets." reads better than naming them again. A line names a player
when that player is not the actor (`{Blue}`), or when the power runs off
turn, such as Herald or a Revelation choice.

### Slice 1: battle plans

| Card | Line | Covers |
|---|---|---|
| Rusting Ray | Rusting Ray: Hollow swords ignored. | |
| Fae Battalion | Fae Battalion: Hollow swords counted as swords. | |
| War Tortoise, used by the defender, a skull face was rolled | War Tortoise: Double swords added no attack. | |
| War Tortoise, used by the attacker, a double shield was rolled | War Tortoise: Double shields added no defense. | |
| Lancers | Lancers: The attack roll was doubled. | |
| Fearsome General, a skull face was rolled | Fearsome General: Skulls added {n} more swords. | |
| Kindred Warriors, attacker, a skull was rolled | Kindred Warriors: Skulls ignored. | |
| Rangers, a skull was rolled | Rangers: Skulls ignored. | |
| Mounted Patrol | Mounted Patrol: The attack pool was halved to {n} dice. | |
| Scouts | Scouts: Gained {n} Supply. | |
| Lancers, Mounted Patrol, Traveling Doctor, key `discarded` | {Card}: Discarded after the Campaign. | |
| Billowing Fog, Traveling Doctor, key `spared` | {Card}: {Blue}'s force was spared. | |
| Shield Wall, key `killed` | Shield Wall: {Blue}'s whole force was killed. | |
| Cursed Cauldron, at least one warband gained | Cursed Cauldron: {Blue} gained {n warbands}. | |
| Salt the Earth, key `buried` | Salt the Earth: Buried {cards} from {site}. | the Buried lines (verify) |
| Sealing Ward, at least one relic targeted | Sealing Ward: Added {n} more defense dice. | |
| Herald | Herald: {Blue} gained {1 favor} from {the Order bank}. | the Gain line |

A battle plan's line names its user as `{Blue}` where either side may use it,
since the defender acts off turn: Billowing Fog, Traveling Doctor, Shield
Wall and Cursed Cauldron. Herald's holder is never the attacker.

**No line:** Kindred Warriors' and Rangers' dice, whose effect is only a dice
change, as in batch 3. Shield Wall's dice. Diplomat, whose hidden options
write nothing (the roadmap's **Explain restricted options where they are
offered**).

### Slice 2: card moves

| Card | Line | Covers |
|---|---|---|
| Roving Terror | Roving Terror: Replaced {card} at {site}. | the Discarded line |
| Roving Terror, key `used.none` | Roving Terror: No card could be replaced. | |
| Homesteaders | Homesteaders: Moved {card} to {site}. | |
| Homesteaders, key `used.none` | Homesteaders: No adviser could be moved. | |
| Resettle | Resettle: Moved {Blue}'s {card} to {site}. | |
| Resettle, key `used.none` | Resettle: No Nomad adviser could be moved. | |
| Moving Market, key `moved` | Moving Market: Moved it to {site} and gained {n favor} from {the Nomad bank}. | |
| Moving Market, key `moved.only`, the Nomad bank was empty | Moving Market: Moved it to {site}. | |

**No line:** Moving Market skipped.

### Slice 3: ACTION powers

| Card | Line | Covers |
|---|---|---|
| Terror Spells | Terror Spells: Killed {n} warbands. | |
| Terror Spells, key `used.none` | Terror Spells: The {Darkest Secret} was not held. | |
| Terror Spells, key `used.empty` | Terror Spells: No warband was in the region. | |
| Witch's Bargain, key `secret`, one line per exchange | Witch's Bargain: Gave {1 secret} to {Blue} and took {n favor}. | |
| Witch's Bargain, key `favor`, one line per exchange | Witch's Bargain: Gave {2 favor} to {Blue} and took {n secrets}. | |
| Witch's Bargain, no exchange made | Witch's Bargain: No exchange was made. | |
| Inquisitor, key `used.<vision>.right` | Inquisitor: Guessed {name} and discarded {card} from {Blue}. | the Discarded and Revealed lines |
| Inquisitor, key `used.<vision>.wrong` | Inquisitor: Guessed {name} for {Blue}'s Vision, wrongly. | |
| Inquisitor, nothing to guess | Inquisitor: No player at the site held a facedown Vision. | |
| Relic Thief | Relic Thief: Rolled no shield and took {relic} from {Blue}. | |
| Relic Thief, key `used.kept` | Relic Thief: Rolled a shield; {Blue} kept {relic}. | |
| Relic Thief, key `used.none` | Relic Thief: No faceup relic could be taken. | |
| Ancient Binding | Ancient Binding: Burned {n secrets}. | |
| Ancient Binding, key `used.none` | Ancient Binding: No secret was burned. | |
| Convoys | Convoys: Moved the {pile} onto the {pile}. | |
| Singing Mask | Singing Mask: Moved {n favor} from {the Beast bank} to the {People's Favor}. | |
| Singing Mask, key `used.returned` | Singing Mask: Moved {n favor} from the {People's Favor} to {the Beast bank}. | |
| Singing Mask, key `used.none` | Singing Mask: No favor moved. | |
| Assassin | Assassin: Discarded {card} from {Blue}'s advisers. | the Discarded line |
| Assassin, key `used.none` | Assassin: No faceup adviser could be discarded. | |

Inquisitor's guessed name is literal text, one note key per Vision, because a
`NoteArg.Card` is redacted per viewer. Its `{card}` on a right guess is the
discarded Vision, which every viewer then identifies: a `Reveal` in the pile
records it as known to every player, and it stays facedown there. Where the
actor targets
their own cards (Inquisitor, Resettle, Assassin), `{Blue}` is the actor's
chip. Every decision whose choice a note names is declared narrated, as Fae
Merchant's is.

A phase power declares a key named `used` (`PowerNoteCatalogSuite`), so a
row with no key above is key `used`: Witch's Bargain's and Inquisitor's
lines for nothing done, and Relic Thief's take. No generic line exists for a
kill, a relic's take, a secret's burn, or favor, secrets and cards moving
between boards, banks, banners and piles, so those rows cover nothing.

**No line:** Assassin's adviser limit, as for Silver Tongue.

### Slice 4: when played

| Card | Line | Covers |
|---|---|---|
| Revelation, key `burned`, one line per player who burned | Revelation: {Blue} burned {n favor} and gained {n secrets}. | the Gain line |
| Revelation, key `declined`, one line per player who was asked and burned none | Revelation: {Blue} did not burn any favor. | |
| Revelation, key `none` | Revelation: Nobody had favor to burn. | |
| Blackmail, key `paid` | Blackmail: {Blue} gave {3 favor} to keep {relic}. | |
| Blackmail, key `took` | Blackmail: Took {relic} from {Blue}. | |
| Blackmail, key `none` | Blackmail: No relic could be taken. | |
| Dissent, key `placed`, one line per player who placed | Dissent: {Blue} placed {n favor} on it. | |
| Dissent, key `none` | Dissent: Nobody placed favor. | |
| Wizard's Conclave, key `took` | Wizard's Conclave: {Blue} took the {Darkest Secret}. | |
| Wizard's Conclave, key `kept` | Wizard's Conclave: {Blue} kept the {Darkest Secret}. | |
| Wizard's Conclave, key `none` | Wizard's Conclave: The {Darkest Secret} did not move. | |
| Bandit Chief, key `killed`, one line per site | Bandit Chief: Killed {n} {Blue} warband at {site}. | |
| Bandit Chief, key `none` | Bandit Chief: No warband was killed. | |
| A Round of Ale, key `cleanup` | A Round of Ale: Triggering resource cleanup. | |
| Call for Help, key `revealed`, one line per draw that gained warbands | Call for Help: Revealed and discarded {cards}, and gained {n warbands}. | that draw's Revealed lines |
| Call for Help, key `discarded`, one line per draw that gained none | Call for Help: Revealed and discarded {cards}. | that draw's Revealed lines |
| Call for Help, key `none` | Call for Help: Nothing was revealed. | |

Revelation, Dissent and Wizard's Conclave name every player, the actor
included, because the line is about several players. A Round of Ale's line
comes before the cleanup, which writes no generic line. Bandit Chief names
whose warband died, the actor's included.

No generic line exists for a favor burn, favor moving between boards or
onto a card, a relic's or a banner's take, a kill, or a card moving between
discard piles, so those rows cover nothing. Call for Help reveals each card
in its new pile, which writes one Revealed line per card; a draw's line
covers them. A draw that gains no warband uses its own key rather than
saying it gained none.

**No line:** Wizard's Conclave's tie choice, which the `took` line names.

### Slice 5: modifiers and restrictions

| Card | Line | Covers |
|---|---|---|
| Vow of Poverty (REST), key `used` | Vow of Poverty: Took {n favor} from {the Order bank}. | the Gain line |
| Vow of Beastkin, key `gained`, after the gain | Vow of Beastkin: Gained {1} more warband. | |
| Defame, key `burned` | Defame: Burned {1 favor} from {Blue}'s board. | |
| Mounted Library, key `doubled` | Mounted Library: Gained {n} more secrets. | the second gain's Gain line; the Trade's own stays |
| Mounted Library, key `discarded` | Mounted Library: Discarded after the Trade. | the Discarded line |
| Curfew, Forced Labor, key `gave` | {Card}: Gave {1 favor} to {Blue}. | |
| Curfew, Forced Labor, key `burned`, bandits rule | {Card}: Burned {1 favor}. | |
| Awaited Return, key `sacrificed` | Awaited Return: Spent no Supply after sacrificing {1} warband. | |
| Special Envoy, key `ended` | Special Envoy: The Act Phase ended. | |
| Tyrant, key `killed` | Tyrant: Killed {1} {Blue} warband at {site}. | |
| Tyrant, key `killed.bandits` | Tyrant: Killed {1} bandit warband at {site}. | |

Defame names the chosen player even when the actor chose themself, and
Tyrant names whose warband died, the holder's own included, as Bandit Chief
does. No generic line exists for a favor burn, favor given between boards, a
kill, a warband gain or a phase change, so those rows cover nothing. Mounted
Library doubles through a second secret gain. Its line covers only that
gain's generic line, so the Trade's own "Gained {n} secrets" line stays and
"Mounted Library: Gained {n} more secrets." follows it. Tyrant's bandit kill
has its own key, since no note argument names the bandits.

**No line:** News from Afar, A Fast Steed and Special Envoy's Supply waiver,
which the start line's cost span shows. Vow of Poverty's and Vow of
Beastkin's hidden options, Curfew's and Forced Labor's hidden sources, as
restrictions never offered.

## Testing

- Every card gets a walker-driven suite that starts the action or reaches the
  plan step, answers its decisions, and asserts the resulting state, the
  recorded steps and its exact log line. Each suite covers its rulings' edge
  cases: a cost that cannot be paid, an empty target, an empty bank, pile or
  supply, a bandit enemy, and off-turn decisions.
- The attack scorer gets a table-driven suite over the rulings' steps,
  including the worked example (force 3, Lancers and Fearsome General, 2
  skulls and 1 sword, total 14 and 3 deaths), unpayable and ignored skulls
  with Fearsome General, and War Tortoise with Fearsome General. The defense
  scorer's suite covers War Tortoise with Rain Boots and a doubler.
- The suites of Outriders, The Great Levy, Rain Boots, Bag of Siegeworks,
  Garrison Armory, Hospital and Sticky Fire pass unchanged after R1 to R4.
- Every seam in **Seams used for the first time** gets a test through its
  first power.
- The restriction-icon rule gets a suite of its own: an adviser-only card
  refused to a site, a site-only card refused to advisers, a facedown adviser
  allowed, and Bury unaffected. Each implemented power whose behavior the
  audit changes gets a test of the new behavior.
- Frontend suites cover Moving Market's slots-plus-Skip question, if the
  existing panels need no change.
- `PowerImplementationStatusSuite` and `PowerKindsCatalogSuite` pin the new
  cards. `BackendArchitectureSuite` still applies: files stay under 800 lines,
  no power names in walker sources, and powers do not import
  `gameplay.walker`.

## Verify at plan time

- **Moving Market.** That the board panel shows a `ChooseOne` of `SiteSlot`
  options with a Skip button. If not, stop and ask the product owner.
- **Inquisitor and Blackmail.** That an `AdviserSlot` or `RelicSlot` option on
  a facedown card projects to a viewer who cannot identify the card.
- **Fold order.** That R1 makes every rescoring plan write the same total
  whatever order they fold in, with a test that permutes their power ids.
- **Hospital at priority 2.** That nothing else at `CampaignLosses` relies on
  priority 1.
- **Herald.** That its branch at `CampaignActionEligibility` runs after
  `RecordCampaignResult` and reads that Campaign's result.
- **Salt the Earth.** That `Bury` moves a relic from a site, facedown ones
  included.
- **Defame and Moving Market.** That a decision inside the Economy `Branch`
  does fail on resume, which is why they append at the root.
- **Call for Help.** That each card's move to the next region's pile happens
  one at a time, so the first card drawn ends deepest.
- **Generic lines.** Which generic lines exist for each "verify" above, so
  each Covers column is exact.
- **Registration.** WHEN PLAYED cards flip their `ActionPowers.scala` entry
  and join `WhenPlayedPowers.forCatalog`. ACTION cards join
  `SelfActionPowers`, `OtherActionPowers` or `WorldActionPowers`. Retire each
  implemented card's reviewed stub, such as those in `MusterPowers.scala` and
  `RestPowers.scala`, and move each card out of `catalog/holding/`.

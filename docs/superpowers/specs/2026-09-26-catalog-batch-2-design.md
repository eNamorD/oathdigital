# Catalog Batch 2: Cards, Engine Additions and Slicing

**Status:** designed 2026-09-26. Implementation starts after the
[Power log lines](2026-09-26-power-log-lines-design.md) phase merges its
`Note` mechanism, so every card in this batch writes its log line in the task
that implements it. The per-card rulings are in
[the rulings appendix](2026-09-26-catalog-batch-2-rulings.md).

**Builds on** [Powers batch 1](2026-09-20-powers-design.md), whose
vocabulary, cost rules and walker shapes apply unchanged, and the
[rule gaps phase](2026-09-26-rule-gaps-in-implemented-cards-design.md), whose
restriction look-ahead hides options a Restriction forbids.

## Goal and scope

Complete the all-Exile world deck. The first-game generator
(`FirstGameChronicleGenerator`) builds a 60-card world deck of 10 denizens per
suit, implemented cards first. Before this batch 37 of 255 denizens are
implemented, spread unevenly: arcane 5, beast 5, discord 6, hearth 8, nomad 5,
order 8. A game draws unimplemented, inert cards once the implemented ones
run out. This batch implements exactly enough denizens to reach 10 in every
suit, so every card in a generated world deck works.

It also implements 8 relics. A game deals 8 of 24 sites, which print 21 relic
slots between them, so about 7 relics start in play, and Forge and the relic
powers draw a few more. The relic deck is ordered implemented-first too, and
16 relics are already implemented, so games rarely see an inert relic today.
The relics here add variety between games.

In scope, 31 powers:

| Suit | Needed | Cards |
|---|---|---|
| Arcane | 5 | 69 Tutor, 33 Spirit Snare, 34 Wizard School, 31 Fire Talkers, 75 Forgotten Vault |
| Beast | 5 | 40 Animal Playmates, 176 Birdsong, 175 Nature Worship, 214 Shifting Fog, 216 Hunger |
| Discord | 4 | 19 Scryer, 83 Cracked Sage, 228 Spoiled Supplies, 101 Chaos Cult |
| Hearth | 2 | 131 Charming Friend, 149 Hospital |
| Nomad | 5 | 24 Horse Archers, 167 Storm Caller, 245 Royal Stables, 170 Twin Brother, 160 Oracle |
| Order | 2 | 4 Longbows, 116 Siege Engines |
| Relics | 8 | R17 Shifting Map, R46 Demon Tail, R35 Black Sword, R14 Oracular Pig, R37 Bag of Siegeworks, R36 Barbed Net, R19 Book of Records, R47 Clay Rattle |

After the batch, 60 of 255 denizens (10 per suit) and 24 of 48 relics are
implemented.

### How the cards were chosen

Half the cards are the cheapest in their suit: short, unambiguous text that
copies an existing power's shape. The other half cover a kind of power the
engine has no example of yet, so later batches can copy it. The coverage
cards are Shifting Fog, Hunger, Chaos Cult, Hospital, Twin Brother, Oracle,
Siege Engines, Bag of Siegeworks, Barbed Net, Book of Records and Clay
Rattle.

### Out of scope

A survey of the 250 unimplemented denizens and relics found 13 blocked on
systems that do not exist and 6 that need an engine addition not taken here.
None of them is in this batch:

- **Empire rules:** Naysayers, Council Seat, Bewitch, Long-Lost Heir, Royal
  Ambitions, Ancient Pact, Martial Culture, Grand Mask, Imperial Seal and the
  Grand Scepter.
- **Nested Negotiation:** The Gathering, Whispering Stone.
- **Off-turn nested actions:** Sneak Attack.
- **Legacies:** Ancient Writ draws legacy cards.
- **Negotiation participants:** Council Arbiter, Deed Writer, Traveling
  Negotiator.
- **Plan restrictions:** Peace Envoy, Marsh Spirit, True Names and Code of
  Honor restrict which battle plans a side may choose.
- **Muster exceptions:** Golem Legions.
- **A non-Empire Reliquary:** Reliquary Raid.

Tracker and Special Envoy end the Act phase, which Wizard School's shape
supports, and are left for a later batch only to keep this one small.

## Engine additions

Each addition is built in the slice that first needs it.

### N1. Supply reduction (slice 1)

Royal Stables lowers the amount of the Travel's `SpendSupply` by 1, never
below 1, when the Travel has one. Tents removes the `SpendSupply` entirely, so
with both selected Tents wins and the Travel costs nothing. The two
Transforms commute, so their order does not matter.

### N2. Ignored defense faces (slice 2)

Bag of Siegeworks makes each single-shield defense die score 0. Two shields
and doublers score as usual, so a roll of Blank, OneShield and Doubler scores
0. The change applies where the defense roll is scored, through the existing
`ModifyRollOutcome` if it can express it, otherwise through a new roll-scoring
contribution. The reviewed-catalog stub `CampaignPowers.BagOfSiegeworks`
retires.

### N3. Campaign kill replacement (slice 2)

Hospital is a battle plan. Once chosen, each `Kill` of its user's warbands
during that Campaign becomes a move to Hospital's site, while the user still
rules that site when the kill happens. It is a later hook at the Campaign's
loss windows, like Sticky Fire's. Kills at Hospital's own site, when that
site is a Conquest target the attacker won, stay kills.

### N4. Shuffle (slice 3)

A new `Shuffle` operation reorders the world deck or one region's discard
pile. The server generates the new order and the journal records it, as dice
faces are generated and recorded, so replay reproduces it. Setup shuffles are
unchanged: the Chronicle generator shuffles once when a game is created, and
its result is the stored starting state.

### N5. The card-list view (slice 3)

Scryer can look at a whole discard pile, which may hold dozens of cards, and
later powers look at the Dispossessed. One reusable view serves them:

- **`DecisionQuery.Inspect(cards)`**, a decision with no choice and a Done
  button, owned by the player who looked. The `Peek`s are recorded before it,
  so the projector lets that player identify the cards.
- **A card-list overlay** in the frontend: a scrolling grid in pile order that
  holds dozens of cards. The `Inspect` decision shows it, and so does the log.
  Later choices among many cards (Search Party, Pilgrimage, the Dispossessed)
  reuse it as a pick list.
- **`NoteArg.Cards(ids)`**, a list of cards in a log line. Up to 5 cards render
  inline as card chips. More than 5 render as "6 cards", which opens the
  overlay, so a Scryer line can read "Scryer: Red peeked at the Cradle
  discard pile: 6 cards." Each card in the overlay passes the log's knowledge
  rule, showing its face to a viewer who may identify it and its back
  otherwise. The text is not a link for a viewer who may identify none of
  them.
- **`NoteArg.Pile(pile)`**, which renders "world deck" or "Cradle discard
  pile". The template supplies the article.
- **UI work.** Design and build the overlay, and any other UI element this
  batch needs, with the `/impeccable` skill.

### N6. Drawing a Vision (slice 3)

Oracle finds the first Vision from the top of the world deck. The cards above
it stay where they are, unseen. The Vision is then played or discarded
through Search's placement, as if it had been searched: faceup (subject to
the Vision restrictions of the rule gaps phase), facedown as an adviser, or
discarded. The draw advances the Visions Drawn track, as a Vision drawn in a
world-deck Search does.

### N7. A title-change window (slice 5)

`OathkeeperProcedure` gains a window after its `SetOathkeeper`, so Chaos Cult
can act when the title changes hands. Its comment already expected one "until
a real power needs to".

### N8. Forced Wake steps (slice 5)

Hunger's WAKE is mandatory. The start of Wake gains a step that runs every
forced Wake power its holder has, before the holder may use optional Wake
powers. Each forced power asks its own decision.

### Reused as they are

Barbed Net reuses Recover's peek-then-take. Book of Records takes from a
banner with `Take`. Twin Brother uses `Swap`. Siege Engines uses `Kill`, and
the existing bandit refill fills a site it empties. Wizard School ends the Act
phase with `EnterPhase(Rest)`, as Murky Fountain does.

## Slicing

Slices are grouped by the kind of power, so each plan and each reviewer
checks one shape. They run in this order. Each gets its own plan, written
when it starts.

| Slice | Cards | Additions |
|---|---|---|
| 1. Modifiers and restrictions | Animal Playmates, Birdsong, Royal Stables, Forgotten Vault | N1 |
| 2. Battle plans | Fire Talkers, Nature Worship, Cracked Sage, Horse Archers, Storm Caller, Longbows, Black Sword, Bag of Siegeworks, Hospital | N2, N3 |
| 3. Actions on yourself | Tutor, Spirit Snare, Wizard School, Scryer, Oracle, Shifting Map, Demon Tail, Oracular Pig, Clay Rattle | N4, N5, N6 |
| 4. Actions on others | Spoiled Supplies, Charming Friend, Siege Engines, Barbed Net, Book of Records | none |
| 5. Triggers and when played | Shifting Fog, Hunger, Twin Brother, Chaos Cult | N7, N8 |

Slices 1 and 2 come first because most of their cards copy existing powers.
Slices 3 and 4 follow. Slice 5 comes last, so a trigger that proves harder
than expected does not hold up the rest. Hospital's kill replacement (N3) may
move to its own sub-slice of slice 2 if the Campaign's loss paths make it
large.

## Log lines

Every power follows the Power log lines design: its wording rules, its
covering, and its "No line" categories. `{Red}` is the acting player's chip
and `{Blue}` another player's. Amounts are what happened, never the printed
number.

### Phase powers

Each note is the power's `used` line unless marked otherwise.

| Card | Line | Covers |
|---|---|---|
| Tutor | Tutor: {Red} gained {1} secret. | the Gain line |
| Spirit Snare | Spirit Snare: {Red} took {n} favor from {the Order bank}. | the Gain line |
| Spirit Snare, every bank empty | Spirit Snare: Every favor bank was empty. | |
| Wizard School | Wizard School: {Red} gained {1} secret. | the Gain line |
| Wizard School, key `ended` | Wizard School: {Red}'s Act phase ended. | |
| Scryer | Scryer: {Red} peeked at the {Cradle discard pile}: {cards}. | the Peeked lines |
| Scryer, empty pile | Scryer: {Red} peeked at the {Cradle discard pile}, which was empty. | |
| Oracular Pig | Oracular Pig: {Red} peeked at the top of the world deck: {cards}. | the Peeked lines |
| Oracle | Oracle: {Red} drew {Vision} from the world deck. | |
| Oracle, no Vision | Oracle: The world deck held no Vision. | |
| Shifting Map | Shifting Map: {Red} gained {n} Supply. | |
| Demon Tail | Demon Tail: {Red} gained {n} Supply. | |
| Clay Rattle | Clay Rattle: {Red} shuffled the {Cradle discard pile}. | |
| Spoiled Supplies, one line per player who lost Supply | Spoiled Supplies: {Blue} lost {n} Supply. | |
| Spoiled Supplies, nobody lost any | Spoiled Supplies: No enemy lost Supply. | |
| Charming Friend | Charming Friend: {Red} took {1} favor from {Blue}. | |
| Charming Friend, no other player at the site | Charming Friend: No player could be robbed. | |
| Charming Friend, the chosen player had no favor | Charming Friend: {Blue} had no favor to take. | |
| Siege Engines | Siege Engines: Killed {n} {Blue} warband at {site}. | |
| Siege Engines, key `bandits` | Siege Engines: Killed {n} bandit warband at {site}. | |
| Siege Engines, no warband | Siege Engines: {site} had no warband to kill. | |
| Barbed Net | Barbed Net: {Red} took {relic} facedown from {site}. | the Peeked lines |
| Barbed Net, no relic | Barbed Net: {site} held no relic. | |
| Book of Records | Book of Records: {Red} took {n} favor from {Blue}'s {banner}. / Book of Records: {Red} took {n} secret from {Blue}'s {banner}. | |
| Book of Records, empty banner | Book of Records: {Blue}'s {banner} held nothing to take. | |

Scryer's and Oracular Pig's `{cards}` is `NoteArg.Cards`: up to 5 names
inline, or "6 cards" opening the overlay. Oracular Pig and Oracle always use
the world deck, so their lines name it as text. Scryer and Clay Rattle name
their pile with `NoteArg.Pile`. Oracle's Vision is a card argument, so a
viewer who may not identify it reads its back. The Search placement lines
that follow Oracle stay.

### Removed and hidden options

| Card | Path | Line |
|---|---|---|
| Forgotten Vault | hide hook | Forgotten Vault: {Blue}'s relics cannot be targeted. |

### Added effects

| Card | Line | Covers |
|---|---|---|
| Shifting Fog | Shifting Fog: Every bank's favor moved to the next bank. | |
| Hunger | Hunger: {Red} buried {Blue}'s {card}. | the Buried line |
| Hunger, no adviser to bury | Hunger: No adviser could be buried. | |
| Twin Brother | Twin Brother: {Red} swapped it for {Blue}'s {card}. | |
| Chaos Cult | Chaos Cult: {Red} took {1} favor from {Blue}. | |
| Hospital | Hospital: Placed {n} {Red} warband at {site} instead. | the Killed line |
| Horse Archers | Horse Archers: Discarded after the Campaign. | |
| Storm Caller | Storm Caller: Discarded after the Campaign. | |

### Altered procedures

| Card | Line |
|---|---|
| Bag of Siegeworks | Bag of Siegeworks: Single shields ignored. |

### No line

- Animal Playmates, Birdsong and Royal Stables: Supply waivers and cost
  changes, which the start line's cost span shows.
- Fire Talkers, Nature Worship, Cracked Sage, Longbows and Black Sword: plans
  whose effect is only a dice change. Horse Archers' and Storm Caller's dice
  change has no line either; only their discard does.
- Twin Brother declined: nothing happened.

## Testing

- Every card gets a walker-driven suite that starts the action, answers its
  decisions and asserts the resulting state, the recorded steps and its exact
  log line. Each suite covers its rulings' edge cases: a cost that cannot be
  paid, an empty target, an empty deck, pile or bank, and off-turn payment for
  a defender's plan.
- Replay tests cover `Shuffle`, `Inspect` and the forced Wake step.
- Codec tests cover the new operation, decision and note argument kinds.
- Frontend tests cover the overlay, built with the `/impeccable` skill:
  inline chips up to 5 cards, the "N cards" link above 5, card backs for a
  viewer who may not identify a card, and the plain text when they may
  identify none.
- `PowerImplementationStatusSuite` and `PowerKindsCatalogSuite` pin the new
  cards.
- `BackendArchitectureSuite` still applies: files stay under 800 lines, no
  power names in walker sources, and powers do not import `gameplay.walker`.

## Verify at plan time

- Whether `ModifyRollOutcome` can ignore single shields, or N2 needs a new
  contribution.
- Every path by which a Campaign kills warbands (skull losses, defeat losses,
  Sticky Fire), for N3.
- Whether the Power log lines phase added a banner note argument for
  Conspiracy. If not, Book of Records adds `NoteArg.Banner`.
- Where Wake begins, and whether a forced step fits before the optional Wake
  powers without a new phase state (N8).
- How Search's placement path can be entered without a Search, for Oracle (N6).
- That `Swap` accepts two advisers in different players' play areas and
  carries their resources, for Twin Brother.

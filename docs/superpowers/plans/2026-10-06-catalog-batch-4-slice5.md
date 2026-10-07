# Catalog Batch 4, Slice 5 (Modifiers and Restrictions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement eleven modifiers and rules: Vow of Poverty (193), Vow of Beastkin (196), Defame (102), Mounted Library (248), Curfew (119), Awaited Return (150), Special Envoy (158), Tyrant (111), A Fast Steed (172), Forced Labor (112) and News from Afar (134). This is the last slice of Catalog batch 4.

**Architecture:**

- **Selected modifiers** are `SelectedModifier`s:
  - Trade: Defame, Mounted Library and Awaited Return.
  - Travel: Special Envoy and A Fast Steed.
  - Search: News from Afar.
- **Automatic rules** are `ContributingPower`s:
  - Curfew and Forced Labor, persistent site rules that charge a toll.
  - Vow of Beastkin and Tyrant, rules of a locked adviser.
- **Vow of Poverty** is both a `PhasePower` (its REST) and a `ContributingPower` (its Trade rule), as Vow of Obedience is.
- **Registration.**
  - Each card's object moves out of `catalog/holding/` into its power's file.
  - Each power registers in `ActionModifiers` (Search, Trade, Muster) or `TravelModifiers` (Travel); Vow of Poverty also registers in `PhasePowerCatalog`.
  - Each reviewed stub retires from `MusterPowers` or `RestPowers`.
- **Powers-side additions:**
  - `RulerToll` holds the toll Curfew and Forced Labor share.
  - `SiteCards` widens from `private[action]` to `private[powers]` (refactor R6), so Mounted Library can discard itself the standard way.
- **No engine change** beyond one hook. No new operation, window, query, option kind, `NoteArg` kind, protocol change or frontend change. Execution found that the active-modifier rule refused Mounted Library's own discard, so `ContributingPower` gained `discardsItself` and `WalkerPowers` leaves such a power out of that rule (commit d3594fd3).

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`: the sections "The bar", "Powers-side refactors" (R6), "Seams used for the first time", "Implementation notes from the verification", "Log lines", "Slice 5: modifiers and restrictions", "Testing" and "Verify at plan time".
- `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md`: "Rules that apply to the whole batch" and the per-card rulings under "Slice 5: modifiers and restrictions".

The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- **Slice order.** Slices 1a to 4 are on `main` (slice 4 merged as c67baf5c). Check with `git log main --oneline | grep "record catalog batch 4 slice 4"`. This slice needs none of slice 4's code. Task 8 edits the roadmap status line that slice 4 left.
- **Build rules.** `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails the build, in tests too.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` (such as `val Vector(a, b) = ...`) also fails, so bind each value separately. A tuple pattern over a tuple type is irrefutable and is fine.
  - `BuildOps`' second positional parameter is `window`, so always pass `required = true` by name.
- **File size.** Production and test Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Importing `gameplay.actions` and `gameplay.phases` is allowed (Moving Market imports `TradeProcedure`).
  - No power name appears in walker sources.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Note templates.** A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite`). A phase power declares a key named `used`; Vow of Poverty's REST line is that key. No other power here is a phase power.
- **Log lines, exactly** (spec, "Log lines", "Slice 5", with plan-time rulings 1 and 2). These are all the actor's own turn, so the actor is not named, except where a line is about a chosen player (Defame) or whose warband died (Tyrant, as Bandit Chief).

  | Card | Key | Line | When |
  |---|---|---|---|
  | Vow of Poverty | `used` | Vow of Poverty: Took {n favor} from {the Order bank}. | its REST took favor; covers the Gain line |
  | Vow of Beastkin | `gained` | Vow of Beastkin: Gained {n} more warband. | a Muster's gain added more than its base |
  | Defame | `burned` | Defame: Burned {1 favor} from {Blue}'s board. | a player was chosen, the actor included |
  | Mounted Library | `doubled` | Mounted Library: Gained {n} more secrets. | covers the second gain's Gain line only; the Trade's own Gained line stays |
  | Mounted Library | `discarded` | Mounted Library: Discarded after the Trade. | covers the Discarded line |
  | Curfew, Forced Labor | `gave` | {Card}: Gave {1 favor} to {Blue}. | a player rules |
  | Curfew, Forced Labor | `burned` | {Card}: Burned {1 favor}. | bandits rule |
  | Awaited Return | `sacrificed` | Awaited Return: Spent no Supply after sacrificing {n} warband. | its kill ran |
  | Special Envoy | `ended` | Special Envoy: The Act Phase ended. | always, after the Travel |
  | Tyrant | `killed` | Tyrant: Killed {n} {Blue} warband at {site}. | a player's warband died, the holder's own included |
  | Tyrant | `killed.bandits` | Tyrant: Killed {n} bandit warband at {site}. | a bandit warband died |

  Defame's decision is declared narrated, so it writes no generic "Chose" line. No other power here asks anything, except Vow of Poverty's REST bank choice, which keeps its generic line as Vow of Obedience's does.
- **Batch rulings that every task applies** (rulings, "Rules that apply to the whole batch"): "Your site" means the pawn's site. Site rulers are players or bandits under the all-Exile rule; Empire rulers are not supported.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare against them at the end. This plan adds 40 server tests and no frontend test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one says what it costs if it is wrong.

1. **Generic lines** (the spec's "verify" items, checked in `DetailLines.delta`).
   - A favor `Move` from a bank to a board writes "Gained {n} favor", so Vow of Poverty's REST line covers it, as Vow of Obedience's does.
   - A `Gain.Secrets` writes "Gained {n} secrets". Mounted Library doubles through a second gain, which would write a second "Gained {n} secrets" line. Its `doubled` line covers only that second step, so the log reads "Gained 1 secret." from the Trade, then "Mounted Library: Gained 1 more secret.". The spec's table listed no cover; Task 8 corrects it.
   - A `Discard.Denizen` writes a Discarded line, so Mounted Library's `discarded` covers it.
   - A favor `Burn`, a `Give` between boards, a burn paid as `PayCost`, a `Kill`, a `Gain.Warbands` and `EnterPhase` write no line. So Defame, Curfew, Forced Labor, Awaited Return, Tyrant, Vow of Beastkin and Special Envoy cover nothing. Task 8 corrects the spec's "verify" rows.
   - Cost if wrong: a generic line shows twice.
2. **Tyrant's bandit kill.** "Whoever's" includes bandits, and no `NoteArg` names bandits. So a bandit kill uses a second key, `killed.bandits`. Cost if wrong: renaming a key later orphans the notes of saved games, so decide before the slice merges.
3. **Vow of Poverty's Trade rule** is a `Restriction` at `TradeGain`. It refuses a gain node that holds a favor gain for its holder.
   - The node exists only once the source is answered. The restriction look-ahead probes each source with its answer, so every source of a Trade for favor is hidden. `TradeProcedure.startOptions` then has no answerable source, and `LegalActionProjector` does not offer the Trade.
   - A Trade for secrets gains secrets in that node, and Moving Market's favor is gained at the Trade's root, so both are unaffected.
   - The violation is `NoPlayableOption("trade")`. The hidden sources write no line (spec, "No line").
4. **Vow of Beastkin.**
   - Its source rule is an `OptionRestriction` at `MusterSourceSelection`, the spec's seam. It refuses a card whose suit matches none of the holder's faceup advisers (`MusterSource.matching`). Vow of Beastkin is itself a faceup Beast adviser, so a Beast card always qualifies.
   - Its extra warband joins the Muster's own gain: the base `Gain.Warbands` becomes one more, followed at once by the note. The note reads what the gain added beyond the base, so an empty warband bank writes nothing.
   - Rowdy Pub's, Village Idiot's and Downtrodden's appended gains are untouched.
5. **Curfew and Forced Labor** charge through `RulerToll`, as Toll Roads charges for Travel.
   - The payment is required and sits before the action's own cost. An enemy who cannot pay is refused, the look-ahead hides that Trade source, and `SearchProcedure.legalSources` drops that Search.
   - Curfew reads the Trade's source when the payment runs, in a `BuildOps`, so the cost node's shape never depends on the answer (as The Old Oak). "A card its ruler rules" is `RuledCards`, which counts the ruler's faceup advisers and the cards at the sites it rules.
   - Forced Labor reads the pawn's site when the cost node folds.
   - Toll Roads stays as it is: its lines name the traveller, and its suite must pass unchanged.
6. **Defame** appends one `Branch` at the Trade's root, as Moving Market does.
   - It asks only after a Trade with a Discord source, a card or an edifice by its suit.
   - The options are the players with favor, in seat order, then Skip when some player has none. With nobody holding favor, nothing is asked.
   - The burn is a `Burn` of 1 favor from the chosen board. Skip writes nothing.
7. **Mounted Library.**
   - It doubles by a second `Gain.Secrets` after the Trade's own, added for every Trade and run only when it was the source (The Old Oak's shape).
   - It folds at priority 1, so its discard comes last at the Trade's root, after Defame's and Moving Market's questions.
   - The discard is `SiteCards.discard` from wherever it stands, whatever the Trade was. Its favor returns to the Nomad bank, and its secrets go to the trader facedown. So a Trade for favor with it returns the paid secret.
8. **Awaited Return** rewrites the Trade's cost node.
   - It removes the actor's `SpendSupply` and adds a required batch that kills one warband from the actor's board.
   - It is selectable only with a warband on the board.
   - It folds before the Cup of Plenty (`denizen.` sorts before `relic.`), so the Cup finds no payment left to waive.
9. **Special Envoy** folds at `RoyalStables.Priority + 1`.
   - It removes the Supply payment after Royal Stables lowers it.
   - It appends `EnterPhase(Rest)` and its line after every other Travel effect, Dragonskin Drum's gain and Tyrant's kill included.
   - Task 5 tests the seam, `EnterPhase` inside Travel.
10. **A Fast Steed** reads the board's warbands when `TravelCost` folds, after the root has placed its favor. That is "when the cost is paid", since nothing between them changes warbands.
11. **News from Afar** rewrites the Search's cost `BuildOps` to drop the actor's `SpendSupply`, as Disciples caps it. Forced Labor's toll is a separate operation and is unaffected.
12. **Tyrant** is a `case object`. It needs no catalog, and it keeps the default automatic resolution although the catalog flag is `false`, as Vow of Obedience does.
    - Its kill is a `BuildOps` after the pawn's move, reading the destination live.
    - Only Travel walks `TravelCost`, so Quick Exit and a Raid's relocation never trigger it.
    - The bandit refill after the command may refill a site the kill empties, so the tests leave a warband behind.
13. **Resolution.**
    - Curfew and Forced Labor read theirs from the catalog (`persistent: true`), as Toll Roads does.
    - Vow of Beastkin, Tyrant and Vow of Poverty are automatic although their flags are `false`. `PowerKindsCatalogSuite` pins all of them.
14. **Test filler.** Each task runs its card's filler suites:
    - News from Afar in seven suites, as an unselected Hearth card.
    - Defame in `SearchFixture.discordCards`, `SecondChanceSuite` and `SetupProcedureSuite`.
    - Curfew in `CardSlotsSuite`, and Forced Labor in `TableSuite`.
    - None of them selects the modifier or stands Curfew or Forced Labor where it charges.
    - `RestSuite` drops Vow of Poverty as its unimplemented Rest example (spec, "Test filler"), in Task 7.

## File Structure

Production, under `src/main/scala/oathdigital/`:

| File | Change | Responsibility | Task |
|---|---|---|---|
| `gameplay/powers/RulerToll.scala` | create | the toll Curfew and Forced Labor charge | 1 |
| `gameplay/powers/economy/Curfew.scala` | create | Curfew's card and rule | 1 |
| `gameplay/powers/search/ForcedLabor.scala` | create | Forced Labor's card and rule | 1 |
| `gameplay/powers/search/NewsFromAfar.scala` | create | News from Afar's card and modifier | 2 |
| `gameplay/powers/travel/AFastSteed.scala` | create | A Fast Steed's card and modifier | 2 |
| `gameplay/powers/action/SiteCards.scala` | modify | widen to `private[powers]` (R6) | 3 |
| `gameplay/powers/economy/AwaitedReturn.scala` | create | Awaited Return's card and modifier | 3 |
| `gameplay/powers/economy/MountedLibrary.scala` | create | Mounted Library's card and modifier | 3 |
| `gameplay/powers/economy/Defame.scala` | create | Defame's card and modifier | 4 |
| `gameplay/powers/travel/SpecialEnvoy.scala` | create | Special Envoy's card and modifier | 5 |
| `gameplay/powers/travel/Tyrant.scala` | create | Tyrant's card and rule | 5 |
| `gameplay/powers/economy/VowOfBeastkin.scala` | create | Vow of Beastkin's card and rule | 6 |
| `gameplay/powers/economy/VowOfPoverty.scala` | create | Vow of Poverty's card, rule and REST | 7 |
| `gameplay/powers/ActionModifiers.scala` | modify | registers the Search, Trade and Muster powers | 1-4, 6, 7 |
| `gameplay/powers/travel/TravelModifiers.scala` | modify | registers the Travel powers | 2, 5 |
| `gameplay/powers/PhasePowerCatalog.scala` | modify | registers Vow of Poverty's REST | 7 |
| `gameplay/powers/MusterPowers.scala` | modify | retires the Curfew, Mounted Library, Defame and Vow of Beastkin stubs | 1, 3, 4, 6 |
| `gameplay/powers/RestPowers.scala` | modify | retires the Vow of Poverty stub | 7 |
| `catalog/holding/Holding{Beast,Discord,Hearth,Nomad,Order}Denizens.scala` | modify | each card leaves; every import stays used | 1-7 |
| `gameplay/cards/NewFoundations.scala` | modify | imports each moved card | 1-7 |

Tests, under `src/test/scala/oathdigital/`:

| File | Change | Tests | Task |
|---|---|---|---|
| `gameplay/powers/economy/CurfewSuite.scala` | create | 4 | 1 |
| `gameplay/powers/search/ForcedLaborSuite.scala` | create | 4 | 1 |
| `gameplay/powers/search/NewsFromAfarSuite.scala` | create | 3 | 2 |
| `gameplay/powers/travel/AFastSteedSuite.scala` | create | 3 | 2 |
| `gameplay/powers/economy/AwaitedReturnSuite.scala` | create | 2 | 3 |
| `gameplay/powers/economy/MountedLibrarySuite.scala` | create | 3 | 3 |
| `gameplay/powers/economy/DefameSuite.scala` | create | 3 | 4 |
| `gameplay/powers/travel/SpecialEnvoySuite.scala` | create | 3 | 5 |
| `gameplay/powers/travel/TyrantSuite.scala` | create | 4 | 5 |
| `gameplay/powers/economy/VowOfBeastkinSuite.scala` | create | 4 | 6 |
| `gameplay/powers/economy/VowOfPovertySuite.scala` | create | 4 | 7 |
| `gameplay/PowerKindsCatalogSuite.scala` | modify | +3 (Tasks 5, 6, 7), and new ids in its lists | 1-7 |
| `gameplay/RestSuite.scala` | modify | 0 (drops Vow of Poverty) | 7 |

Total: 37 new suite tests and 3 new pins, 40 server tests.

**Moving a card out of holding.** Each task deletes a card's `object XCard ... :` block from its holding file: from the `object` line through its `val powers` line, and the blank line after it. Each holding file keeps another user of every trait it imports, so no import line changes. Each task then adds the card to `NewFoundations.scala`'s import from the power's package, keeping the names in alphabetical order. The card objects below are copied verbatim from holding.

---

### Task 1: The ruler toll, Curfew and Forced Labor

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/RulerToll.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/Curfew.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/search/ForcedLabor.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala` (retire `Curfew`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `CurfewCard`, `ForcedLaborCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `SiteRulers.rulerOfCard`, `SiteRulers.rulerOf`, `PowerAccess.pawnSite`, `RuledCards.of`, `PowerAnswers.one`, `TradeProcedure.decisionId`, `NoteSupport.favor`.
- Produces:
  - `private[powers] object RulerToll` with `Favor: Int`, `gave: NoteKey`, `burned: NoteKey`, `keys: Vector[NoteKey]`, `enemyRuler(ready: ReadyGame, card: DenizenId, payer: PlayerId): Option[SiteRuler]`, `payment(payer: PlayerId, ruler: SiteRuler): CoreOperation`, `note(card: DenizenId, payer: PlayerId, ruler: SiteRuler)(states: NoteStates): Option[PowerNote]`.
  - `final case class Curfew` with `Curfew.forCatalog`, `Curfew.id`.
  - `final case class ForcedLabor` with `ForcedLabor.forCatalog`, `ForcedLabor.id`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}

class CurfewSuite extends munit.FunSuite:
  private val curfew = CatalogNames.denizen("Curfew")
  private val power = Curfew.forCatalog(catalog)
  /** p1's pawn site, where p1 trades. */
  private val home = CatalogNames.site("Ancient City")
  /** Where Curfew stands. */
  private val peaks = CatalogNames.site("Broken Peaks")
  /** A plain Order denizen to Trade with. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val automatic = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  /** p1 at Ancient City beside Wrestlers, with the start's 1 favor and 1
    * faceup secret, Curfew at Broken Peaks, and the Order bank holding 3
    * favor. Nobody rules either site yet. */
  private def curfewTable: Table = Table.start
    .denizen(wrestlers, at = home).denizen(curfew, at = peaks)
    .bankFavor(Suit.Order, 3)

  /** p2 rules Broken Peaks, so Curfew is p2's, and Ancient City, so p2
    * rules Wrestlers. */
  private def rivalRulesBoth: Table =
    curfewTable.warbandsAt(peaks, p2, 1).warbandsAt(home, p2, 1)

  private def tradedForFavorWithWrestlers(ready: ReadyGame)
      : EconomyActions.Completed = EconomyActions.trade(rules, ready, p1,
    Vector.empty, "favor", DecisionOptionRef.Denizen(wrestlers))

  /** Whether a Trade for favor with Wrestlers survives the preview a start
    * runs. */
  private def wrestlersOffered(ready: ReadyGame): Boolean =
    TradeProcedure.startOptions(catalog, ready, p1, TradeResource.Favor,
      automatic).exists(previewed => previewed.outcome.isRight &&
        previewed.option.ref == DecisionOptionRef.Denizen(wrestlers))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("an enemy trading with a card the ruler rules gives the ruler 1 favor " +
      "on top of the Trade's cost"):
    val ready = rivalRulesBoth.ready
    val done = tradedForFavorWithWrestlers(ready)
    val end = Look(done.result)
    // The start's 1 favor went to p2, then the Trade gained 1 Order favor.
    assertEquals(end.favor(p1), 1)
    assertEquals(end.favor(p2), Look(ready).favor(p2) + 1)
    assertEquals(end.bankFavor(Suit.Order), 3 - 1)
    assertEquals(said(done.journal.events), Vector(NoteText.Said("gave",
      s"Gave 1 favor to ${p2.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("a trader who cannot pay is not offered the card"):
    assert(wrestlersOffered(rivalRulesBoth.ready))
    assert(!wrestlersOffered(rivalRulesBoth.favor(p1, 0).ready))

  test("when bandits rule, the favor is burnt"):
    val ready = curfewTable.bandits(peaks, 1).bandits(home, 1).ready
    val done = tradedForFavorWithWrestlers(ready)
    assertEquals(Look(done.result).favor(p1), 1)
    assertEquals(Look(done.result).favor(p2), Look(ready).favor(p2))
    assertEquals(said(done.journal.events), Vector(NoteText.Said("burned",
      "Burned 1 favor.", covers = false)))

  test("the ruler trades without paying, and so does an enemy trading with " +
      "a card the ruler does not rule"):
    val rulerTrades = curfewTable.warbandsAt(peaks, p1, 1)
      .warbandsAt(home, p1, 1).ready
    val unruledCard = curfewTable.warbandsAt(peaks, p2, 1).ready
    Vector(rulerTrades, unruledCard).foreach { ready =>
      val done = tradedForFavorWithWrestlers(ready)
      // Only the Trade's own favor is gained, and nothing is paid.
      assertEquals(Look(done.result).favor(p1), Look(ready).favor(p1) + 1)
      assertEquals(said(done.journal.events), Vector.empty)
    }
```

Create `src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.{NoteText, SearchFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class ForcedLaborSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val labor = CatalogNames.denizen("Forced Labor")
  private val power = ForcedLabor.forCatalog(catalog)
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** Where Forced Labor stands. */
  private val peaks = CatalogNames.site("Broken Peaks")
  /** Plain denizens for the world deck; none is played. */
  private val deckTop = Vector("Wrestlers", "Errand Boy", "Mercenaries")
    .map(CatalogNames.denizen(_))
  private val startingSupply = 5

  /** p1 in Act at Ancient City with `startingSupply` Supply and the start's
    * 1 favor, the world deck topped by three plain cards, and Forced Labor
    * at Broken Peaks. Nobody rules either site yet. */
  private def laborTable: Table = Table.start.worldDeckTop(deckTop*)
    .supply(p1, startingSupply).denizen(labor, at = peaks)

  /** p2 rules Broken Peaks, so Forced Labor is p2's, and Ancient City, where
    * p1's pawn stands. */
  private def rivalRulesBoth: Table =
    laborTable.warbandsAt(peaks, p2, 1).warbandsAt(home, p2, 1)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("an enemy whose pawn stands at a site the ruler rules gives the ruler " +
      "1 favor on top of the Search's Supply"):
    val ready = rivalRulesBoth.ready
    val started = accepted(SearchFixture.start(ready))
    val after = Look(SearchFixture.after(started))
    assertEquals(after.favor(p1), 0)
    assertEquals(after.favor(p2), Look(ready).favor(p2) + 1)
    // The first world Search costs 2 Supply.
    assertEquals(after.supply(p1), startingSupply - 2)
    assertEquals(said(started.events), Vector(NoteText.Said("gave",
      s"Gave 1 favor to ${p2.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assertWireRoundTrips(started.events)

  test("a searcher who cannot pay is not offered the Search"):
    val automatic = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    val sources = (ready: ReadyGame) =>
      SearchProcedure.legalSources(catalog, ready, p1, automatic).map(_._1)
    // The Cradle discard is empty, so the world deck is the only source.
    assertEquals(sources(rivalRulesBoth.ready), Vector(SearchSource.WorldDeck))
    val broke = rivalRulesBoth.favor(p1, 0).ready
    assertEquals(sources(broke), Vector.empty)
    assert(SearchFixture.start(broke).isLeft)

  test("when bandits rule, the favor is burnt"):
    val ready = laborTable.bandits(peaks, 1).bandits(home, 1).ready
    val started = accepted(SearchFixture.start(ready))
    assertEquals(Look(SearchFixture.after(started)).favor(p1), 0)
    assertEquals(said(started.events), Vector(NoteText.Said("burned",
      "Burned 1 favor.", covers = false)))

  test("the ruler searches without paying, and so does an enemy whose pawn " +
      "stands at a site the ruler does not rule"):
    val rulerSearches = laborTable.warbandsAt(peaks, p1, 1)
      .warbandsAt(home, p1, 1).ready
    val unruledSite = laborTable.warbandsAt(peaks, p2, 1).ready
    Vector(rulerSearches, unruledSite).foreach { ready =>
      val started = accepted(SearchFixture.start(ready))
      assertEquals(Look(SearchFixture.after(started)).favor(p1),
        Look(ready).favor(p1))
      assertEquals(said(started.events), Vector.empty)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.CurfewSuite oathdigital.gameplay.powers.search.ForcedLaborSuite"`
Expected: compile failure, `Curfew` and `ForcedLabor` are not found.

- [ ] **Step 3: Write the shared toll**

Create `src/main/scala/oathdigital/gameplay/powers/RulerToll.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.SiteRulers
import oathdigital.model._

/** The toll Curfew and Forced Labor charge an enemy of their ruler, on top
  * of the action's own cost: 1 favor given to the ruling player, or burnt
  * when bandits rule. The rule card's ruler is the ruler of the site holding
  * it faceup; Empire rulers are not supported.
  *
  * The payment is required, so an enemy who cannot pay is refused. Toll
  * Roads charges the same toll for Travel, with lines that name the
  * traveller.
  */
private[powers] object RulerToll:
  val Favor: Int = 1
  /** "Gave {1 favor} to {Blue}." */
  val gave: NoteKey = NoteKey("gave", Vector(NotePart.Text("Gave "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "Burned {1 favor}.", when bandits rule. */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(".")))
  val keys: Vector[NoteKey] = Vector(gave, burned)

  /** The ruler of the site holding `card` faceup, when `payer` is its
    * enemy. */
  def enemyRuler(ready: ReadyGame, card: DenizenId, payer: PlayerId)
      : Option[SiteRuler] =
    SiteRulers.rulerOfCard(ready, card).filter(ruler =>
      SiteRule.enemies(ruler, SiteRuler.Player(payer)))

  /** The required payment from `payer` to `ruler`. */
  def payment(payer: PlayerId, ruler: SiteRuler): CoreOperation = ruler match
    case SiteRuler.Player(owner) => Give(Piece.Favor(Favor), payer,
      Location.PlayArea(payer), Location.PlayArea(owner), required = true)
    case _ => PayCost(payer, Location.SharedBank, Cost(favorBurnt = Favor))

  /** The line, from what the payment's step took from `payer`. */
  def note(card: DenizenId, payer: PlayerId, ruler: SiteRuler)(
      states: NoteStates): Option[PowerNote] = for
    step <- states.previous
    paid = -NoteSupport.favor(step, payer)
    if paid > 0
  yield
    val source = PowerSourceRef.Card(card)
    val amount = NoteArg.Amount(paid, NoteUnit.Favor)
    ruler match
      case SiteRuler.Player(owner) =>
        gave(source, amount, NoteArg.Player(owner))
      case _ => burned(source, amount)
```

- [ ] **Step 4: Write Curfew**

Remove `object CurfewCard` from `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/economy/Curfew.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, PowerAnswers,
  RuledCards, RulerToll}
import oathdigital.model._

object CurfewCard extends Denizen(DenizenId("119"), "Curfew", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.curfew"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** trade with cards ruled by Curfew's ruler " +
      "unless they give [favor] to its ruler. _(Give it to " +
      "Chancellor if Empire, burn it if bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Curfew (card 119, site-only), a persistent rule of a faceup site card: an
  * enemy of Curfew's ruler who trades with a card that ruler rules, an
  * adviser included, gives the ruler 1 favor, or burns it when bandits rule,
  * on top of the Trade's cost ([[RulerToll]]). The ruler is exempt.
  *
  * The toll is a required batch placed before the Trade's cost, so a trader
  * who cannot pay is refused, and the restriction look-ahead hides that
  * source. The batch reads the Trade's source when it runs and is placed for
  * every Trade by an enemy, so the cost node's shape never depends on the
  * answer. Its line reads the payment's step.
  */
final case class Curfew private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = CurfewCard.id
  def id: PowerId = Curfew.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = RulerToll.keys

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer)
        .fold(operations)(ruler => Vector(toll(ctx.activePlayer, ruler),
          Note(id, RulerToll.note(cardId, ctx.activePlayer, ruler))) ++
          operations))))

  override def applicable(ctx: PowerCtx): Boolean =
    RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer).nonEmpty

  /** The payment, when the Trade's source is a card `ruler` rules. */
  private def toll(trader: PlayerId, ruler: SiteRuler): Operation =
    BuildOps((ready, pending) => Right(
      PowerAnswers.one(pending, TradeProcedure.decisionId).flatMap(cardOf)
        .filter(ruled(ready, ruler, _))
        .map(_ => RulerToll.payment(trader, ruler)).toVector),
      required = true)

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(card) => Some(card)
    case DecisionOptionRef.Edifice(card) => Some(card)
    case _ => None

  /** Whether `ruler` rules `card`: one of its faceup advisers, or a card at
    * a site it rules. */
  private def ruled(ready: ReadyGame, ruler: SiteRuler, card: CardId)
      : Boolean = catalog.suitOf(card).exists(suit =>
    RuledCards.of(catalog, ready, ruler, suit).contains(card))

object Curfew:
  val id: PowerId = CurfewCard.power.id

  def forCatalog(catalog: ExecutableCatalog): Curfew = new Curfew(catalog)
```

- [ ] **Step 5: Write Forced Labor**

Remove `object ForcedLaborCard` from `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/search/ForcedLabor.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, RulerToll}
import oathdigital.model._

object ForcedLaborCard extends Denizen(DenizenId("112"), "Forced Labor", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.forced-labor"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** search if their pawn is at any site " +
      "ruled by Forced Labor's ruler unless they give [favor] to " +
      "its ruler. _(Give it to Chancellor if Empire, burn it if " +
      "bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Forced Labor (card 112, site-only), a persistent rule of a faceup site
  * card: an enemy of Forced Labor's ruler whose pawn stands at a site that
  * ruler rules gives the ruler 1 favor, or burns it when bandits rule, on top
  * of a Search's cost ([[RulerToll]]). The ruler is exempt.
  *
  * The payment is required and comes before the Search's Supply, so a
  * searcher who cannot pay is refused and the Search is not offered. Its line
  * reads the payment's step.
  */
final case class ForcedLabor private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = ForcedLaborCard.id
  def id: PowerId = ForcedLabor.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = RulerToll.keys

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      toll(ctx).fold(operations)(_ ++ operations))))

  override def applicable(ctx: PowerCtx): Boolean = toll(ctx).nonEmpty

  /** The payment this Search owes and its line, if it owes one. */
  private def toll(ctx: PowerCtx): Option[Vector[Operation]] = for
    ruler <- RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer)
    site <- PowerAccess.pawnSite(ctx.state, ctx.activePlayer)
    if SiteRulers.rulerOf(ctx.state, site).contains(ruler)
  yield Vector(RulerToll.payment(ctx.activePlayer, ruler),
    Note(id, RulerToll.note(cardId, ctx.activePlayer, ruler)))

object ForcedLabor:
  val id: PowerId = ForcedLaborCard.power.id

  def forCatalog(catalog: ExecutableCatalog): ForcedLabor =
    new ForcedLabor(catalog)
```

- [ ] **Step 6: Register them and retire the stub**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

- Replace the doc comment with:

  ```scala
  /** The Search, Trade, Muster and Recover modifiers, registered together with
    * the rules that change those actions: Initiation Rite's Muster cost, the
    * tolls of Curfew and Forced Labor, and the Vows' limits. Each names its
    * card, so all are always present.
    */
  ```

- Add `Curfew` to the `gameplay.powers.economy` import and `ForcedLabor` to the `gameplay.powers.search` import, alphabetically.
- Add `Vector(Curfew.forCatalog(catalog)) ++` and `Vector(ForcedLabor.forCatalog(catalog)) ++` before `Vector(InitiationRite) ++`.

In `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`, delete the line `object Curfew extends ReviewedPower(...)` and drop `Curfew` from `powers`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `CurfewCard` to the `gameplay.powers.economy` import and `ForcedLaborCard` to the `gameplay.powers.search` import.

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, change the end of `persistentRules` from:

```scala
    "denizen.herald", "denizen.diplomat")
```

to:

```scala
    "denizen.herald", "denizen.diplomat", "denizen.curfew",
    "denizen.forced-labor")
```

- [ ] **Step 7: Run the suites and the filler suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.CurfewSuite oathdigital.gameplay.powers.search.ForcedLaborSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.powers.travel.TollRoadsSuite oathdigital.gameplay.CardSlotsSuite oathdigital.testkit.TableSuite"`
Expected: PASS, 4 tests in each new suite. `CardSlotsSuite` holds Curfew only to test placement, and `TableSuite` holds Forced Labor as an adviser, where neither charges.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/RulerToll.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/Curfew.scala \
  src/main/scala/oathdigital/gameplay/powers/search/ForcedLabor.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/CurfewSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/search/ForcedLaborSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Curfew and Forced Labor charge their ruler's enemies a favor"
```

---

### Task 2: News from Afar and A Fast Steed

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/search/NewsFromAfar.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/AFastSteed.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `NewsFromAfarCard`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `AFastSteedCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/search/NewsFromAfarSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/travel/AFastSteedSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `SelectedModifier`, `TravelPayments.withoutSupply`, `TravelRoute.pawnMove`, `PlayerFacts.player`, `SearchFixture`, `TravelFixture`.
- Produces: `NewsFromAfar.forCatalog`, `NewsFromAfar.id`; `AFastSteed.forCatalog`, `AFastSteed.id`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/search/NewsFromAfarSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class NewsFromAfarSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val news = CatalogNames.denizen("News from Afar")
  private val modifiers = Vector(NewsFromAfar.id)
  /** Plain denizens for the world deck and the Cradle discard; none is
    * played. */
  private val deckTop = Vector("Wrestlers", "Errand Boy", "Mercenaries")
    .map(CatalogNames.denizen(_))
  private val discardPile = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))
  private val region = Region.Cradle // Ancient City's region
  private val startingSupply = 5

  /** p1 in Act at Ancient City holding News from Afar faceup and `favor`
    * favor, with `startingSupply` Supply, the world deck topped by three
    * plain cards and the Cradle discard holding three more. */
  private def holdingNews(favor: Int = 2): ReadyGame = Table.start
    .worldDeckTop(deckTop*).discarded(region, discardPile*)
    .supply(p1, startingSupply).adviser(p1, news).favor(p1, favor).ready

  private def searchedWithNews(ready: ReadyGame, source: String)
      : OathTransition = accepted(rules.startWalker(Ready(ready),
    ActionRef.Search, p1, modifiers, Vector(DecisionOptionRef.Button(source))))

  test("selected, a world Search places 2 favor on it and spends no Supply"):
    val ready = holdingNews()
    val started = searchedWithNews(ready, "search:world")
    val after = Look(SearchFixture.after(started))
    assertEquals(after.supply(p1), startingSupply)
    assertEquals(after.favor(p1), 0)
    assertEquals(after.tokensOn(news), Tokens(2, 0))
    assertEquals(ActPowerHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assertWireRoundTrips(started.events)

  test("a Search of a regional discard spends no Supply either"):
    val started = searchedWithNews(holdingNews(),
      s"search:regional-discard:${region.key}")
    assertEquals(Look(SearchFixture.after(started)).supply(p1), startingSupply)

  test("it is a Search modifier, and cannot be selected without 2 favor to " +
      "place"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(holdingNews(), ActionRef.Search).contains(NewsFromAfar.id))
    assert(!offered(holdingNews(), ActionRef.Travel).contains(NewsFromAfar.id))
    assert(!offered(holdingNews(favor = 1), ActionRef.Search)
      .contains(NewsFromAfar.id))
```

Create `src/test/scala/oathdigital/gameplay/powers/travel/AFastSteedSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class AFastSteedSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val steed = CatalogNames.denizen("A Fast Steed")
  private val modifiers = Vector(AFastSteed.id)

  /** The actor holds A Fast Steed faceup, 1 favor, 7 Supply and `warbands`
    * warbands on the board, at Buried Giant, the Cradle's plains. */
  private def ridingWith(warbands: Int): ReadyGame =
    Table.from(board(supply = 7)).adviser(actor, steed).favor(actor, 1)
      .boardWarbands(actor, warbands).unchecked

  test("with three warbands on the board, Travel spends no Supply and the " +
      "favor is placed on it"):
    val ready = ridingWith(warbands = 3)
    assertEquals(candidates(ready, modifiers).get(coast), Some(0))
    val done = accepted(travel(ready, coast, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).tokensOn(steed), Tokens(1, 0))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("with four warbands on the board, Travel pays its Supply, and the " +
      "favor is still placed: the player owns the choice to select it"):
    val result = Outcomes.ready(accepted(travel(ridingWith(warbands = 4), coast,
      modifiers)).state)
    // The printed 1 for a Travel within the Cradle.
    assertEquals(Look(result).supply(actor), 7 - 1)
    assertEquals(Look(result).tokensOn(steed), Tokens(1, 0))

  test("it is a Travel modifier, and cannot be selected without a favor to " +
      "place"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, actor, action)).map(_.id)
    val ready = ridingWith(warbands = 3)
    assert(offered(ready, ActionRef.Travel).contains(AFastSteed.id))
    assert(!offered(ready, ActionRef.Search).contains(AFastSteed.id))
    val broke = Table.from(ready).favor(actor, 0).unchecked
    assert(!offered(broke, ActionRef.Travel).contains(AFastSteed.id))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.NewsFromAfarSuite oathdigital.gameplay.powers.travel.AFastSteedSuite"`
Expected: compile failure, `NewsFromAfar` and `AFastSteed` are not found.

- [ ] **Step 3: Write News from Afar**

Remove `object NewsFromAfarCard` from `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/search/NewsFromAfar.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object NewsFromAfarCard extends Denizen(DenizenId("134"), "News from Afar", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.news-from-alfar"),
    persistent = false, cost = Cost(favor = 2),
    text = "Spend no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** News from Afar (card 134), a selected Search modifier. Cost: 2 favor
  * placed on the card. The Search spends no Supply, whatever its source.
  *
  * The Search's cost node is rewritten, as Disciples rewrites it, so that it
  * drops the player's Supply payment when it runs. A toll the node also
  * holds, such as Forced Labor's, is a separate operation and is still paid.
  * The start line's cost span shows what was paid, so it writes no line.
  */
final case class NewsFromAfar private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = NewsFromAfarCard.id
  def id: PowerId = NewsFromAfar.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(favor = NewsFromAfar.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      operations.map {
        case payment: BuildOps => payment.copy(build = (ready, pending) =>
          payment.build(ready, pending).map(_.filterNot {
            case SpendSupply(player, _, _) => player == ctx.activePlayer
            case _ => false
          }))
        case other => other
      })))

object NewsFromAfar:
  val id: PowerId = NewsFromAfarCard.power.id
  val Favor: Int = 2

  def forCatalog(catalog: ExecutableCatalog): NewsFromAfar =
    new NewsFromAfar(catalog)
```

- [ ] **Step 4: Write A Fast Steed**

Remove `object AFastSteedCard` from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/travel/AFastSteed.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{PlayerFacts, SelectedModifier}
import oathdigital.model._

object AFastSteedCard extends Denizen(DenizenId("172"), "A Fast Steed", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.old-fast-steed"),
    persistent = false, cost = Cost(favor = 1),
    text = "Spend no Supply if you have three or fewer warbands on your " +
      "board.")
  val powers: Vector[PrintedPower] = Vector(power)

/** A Fast Steed (card 172), a selected Travel modifier. Cost: 1 favor placed
  * on the card. Travel spends no Supply while the player has three or fewer
  * warbands on their board.
  *
  * The warbands are read when the cost node folds, after the favor is
  * placed and before anything else changes the board. The favor is paid
  * whether or not the condition holds, as for Tents: the player owns the
  * choice to select it. The start line's cost span shows what was paid, so
  * it writes no line. The catalog's id carries a typo, `old-fast-steed`.
  */
final case class AFastSteed private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = AFastSteedCard.id
  def id: PowerId = AFastSteed.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def cost: Cost = Cost(favor = AFastSteed.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      if fewWarbands(ctx.state, ctx.activePlayer) then
        TravelPayments.withoutSupply(operations, ctx.activePlayer)
      else operations)))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

  private def fewWarbands(ready: ReadyGame, actor: PlayerId): Boolean =
    PlayerFacts.player(ready, actor)
      .exists(_.board.warbands <= AFastSteed.MostWarbands)

object AFastSteed:
  val id: PowerId = AFastSteedCard.power.id
  val Favor: Int = 1
  /** The most warbands on the board for which Travel is free. */
  val MostWarbands: Int = 3

  def forCatalog(catalog: ExecutableCatalog): AFastSteed =
    new AFastSteed(catalog)
```

- [ ] **Step 5: Register them**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`, add `NewsFromAfar` to the `gameplay.powers.search` import, and `Vector(NewsFromAfar.forCatalog(catalog)) ++` after `Vector(CropRotation.forCatalog(catalog)) ++`.

In `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`, change the last line from:

```scala
      Vector(GraspingVines.forCatalog(catalog))
```

to:

```scala
      Vector(GraspingVines.forCatalog(catalog)) ++
      Vector(AFastSteed.forCatalog(catalog))
```

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `NewsFromAfarCard` to the `gameplay.powers.search` import and `AFastSteedCard` to the `gameplay.powers.travel` import.

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, change the end of `modifiers` from:

```scala
    "denizen.royal-stables", "denizen.moving-market")
```

to:

```scala
    "denizen.royal-stables", "denizen.moving-market",
    "denizen.news-from-alfar", "denizen.old-fast-steed")
```

- [ ] **Step 6: Run the suites and the filler suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.NewsFromAfarSuite oathdigital.gameplay.powers.travel.AFastSteedSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.travel.TentsSuite oathdigital.gameplay.powers.travel.RoyalStablesSuite oathdigital.gameplay.powers.whenplayed.FabledFeastSuite oathdigital.gameplay.powers.search.DisciplesSuite oathdigital.gameplay.powers.action.EnchantressSuite oathdigital.gameplay.powers.cardplay.SaddleMakersSuite oathdigital.gameplay.powers.action.HonorGuardSuite oathdigital.gameplay.powers.banner.PeoplesFavorMobSuite oathdigital.gameplay.powers.rest.InsomniaSuite"`
Expected: PASS, 3 tests in each new suite. The last seven suites hold News from Afar as an unselected Hearth card.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/search/NewsFromAfar.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/AFastSteed.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/search/NewsFromAfarSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/AFastSteedSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(modifiers): News from Afar and A Fast Steed waive the Supply"
```

---

### Task 3: Awaited Return and Mounted Library

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala` (R6)
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/MountedLibrary.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala` (retire `MountedLibrary`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `AwaitedReturnCard`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `MountedLibraryCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/AwaitedReturnSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/MountedLibrarySuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `SiteCards.discard`, `SiteRulers.siteOf`, `PowerAnswers.one`, `TradeProcedure.decisionId`, `NoteSupport.answer`, `NoteSupport.secrets`, `NoteSupport.warbands`, `PlayerFacts`.
- Produces: `private[powers] final class SiteCards`; `AwaitedReturn.forCatalog`, `AwaitedReturn.id`; `MountedLibrary.forCatalog`, `MountedLibrary.id`, `MountedLibrary.Priority`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/economy/AwaitedReturnSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class AwaitedReturnSuite extends munit.FunSuite:
  private val awaited = CatalogNames.denizen("Awaited Return")
  private val power = AwaitedReturn.forCatalog(catalog)
  private val modifiers = Vector(AwaitedReturn.id)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Order denizen to Trade with. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  /** p1 at Ancient City beside Wrestlers, holding Awaited Return faceup,
    * `supply` Supply and the start's 3 warbands, 1 favor and 1 faceup secret.
    * The Order bank holds 3 favor. */
  private def holdingAwaitedReturn(supply: Int = 7): Table = Table.start
    .denizen(wrestlers, at = home).adviser(p1, awaited).supply(p1, supply)
    .bankFavor(Suit.Order, 3)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("selected, the Trade spends no Supply and sacrifices one warband " +
      "from the board"):
    val ready = holdingAwaitedReturn().ready
    val done = EconomyActions.trade(rules, ready, p1, modifiers, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val end = Look(done.result)
    assertEquals(end.supply(p1), 7)
    assertEquals(end.boardWarbands(p1), Look(ready).boardWarbands(p1) - 1)
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(said(done.journal.events), Vector(NoteText.Said("sacrificed",
      "Spent no Supply after sacrificing 1 warband.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("it lets a player with no Supply trade, and is not offered with no " +
      "warband on the board"):
    val tradeable = (ready: ReadyGame, selected: Vector[PowerId]) =>
      TradeProcedure.startOptions(catalog, ready, p1, TradeResource.Favor,
        WalkerPowers.selected(WalkerPowerCatalog.default(catalog), selected))
        .exists(_.outcome.isRight)
    val noSupply = holdingAwaitedReturn(supply = 0).ready
    assert(!tradeable(noSupply, Vector.empty))
    assert(tradeable(noSupply, modifiers))
    val offered = (ready: ReadyGame) =>
      accepted(rules.offerableWalkerPowers(ready, p1, ActionRef.Trade))
        .map(_.id)
    assert(offered(holdingAwaitedReturn().ready).contains(AwaitedReturn.id))
    assert(!offered(holdingAwaitedReturn().boardWarbands(p1, 0).ready)
      .contains(AwaitedReturn.id))
```

Create `src/test/scala/oathdigital/gameplay/powers/economy/MountedLibrarySuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1

class MountedLibrarySuite extends munit.FunSuite:
  private val library = CatalogNames.denizen("Mounted Library")
  private val power = MountedLibrary.forCatalog(catalog)
  private val modifiers = Vector(MountedLibrary.id)
  private val home = CatalogNames.site("Ancient City")
  /** A Nomad adviser, so a Trade with Mounted Library gains 1 secret, or 1
    * more favor. */
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  /** A plain Order denizen to Trade with instead. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  /** p1 at Ancient City beside Mounted Library and Wrestlers, holding Horse
    * Archers faceup, 2 favor and the start's 1 faceup secret. The Nomad and
    * Order banks hold 3 favor each. */
  private def besideTheLibrary: Table = Table.start
    .denizen(library, at = home).denizen(wrestlers, at = home)
    .adviser(p1, horseArchers).favor(p1, 2)
    .bankFavor(Suit.Nomad, 3).bankFavor(Suit.Order, 3)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def secrets(look: Look): Int =
    look.faceUpSecrets(p1) + look.faceDownSecrets(p1)

  test("trading with it for secrets doubles them, then it is discarded and " +
      "its favor goes back to the Nomad bank"):
    val ready = besideTheLibrary.ready
    val done = EconomyActions.trade(rules, ready, p1, modifiers, "secret",
      DecisionOptionRef.Denizen(library))
    val end = Look(done.result)
    // The start's secret, 1 for Horse Archers, and 1 more.
    assertEquals(secrets(end), 3)
    assertEquals(end.cardsAt(home), Vector[CardId](wrestlers))
    assert(end.discardAfter(home).contains(library))
    // The Trade placed 1 favor on it; the discard returns it.
    assertEquals(end.bankFavor(Suit.Nomad), 3 + 1)
    assertEquals(said(done.journal.events), Vector(
      NoteText.Said("doubled", "Gained 1 more secret.", covers = true),
      NoteText.Said("discarded", "Discarded after the Trade.", covers = true)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("trading with it for favor gains no more, and its discard returns the " +
      "secret paid onto it facedown"):
    val ready = besideTheLibrary.ready
    val done = EconomyActions.trade(rules, ready, p1, modifiers, "favor",
      DecisionOptionRef.Denizen(library))
    val end = Look(done.result)
    // 1 Nomad favor, and 1 more for Horse Archers.
    assertEquals(end.favor(p1), 2 + 2)
    assertEquals(secrets(end), 1)
    assertEquals(end.faceDownSecrets(p1), 1)
    assert(end.discardAfter(home).contains(library))
    assertEquals(said(done.journal.events), Vector(NoteText.Said("discarded",
      "Discarded after the Trade.", covers = true)))

  test("selected for a Trade with another card, it is still discarded; " +
      "unselected, it stays"):
    val ready = besideTheLibrary.ready
    val selected = EconomyActions.trade(rules, ready, p1, modifiers, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    assert(Look(selected.result).discardAfter(home).contains(library))
    val unselected = EconomyActions.trade(rules, ready, p1, Vector.empty,
      "favor", DecisionOptionRef.Denizen(wrestlers))
    assert(Look(unselected.result).cardsAt(home).contains(library))
    assertEquals(said(unselected.journal.events), Vector.empty)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.AwaitedReturnSuite oathdigital.gameplay.powers.economy.MountedLibrarySuite"`
Expected: compile failure, `AwaitedReturn` and `MountedLibrary` are not found.

- [ ] **Step 3: Widen `SiteCards` (R6)**

In `src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala`, change:

```scala
/** The cards of `suits` at a site that a power may discard, for Dark
  * Enforcer, Taming Charm and Great Feast: faceup denizens and ruined
  * edifices, in site order, each with its suit. An intact edifice is locked
  * and stays, and a facedown card has no suit.
```

to:

```scala
/** The cards of `suits` at a site that a power may discard, for Dark
  * Enforcer, Taming Charm and Great Feast: faceup denizens and ruined
  * edifices, in site order, each with its suit. An intact edifice is locked
  * and stays, and a facedown card has no suit. Mounted Library discards
  * itself through `discard`.
```

and change `private[action] final class SiteCards` to `private[powers] final class SiteCards`.

- [ ] **Step 4: Write Awaited Return**

Remove `object AwaitedReturnCard` from `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts,
  SelectedModifier}
import oathdigital.model._

object AwaitedReturnCard extends Denizen(DenizenId("150"), "Awaited Return", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.awaited-return"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply if you sacrifice one warband from your " +
      "board.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Awaited Return (card 150), a selected Trade modifier: the Trade spends no
  * Supply, and one warband from the player's board is sacrificed at its cost
  * step. The printed card's action icon is Trade.
  *
  * The Trade's cost node loses the player's Supply payment and gains a
  * required batch that kills one warband from the board. It is selectable
  * only with a warband on the board, so the kill always has one. It folds
  * before the Cup of Plenty, which then finds no payment to waive.
  *
  * Its line, "Spent no Supply after sacrificing {1} warband.", reads the
  * kill's step. No generic line exists for a kill.
  */
final case class AwaitedReturn private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = AwaitedReturnCard.id
  def id: PowerId = AwaitedReturn.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def noteKeys: Vector[NoteKey] = Vector(AwaitedReturn.sacrificed)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      operations.filterNot {
        case SpendSupply(player, _, _) => player == ctx.activePlayer
        case _ => false
      } ++ Vector(sacrifice(ctx.activePlayer),
        Note(id, sacrificedNote(_, ctx.activePlayer))))))

  /** Selectable only with a warband on the board to sacrifice. */
  override def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    super.selectable(ready, actor) &&
      PlayerFacts.player(ready, actor).exists(_.board.warbands > 0)

  private def sacrifice(actor: PlayerId): Operation =
    BuildOps((ready, _) => PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector[CoreOperation](Kill(
        Piece.Warbands(kind, AwaitedReturn.Sacrificed),
        PositionedLocation(Location.PlayArea(actor))))), required = true)

  private def sacrificedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    step <- states.previous
    lost = -NoteSupport.warbands(step, actor)
    if lost > 0
  yield AwaitedReturn.sacrificed(PowerSourceRef.Card(cardId),
    NoteArg.Number(lost))

object AwaitedReturn:
  val id: PowerId = AwaitedReturnCard.power.id
  val Sacrificed: Int = 1
  /** "Spent no Supply after sacrificing {n} warband." */
  val sacrificed: NoteKey = NoteKey("sacrificed", Vector(
    NotePart.Text("Spent no Supply after sacrificing "), NotePart.Arg(0),
    NotePart.Plural(0, " warband.", " warbands.")))

  def forCatalog(catalog: ExecutableCatalog): AwaitedReturn =
    new AwaitedReturn(catalog)
```

- [ ] **Step 5: Write Mounted Library**

Remove `object MountedLibraryCard` from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/economy/MountedLibrary.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers,
  SelectedModifier}
import oathdigital.gameplay.powers.action.SiteCards
import oathdigital.model._

object MountedLibraryCard extends Denizen(DenizenId("248"), "Mounted Library", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.mounted-library"),
    persistent = false, cost = Cost.free,
    text = "If trading on Mounted Library for [secret], gain double the " +
      "[secret], then discard Mounted Library.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mounted Library (card 248, site-only), a selected Trade modifier with no
  * cost: a Trade for secrets with Mounted Library as its source gains double
  * the secrets, and once selected, Mounted Library is discarded at the end of
  * the Trade, whatever the Trade was.
  *
  * The doubling is a second secret gain after the Trade's own, added for
  * every Trade and run only when Mounted Library was the source, as The Old
  * Oak's is. Its line covers that second gain's generic line only, so the
  * Trade's own "Gained" line stays and this one follows it.
  *
  * The discard is appended to the Trade's root and folds at priority 1, so
  * it comes after every other modifier's root question. It is the standard
  * discard ([[SiteCards]]) from wherever the card stands: its favor returns
  * to the Nomad bank and its secrets to the trader facedown, so a Trade for
  * favor with it returns the secret paid onto it. Its line covers the
  * generic Discarded line.
  */
final case class MountedLibrary private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import MountedLibrary._

  val cardId: DenizenId = MountedLibraryCard.id
  def id: PowerId = MountedLibrary.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def priority: Int = Priority
  override def noteKeys: Vector[NoteKey] = Vector(doubled, discarded)

  private val nomadCards = new SiteCards(catalog, Set(Suit.Nomad))

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      operations ++ Vector(double(ctx.activePlayer,
        secretsGained(operations, ctx.activePlayer)),
        Note(id, doubledNote(_, ctx.activePlayer), covers = true)))),
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children ++ Vector(discard(ctx.activePlayer),
        Note(id, discardedNote, covers = true)))))

  /** The secrets the Trade's gain node gives `actor`. */
  private def secretsGained(operations: Vector[Operation], actor: PlayerId)
      : Int = operations.collect {
    case Gain.Secrets(player, amount) if player == actor => amount
  }.sum

  private def double(actor: PlayerId, secrets: Int): Operation =
    BuildOps((_, pending) => Right(
      if secrets > 0 && tradedWith(pending) then
        Vector[CoreOperation](Gain.Secrets(actor, secrets))
      else Vector.empty))

  private def tradedWith(pending: PendingTree): Boolean =
    PowerAnswers.one(pending, TradeProcedure.decisionId)
      .contains(DecisionOptionRef.Denizen(cardId))

  /** The secrets the doubling's step gained, when it was the source. */
  private def doubledNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    step <- states.previous
    if NoteSupport.answer(states, TradeProcedure.decisionId)
      .contains(DecisionOptionRef.Denizen(cardId))
    gained = NoteSupport.secrets(step, actor)
    if gained > 0
  yield doubled(PowerSourceRef.Card(cardId), NoteArg.Number(gained))

  /** The standard discard of the card from the site it stands at. */
  private def discard(actor: PlayerId): Operation = BuildOps((ready, _) =>
    (for
      site <- SiteRulers.siteOf(ready, cardId)
      card <- ready.game.current.map.sites.get(site)
        .flatMap(_.denizens.find(_.id == cardId))
    yield nomadCards.discard(ready, site, card, Suit.Nomad, actor,
      required = true)) match
      case Some(discarding) => discarding.map(Vector(_))
      case None => Right(Vector.empty))

  /** Written when the step before took the card off its site. */
  private def discardedNote(states: NoteStates): Option[PowerNote] = for
    (before, after) <- states.previous
    if SiteRulers.siteOf(before, cardId).nonEmpty &&
      SiteRulers.siteOf(after, cardId).isEmpty
  yield discarded(PowerSourceRef.Card(cardId))

object MountedLibrary:
  val id: PowerId = MountedLibraryCard.power.id
  /** Folds after the Trade modifiers at priority 0, so its discard ends the
    * Trade. */
  val Priority: Int = 1
  /** "Gained {n} more secrets." */
  val doubled: NoteKey = NoteKey("doubled", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Plural(0, " more secret.", " more secrets.")))
  /** "Discarded after the Trade." */
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Discarded after the Trade.")))

  def forCatalog(catalog: ExecutableCatalog): MountedLibrary =
    new MountedLibrary(catalog)
```

- [ ] **Step 6: Register them and retire the stub**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`, add `AwaitedReturn` and `MountedLibrary` to the `gameplay.powers.economy` import, and add `Vector(AwaitedReturn.forCatalog(catalog)) ++` and `Vector(MountedLibrary.forCatalog(catalog)) ++` after `Vector(MovingMarket.forCatalog(catalog)) ++`.

In `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`, delete the line `object MountedLibrary extends ReviewedPower(...)` and drop `MountedLibrary` from `powers`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `AwaitedReturnCard` and `MountedLibraryCard` to the `gameplay.powers.economy` import.

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, change the end of `modifiers` from:

```scala
    "denizen.news-from-alfar", "denizen.old-fast-steed")
```

to:

```scala
    "denizen.news-from-alfar", "denizen.old-fast-steed",
    "denizen.awaited-return", "denizen.mounted-library")
```

- [ ] **Step 7: Run the suites and the suites that share their code**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.AwaitedReturnSuite oathdigital.gameplay.powers.economy.MountedLibrarySuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.powers.action.DarkEnforcerSuite oathdigital.gameplay.powers.action.TamingCharmSuite oathdigital.gameplay.powers.action.GreatFeastSuite oathdigital.gameplay.powers.economy.MovingMarketSuite oathdigital.gameplay.powers.economy.TheOldOakSuite oathdigital.gameplay.powers.economy.CupOfPlentySuite"`
Expected: PASS, 2 tests in `AwaitedReturnSuite` and 3 in `MountedLibrarySuite`.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/MountedLibrary.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/AwaitedReturnSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/MountedLibrarySuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Awaited Return trades for a warband, Mounted Library doubles secrets and leaves"
```

---

### Task 4: Defame

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/Defame.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala` (retire `Defame`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `DefameCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/DefameSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.one`, `PowerAnswers.missing`, `TradeProcedure.decisionId`, `NoteSupport.answer`, `NoteSupport.favor`, `Burn.favor`.
- Produces: `Defame.forCatalog`, `Defame.id`, `Defame.decisionId` (`trade.defame.burn`), `Defame.skip`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/DefameSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class DefameSuite extends munit.FunSuite:
  private val defame = CatalogNames.denizen("Defame")
  private val power = Defame.forCatalog(catalog)
  private val modifiers = Vector(Defame.id)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Discord denizen to Trade with. */
  private val mercenaries = CatalogNames.denizen("Mercenaries")
  /** A plain Order denizen to Trade with instead. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  /** p1 at Ancient City beside Mercenaries and Wrestlers, holding Defame as
    * a faceup adviser. Every board holds the start's 1 favor, and the
    * Discord and Order banks hold 3 favor each. */
  private def defameTable: Table = Table.start
    .denizen(mercenaries, at = home).denizen(wrestlers, at = home)
    .adviser(p1, defame).bankFavor(Suit.Discord, 3).bankFavor(Suit.Order, 3)

  /** p1 Trades for favor with Mercenaries, having selected Defame, and is
    * asked whose favor to burn. */
  private def tradedForFavorWithMercenaries(ready: ReadyGame): OathTransition =
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      modifiers, Vector(DecisionOptionRef.Button("favor"))))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(mercenaries))))
    asked.copy(events = started.events ++ asked.events)

  /** `asked`, answered with `ref`; the events run from the Trade's start. */
  private def answered(asked: OathTransition, ref: DecisionOptionRef)
      : OathTransition =
    val done = accepted(rules.resolveWalker(asked.state, p1, Defame.decisionId,
      ChooseOneAnswer(ref)))
    done.copy(events = asked.events ++ done.events)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("after trading with a Discord card, the chosen player burns 1 favor " +
      "from their board"):
    val ready = defameTable.ready
    val asked = tradedForFavorWithMercenaries(ready)
    parked.assertParked(asked.state, ActionRef.Trade, Defame.decisionId, p1)
    // Every board holds favor, so Skip is not offered.
    assertEquals(PhasePowerHarness.offered(asked, p1), Some(Vector(
      "player" -> p1.value, "player" -> p2.value, "player" -> p3.value)))
    val done = answered(asked, DecisionOptionRef.Player(p2))
    val end = Outcomes.ready(done.state)
    assertEquals(Look(end).favor(p2), Look(ready).favor(p2) - 1)
    assertEquals(said(done.events), Vector(NoteText.Said("burned",
      s"Burned 1 favor from ${p2.value}'s board.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), end)
    assertWireRoundTrips(done.events)

  test("Skip is offered when a player has no favor, and burns nothing"):
    val ready = defameTable.favor(p3, 0).ready
    val asked = tradedForFavorWithMercenaries(ready)
    assertEquals(PhasePowerHarness.offered(asked, p1), Some(Vector(
      "player" -> p1.value, "player" -> p2.value, "button" -> "skip")))
    val done = answered(asked, Defame.skip)
    assertEquals(Look(Outcomes.ready(done.state)).favor(p2),
      Look(ready).favor(p2))
    assertEquals(said(done.events), Vector.empty)

  test("a Trade with a card of another suit, or one after which nobody holds " +
      "favor, asks nothing"):
    val otherSuit = EconomyActions.trade(rules, defameTable.ready, p1,
      modifiers, "favor", DecisionOptionRef.Denizen(wrestlers))
    // A Trade for secrets spends p1's 2 favor, and the others hold none.
    val nobodyHolds = EconomyActions.trade(rules,
      defameTable.favor(p1, 2).favor(p2, 0).favor(p3, 0).ready, p1, modifiers,
      "secret", DecisionOptionRef.Denizen(mercenaries))
    Vector(otherSuit, nobodyHolds).foreach { done =>
      assertEquals(parked.parkedDecision(Ready(done.result)), None)
      assertEquals(said(done.journal.events), Vector.empty)
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.DefameSuite"`
Expected: compile failure, `Defame` is not found.

- [ ] **Step 3: Write Defame**

Remove `object DefameCard` from `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/economy/Defame.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers,
  SelectedModifier}
import oathdigital.model._

object DefameCard extends Denizen(DenizenId("102"), "Defame", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.defame"),
    persistent = false, cost = Cost.free,
    text = "After trading with a [suit-discord] card, burn " +
      "[favor-burnt] from any player board.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Defame (card 102), a selected Trade modifier with no cost: after trading
  * with a Discord card, burn 1 favor from any player's board.
  *
  * One `Branch` is appended to the Trade's root, as Moving Market's is: a
  * decision inside the Economy `Branch` would fail on resume. It asks only
  * when the source was Discord, a denizen or an edifice by its suit, and some
  * player holds favor. The options are the players with favor, the actor
  * included, in seat order, then Skip when some player holds none, since
  * burning from an empty board is legal and burns nothing. Skip writes
  * nothing.
  *
  * Its line names the chosen player, the actor included: "Burned {1 favor}
  * from {Blue}'s board." No generic line exists for a burn.
  */
final case class Defame private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import Defame._

  val cardId: DenizenId = DefameCard.id
  def id: PowerId = Defame.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def noteKeys: Vector[NoteKey] = Vector(burned)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ offer(ctx.activePlayer))))

  private def offer(actor: PlayerId): Operation = Branch((ready, pending) =>
    val players = ready.game.current.players
    val holding = players.filter(_.board.favor > 0).map(_.player)
    if !discordSource(pending) || holding.isEmpty then Vector.empty
    else
      val options: Vector[DecisionOption] = holding.map(player =>
        DecisionOption.Player(DecisionOptionRef.Player(player))) ++
        Option.when(holding.size < players.size)(
          DecisionOption.Button(skip, "Skip")).toVector
      Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(options,
          heading = Some("Defame: burn 1 favor from a player's board"))),
        BuildOps((_, answered) => burn(answered)),
        Note(id, burnedNote)))

  private def discordSource(pending: PendingTree): Boolean =
    PowerAnswers.one(pending, TradeProcedure.decisionId).flatMap {
      case DecisionOptionRef.Denizen(card) => catalog.suitOf(card)
      case DecisionOptionRef.Edifice(card) => catalog.suitOf(card)
      case _ => None
    }.contains(Suit.Discord)

  private def burn(pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case Some(`skip`) => Right(Vector.empty)
      case Some(DecisionOptionRef.Player(target)) => Right(Vector(
        Burn.favor(Burned, PositionedLocation(Location.PlayArea(target)))))
      case Some(other) => Left(OathViolation.InvalidEventOrder(
        s"${other.wireId} is not a player or Skip"))
      case None => Left(PowerAnswers.missing(decisionId))

  /** The favor the burn's step took from the chosen board. */
  private def burnedNote(states: NoteStates): Option[PowerNote] = for
    case DecisionOptionRef.Player(target) <-
      NoteSupport.answer(states, decisionId)
    step <- states.previous
    amount = -NoteSupport.favor(step, target)
    if amount > 0
  yield burned(PowerSourceRef.Card(cardId),
    NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Player(target))

object Defame:
  val id: PowerId = DefameCard.power.id
  /** Under the `trade.` prefix, as Moving Market's is. */
  val decisionId: String = "trade.defame.burn"
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")
  val Burned: Int = 1
  /** "Burned {1 favor} from {Blue}'s board." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text("'s board.")))

  def forCatalog(catalog: ExecutableCatalog): Defame = new Defame(catalog)
```

- [ ] **Step 4: Register it and retire the stub**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`, add `Defame` to the `gameplay.powers.economy` import, and `Vector(Defame.forCatalog(catalog)) ++` after `Vector(MountedLibrary.forCatalog(catalog)) ++`.

In `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`, delete the line `object Defame extends ReviewedPower(...)` and drop `Defame` from `powers`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `DefameCard` to the `gameplay.powers.economy` import.

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, change the end of `modifiers` from:

```scala
    "denizen.awaited-return", "denizen.mounted-library")
```

to:

```scala
    "denizen.awaited-return", "denizen.mounted-library", "denizen.defame")
```

- [ ] **Step 5: Run the suite and the filler suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.DefameSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.powers.economy.MovingMarketSuite oathdigital.gameplay.powers.action.SecondChanceSuite oathdigital.gameplay.setup.SetupProcedureSuite oathdigital.gameplay.powers.search.AugurySuite oathdigital.gameplay.powers.search.TruthfulHarpSuite"`
Expected: PASS, 3 tests in `DefameSuite`. The last four hold Defame as an unselected Discord card.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/Defame.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/DefameSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Defame burns a favor from a chosen board after a Discord Trade"
```

---

### Task 5: Special Envoy and Tyrant

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/SpecialEnvoy.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/Tyrant.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `SpecialEnvoyCard`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `TyrantCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/travel/SpecialEnvoySuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/travel/TyrantSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `RoyalStables.Priority`, `TravelPayments.withoutSupply`, `TravelRoute.pawnMove`, `PlayerFacts.forceKind`, `TravelFixture`, `DragonskinDrum.id`.
- Produces: `SpecialEnvoy.forCatalog`, `SpecialEnvoy.id`, `SpecialEnvoy.Priority`; `case object Tyrant` with `killed`, `killedBandits`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/travel/SpecialEnvoySuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class SpecialEnvoySuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val envoy = CatalogNames.denizen("Special Envoy")
  private val drum = CatalogNames.relic("Dragonskin Drum")
  private val power = SpecialEnvoy.forCatalog(catalog)
  private val modifiers = Vector(SpecialEnvoy.id)

  /** The actor holds Special Envoy faceup and 7 Supply, at Buried Giant, the
    * Cradle's plains. */
  private def holdingEnvoy: ReadyGame =
    Table.from(board(supply = 7)).adviser(actor, envoy).unchecked

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("selected, Travel spends no Supply and then the Act Phase ends"):
    val ready = holdingEnvoy
    assertEquals(candidates(ready, modifiers).get(coast), Some(0))
    val done = accepted(travel(ready, coast, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).phase, Phase.Rest)
    assertEquals(said(done.events), Vector(NoteText.Said("ended",
      "The Act Phase ended.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("Dragonskin Drum's gain comes before the Act Phase ends"):
    val ready = withRelic(holdingEnvoy, drum)
    val done = accepted(travel(ready, coast, modifiers :+ DragonskinDrum.id))
    val ops = PhasePowerHarness.ops(done.events)
    val gained = ops.indexWhere {
      case Gain.Warbands(player, _, _) => player == actor
      case _ => false
    }
    val ended = ops.indexOf(EnterPhase(Phase.Rest))
    assert(gained >= 0 && ended > gained, ops.toString)
    assertEquals(Look(Outcomes.ready(done.state)).boardWarbands(actor),
      Look(ready).boardWarbands(actor) + 1)

  test("it is a Travel modifier, and an unselected Travel keeps the Act " +
      "Phase"):
    val offered = (action: ActionRef) => accepted(
      rules.offerableWalkerPowers(holdingEnvoy, actor, action)).map(_.id)
    assert(offered(ActionRef.Travel).contains(SpecialEnvoy.id))
    assert(!offered(ActionRef.Search).contains(SpecialEnvoy.id))
    val result = Outcomes.ready(accepted(travel(holdingEnvoy, coast)).state)
    assertEquals(Look(result).phase, Phase.Act)
    // The printed 1 for a Travel within the Cradle.
    assertEquals(Look(result).supply(actor), 7 - 1)
```

Create `src/test/scala/oathdigital/gameplay/powers/travel/TyrantSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class TyrantSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val tyrant = CatalogNames.denizen("Tyrant")
  private val rival = Table.p2

  /** The actor holds Tyrant faceup, at Buried Giant, the Cradle's plains,
    * and the coast is set by `atCoast`. */
  private def holdingTyrant(atCoast: Table => Table): ReadyGame =
    atCoast(Table.from(board(supply = 7)).adviser(actor, tyrant)).unchecked

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(Tyrant.id, Tyrant.noteKeys, events)

  test("after its holder travels, one warband at the destination dies, and " +
      "the line names whose"):
    val ready = holdingTyrant(_.warbandsAt(coast, rival, 2))
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).forces(coast),
      SiteForces.Occupied(Look(result).forceKind(rival), 1))
    assertEquals(said(done.events), Vector(NoteText.Said("killed",
      s"Killed 1 ${rival.value} warband at ${coast.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("its holder's own warband dies too"):
    val ready = holdingTyrant(_.warbandsAt(coast, actor, 2))
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).forces(coast),
      SiteForces.Occupied(Look(result).forceKind(actor), 1))
    assertEquals(said(done.events), Vector(NoteText.Said("killed",
      s"Killed 1 ${actor.value} warband at ${coast.value}.", covers = false)))

  test("a bandit warband dies, with its own line"):
    val done = accepted(travel(holdingTyrant(_.bandits(coast, 2)), coast))
    assertEquals(Look(Outcomes.ready(done.state)).forces(coast),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(said(done.events), Vector(NoteText.Said("killed.bandits",
      s"Killed 1 bandit warband at ${coast.value}.", covers = false)))

  test("an empty destination, or another player's Tyrant, kills nothing"):
    val emptyCoast = holdingTyrant(_.bandits(coast, 0))
    val rivalHolds = Table.from(board(supply = 7)).adviser(rival, tyrant)
      .warbandsAt(coast, rival, 2).unchecked
    Vector(emptyCoast, rivalHolds).foreach { ready =>
      val done = accepted(travel(ready, coast))
      assert(!PhasePowerHarness.ops(done.events).exists(_.isInstanceOf[Kill]))
      assertEquals(said(done.events), Vector.empty)
    }
```

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, add this test after the Chaos Cult test:

```scala
  test("Tyrant's rule is automatic whatever its flag: it is a must, not a " +
      "modifier"):
    assertEquals(flag("denizen.tyrant"), Some(false))
    assertEquals(oathdigital.gameplay.powers.travel.Tyrant.resolution,
      PowerResolution.Automatic)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.SpecialEnvoySuite oathdigital.gameplay.powers.travel.TyrantSuite"`
Expected: compile failure, `SpecialEnvoy` and `Tyrant` are not found.

- [ ] **Step 3: Write Special Envoy**

Remove `object SpecialEnvoyCard` from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/travel/SpecialEnvoy.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object SpecialEnvoyCard extends Denizen(DenizenId("158"), "Special Envoy", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.special-envoy"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply. After traveling, end your Act Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Special Envoy (card 158), a selected Travel modifier with no cost: Travel
  * spends no Supply, and after traveling the Act Phase ends.
  *
  * It folds after every other Travel effect, above Royal Stables' priority,
  * so it removes whatever Supply payment Royal Stables left, and its
  * `EnterPhase(Rest)` comes after Dragonskin Drum's gain and Tyrant's kill.
  * Its line, "The Act Phase ended.", always follows. The waiver writes no
  * line: the start line's cost span shows it.
  */
final case class SpecialEnvoy private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = SpecialEnvoyCard.id
  def id: PowerId = SpecialEnvoy.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def priority: Int = SpecialEnvoy.Priority
  override def noteKeys: Vector[NoteKey] = Vector(SpecialEnvoy.ended)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      TravelPayments.withoutSupply(operations, ctx.activePlayer) ++ Vector(
        EnterPhase(Phase.Rest),
        Note(id, _ => Some(SpecialEnvoy.ended(
          PowerSourceRef.Card(cardId))))))))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

object SpecialEnvoy:
  val id: PowerId = SpecialEnvoyCard.power.id
  /** Folds after every Travel power, Royal Stables included. */
  val Priority: Int = RoyalStables.Priority + 1
  /** "The Act Phase ended." */
  val ended: NoteKey = NoteKey("ended", Vector(
    NotePart.Text("The Act Phase ended.")))

  def forCatalog(catalog: ExecutableCatalog): SpecialEnvoy =
    new SpecialEnvoy(catalog)
```

- [ ] **Step 4: Write Tyrant**

Remove `object TyrantCard` from `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/travel/Tyrant.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

object TyrantCard extends Denizen(DenizenId("111"), "Tyrant", Suit.Order) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.tyrant"),
    persistent = false, cost = Cost.free,
    text = "You **must** kill a warband _(even your own)_ at the site " +
      "you travel to, if able.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Tyrant (card 111, adviser-only, locked), an automatic rule of a faceup
  * adviser: after its holder travels, one warband at the destination is
  * killed, whoever's, the holder's own and bandits included, when one stands
  * there. Only Travel walks `TravelCost`, so Quick Exit and a Raid's
  * relocation move the pawn without it.
  *
  * The kill follows the pawn's move in the cost node and reads the
  * destination when it runs. The catalog marks the power `persistent: false`,
  * but it is a must, so it keeps the default automatic resolution, as Vow of
  * Obedience does.
  *
  * Its line names whose warband died, as Bandit Chief's does: "Killed {n}
  * {Blue} warband at {site}.", or "Killed {n} bandit warband at {site}." No
  * generic line exists for a kill.
  */
case object Tyrant extends ContributingPower:
  val cardId: DenizenId = TyrantCard.id
  val id: PowerId = TyrantCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  val Warbands: Int = 1
  /** "Killed {n} {Blue} warband at {site}." */
  val killed: NoteKey = NoteKey("killed", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "Killed {n} bandit warband at {site}." */
  val killedBandits: NoteKey = NoteKey("killed.bandits", Vector(
    NotePart.Text("Killed "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit warband at ", " bandit warbands at "),
    NotePart.Arg(1), NotePart.Text(".")))

  override def noteKeys: Vector[NoteKey] = Vector(killed, killedBandits)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      TravelRoute.pawnMove(ctx.operation).fold(operations)(route =>
        operations ++ Vector(kill(route.destination),
          Note(id, killedNote(_, route.destination)))))))

  override def applicable(ctx: PowerCtx): Boolean =
    holds(ctx.state, ctx.activePlayer) &&
      TravelRoute.pawnMove(ctx.operation).exists(_.player == ctx.activePlayer)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

  private def forcesAt(ready: ReadyGame, site: SiteId): SiteForces =
    ready.game.current.map.sites.get(site).map(_.forces)
      .getOrElse(SiteForces.Empty)

  /** One warband of whoever holds `site`, when anyone does. */
  private def kill(site: SiteId): Operation = BuildOps((ready, _) => Right(
    forcesAt(ready, site) match
      case SiteForces.Occupied(kind, _) => Vector[CoreOperation](Kill(
        Piece.Warbands(kind, Warbands), PositionedLocation(Location.Site(site))))
      case SiteForces.Empty => Vector.empty))

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    forcesAt(ready, site) match
      case SiteForces.Occupied(`kind`, count) => count
      case _ => 0

  /** The warbands the step before killed at `site`, and whose. */
  private def killedNote(states: NoteStates, site: SiteId)
      : Option[PowerNote] = states.previous.flatMap { case (before, after) =>
    forcesAt(before, site) match
      case SiteForces.Occupied(kind, count) =>
        val lost = count - held(after, site, kind)
        if lost > 0 then line(after, kind, lost, site) else None
      case SiteForces.Empty => None
  }

  private def line(ready: ReadyGame, kind: ForceKind, lost: Int, site: SiteId)
      : Option[PowerNote] =
    val card = PowerSourceRef.Card(cardId)
    kind match
      case ForceKind.Bandit => Some(killedBandits(card, NoteArg.Number(lost),
        NoteArg.Site(site)))
      case _ => ready.game.current.players.find(seat =>
          PlayerFacts.forceKind(ready, seat.player).toOption.contains(kind))
        .map(owner => killed(card, NoteArg.Number(lost),
          NoteArg.Player(owner.player), NoteArg.Site(site)))
```

- [ ] **Step 5: Register them**

In `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`, change the last line from:

```scala
      Vector(AFastSteed.forCatalog(catalog))
```

to:

```scala
      Vector(AFastSteed.forCatalog(catalog)) ++
      Vector(SpecialEnvoy.forCatalog(catalog)) ++
      Vector(Tyrant)
```

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `SpecialEnvoyCard` and `TyrantCard` to the `gameplay.powers.travel` import.

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, change the end of `modifiers` from:

```scala
    "denizen.awaited-return", "denizen.mounted-library", "denizen.defame")
```

to:

```scala
    "denizen.awaited-return", "denizen.mounted-library", "denizen.defame",
    "denizen.special-envoy")
```

- [ ] **Step 6: Run the suites and the Travel suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.powers.action.WizardSchoolSuite oathdigital.gameplay.powers.action.QuickExitSuite"`
Expected: PASS, 3 tests in `SpecialEnvoySuite`, 4 in `TyrantSuite` and 1 more in `PowerKindsCatalogSuite`.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/SpecialEnvoy.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/Tyrant.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/SpecialEnvoySuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TyrantSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(travel): Special Envoy ends the Act Phase after a free Travel, Tyrant kills at the destination"
```

---

### Task 6: Vow of Beastkin

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala` (retire `VowOfBeastkin`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `VowOfBeastkinCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/VowOfBeastkinSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `MusterSource.matching`, `PowerAccess.pawnSite`, `NoteSupport.warbands`, `OptionRestriction`.
- Produces: `VowOfBeastkin.forCatalog`, `VowOfBeastkin.id`, `VowOfBeastkin.gained`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/VowOfBeastkinSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class VowOfBeastkinSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Beastkin")
  private val power = VowOfBeastkin.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Beast denizen to Muster on. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen, which matches no Beast adviser. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** An Order adviser, so that Wrestlers matches one. */
  private val messenger = CatalogNames.denizen("Messenger")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  /** p1 at Ancient City beside Errand Boy and Wrestlers, with the start's 1
    * favor, 7 Supply and 3 warbands, and no adviser yet. */
  private def musterSite: Table = Table.start
    .denizen(errandBoy, at = home).denizen(wrestlers, at = home)

  private def mustered(ready: ReadyGame, card: DenizenId)
      : EconomyActions.Completed = EconomyActions.muster(rules, ready, p1,
    Vector.empty, DecisionOptionRef.Denizen(card))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("its holder is offered only the cards matching an adviser, and an " +
      "answer naming another is refused"):
    val started = accepted(rules.startWalker(musterSite.adviser(p1, vow).state,
      ActionRef.Muster, p1, Vector.empty))
    assertEquals(PhasePowerHarness.offered(started, p1),
      Some(Vector("denizen" -> errandBoy.value)))
    assert(rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(wrestlers))).isLeft)

  test("every Muster gains one more warband, and the line says so"):
    val ready = musterSite.adviser(p1, vow).ready
    val done = mustered(ready, errandBoy)
    // 1, plus 1 for the Vow itself, a Beast adviser, plus its 1 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)
    assertEquals(said(done.journal.events), Vector(NoteText.Said("gained",
      "Gained 1 more warband.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("a faceup Order adviser lets its holder Muster on an Order card"):
    val ready = musterSite.adviser(p1, vow).adviser(p1, messenger).ready
    val done = mustered(ready, wrestlers)
    // 1, plus 1 for Messenger, plus the Vow's 1 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)

  test("another player's Vow, or a facedown one, changes nothing"):
    val rivalHolds = musterSite.adviser(p2, vow).ready
    val facedown = musterSite.adviser(p1, vow, facedown = true).ready
    Vector(rivalHolds, facedown).foreach { ready =>
      val done = mustered(ready, wrestlers)
      assertEquals(Look(done.result).boardWarbands(p1),
        Look(ready).boardWarbands(p1) + 1)
      assertEquals(said(done.journal.events), Vector.empty)
    }
```

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, add this test after Tyrant's:

```scala
  test("Vow of Beastkin's rule is automatic whatever its flag"):
    assertEquals(flag("denizen.vow-of-beastkin"), Some(false))
    assertEquals(oathdigital.gameplay.powers.economy.VowOfBeastkin
      .forCatalog(catalog).resolution, PowerResolution.Automatic)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.VowOfBeastkinSuite"`
Expected: compile failure, `VowOfBeastkin` is not found.

- [ ] **Step 3: Write Vow of Beastkin**

Remove `object VowOfBeastkinCard` from `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.economy.MusterSource
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object VowOfBeastkinCard extends Denizen(DenizenId("196"), "Vow of Beastkin", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-beastkin"),
    persistent = false, cost = Cost.free,
    text = "You **must** muster on a card matching any of your " +
      "advisers, but you gain one more warband.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Vow of Beastkin (card 196, adviser-only, locked), an automatic rule of a
  * faceup adviser: its holder may Muster only on a card of the same suit as
  * one of their faceup advisers, and every Muster gains one more warband.
  *
  * The source rule is an `OptionRestriction` on the Muster's source
  * decision, so another card is not offered and an answer naming one is
  * refused. The Vow is itself a faceup Beast adviser, so a Beast card always
  * qualifies. The hidden sources write no line.
  *
  * The extra warband joins the Muster's own gain, and the line follows that
  * gain at once: "Gained {n} more warband.", what the gain added beyond its
  * base. An empty warband bank writes nothing. The catalog marks the power
  * `persistent: false`, but it is a must, so it keeps the default automatic
  * resolution.
  */
final case class VowOfBeastkin private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = VowOfBeastkinCard.id
  def id: PowerId = VowOfBeastkin.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  override def noteKeys: Vector[NoteKey] = Vector(VowOfBeastkin.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterSourceSelection -> Vector(OptionRestriction(refused)),
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      withExtra(ctx.activePlayer, operations))))

  override def applicable(ctx: PowerCtx): Boolean =
    holds(ctx.state, ctx.activePlayer)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

  /** Refuses a source whose suit matches none of the holder's faceup
    * advisers. */
  private def refused(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = for
    card <- cardOf(ref)
    suit <- catalog.suitOf(card)
    if MusterSource.matching(catalog, ctx.state, ctx.activePlayer, suit) == 0
    site <- PowerAccess.pawnSite(ctx.state, ctx.activePlayer)
  yield OathViolation.EconomyCardUnavailable(site, card)

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(card) => Some(card)
    case DecisionOptionRef.Edifice(card) => Some(card)
    case _ => None

  /** The Muster's own gain for `actor`, one more, with the line after it. */
  private def withExtra(actor: PlayerId, operations: Vector[Operation])
      : Vector[Operation] =
    val base = operations.indexWhere {
      case Gain.Warbands(player, _, _) => player == actor
      case _ => false
    }
    if base < 0 then operations
    else operations(base) match
      case gain: Gain.Warbands => operations.patch(base, Vector(
        gain.copy(amount = gain.amount + VowOfBeastkin.Warbands),
        Note(id, extraNote(_, actor, gain.amount))), 1)
      case _ => operations

  /** What the gain's step added beyond the Muster's base. */
  private def extraNote(states: NoteStates, actor: PlayerId, base: Int)
      : Option[PowerNote] = for
    step <- states.previous
    more = NoteSupport.warbands(step, actor) - base
    if more > 0
  yield VowOfBeastkin.gained(PowerSourceRef.Card(cardId),
    NoteArg.Number(more))

object VowOfBeastkin:
  val id: PowerId = VowOfBeastkinCard.power.id
  val Warbands: Int = 1
  /** "Gained {n} more warband." */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Plural(0, " more warband.", " more warbands.")))

  def forCatalog(catalog: ExecutableCatalog): VowOfBeastkin =
    new VowOfBeastkin(catalog)
```

- [ ] **Step 4: Register it and retire the stub**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`, add `VowOfBeastkin` to the `gameplay.powers.economy` import, and `Vector(VowOfBeastkin.forCatalog(catalog)) ++` after `Vector(InitiationRite) ++`.

In `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`, delete the line `object VowOfBeastkin extends ReviewedPower(...)` and drop `VowOfBeastkin` from `powers`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `VowOfBeastkinCard` to the `gameplay.powers.economy` import.

- [ ] **Step 5: Run the suite and the Muster suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 4 tests in `VowOfBeastkinSuite` and 1 more in `PowerKindsCatalogSuite`. `RowdyPubSuite`, `VillageIdiotSuite`, `DowntroddenSuite` and `KnightsErrantSuite` pass unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/VowOfBeastkinSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Vow of Beastkin limits Muster to matching cards and gains one more warband"
```

---

### Task 7: Vow of Poverty

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/VowOfPoverty.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/RestPowers.scala` (retire `VowOfPoverty`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `VowOfPovertyCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/VowOfPovertySuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/RestSuite.scala`

**Interfaces:**
- Consumes: `FavorBankChoice.stocked`, `FavorBankChoice.take`, `NoteSupport.favor`, `NoteSupport.bankPaid`, `PlayerFacts.player`, `Restriction`, `MovingMarket`.
- Produces: `case object VowOfPoverty` with `took`, `Taken`, `choiceDecisionId(ready: ReadyGame, player: PlayerId): String`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/VowOfPovertySuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class VowOfPovertySuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Poverty")
  private val home = CatalogNames.site("Ancient City")
  private val giant = CatalogNames.site("Buried Giant")
  /** A plain Order denizen to Trade with. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val market = CatalogNames.denizen("Moving Market")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))
  private val automatic = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  /** Whether p1 could Trade for `resource` with some card at the site now,
    * by the preview a start runs. */
  private def tradeOffered(ready: ReadyGame, resource: TradeResource)
      : Boolean = TradeProcedure.startOptions(catalog, ready, p1, resource,
    automatic).exists(_.outcome.isRight)

  test("its holder is not offered Trade for favor, and may still Trade for " +
      "secrets"):
    // 2 favor pays a Trade for secrets; the start's secret pays one for favor.
    val withoutVow = Table.start.denizen(wrestlers, at = home).favor(p1, 2)
      .bankFavor(Suit.Order, 3)
    val held = withoutVow.adviser(p1, vow).ready
    assert(tradeOffered(withoutVow.ready, TradeResource.Favor))
    assert(!tradeOffered(held, TradeResource.Favor))
    assert(tradeOffered(held, TradeResource.Secret))
    val traded = for
      started <- rules.startWalker(Ready(held), ActionRef.Trade, p1,
        Vector.empty, Vector(DecisionOptionRef.Button("favor")))
      done <- rules.resolveWalker(started.state, p1, TradeProcedure.decisionId,
        ChooseOneAnswer(DecisionOptionRef.Denizen(wrestlers)))
    yield done
    assert(traded.isLeft, traded.toString)

  test("Moving Market's favor is not from Trade, so its holder still gains " +
      "it"):
    val ready = Table.start.denizen(market, at = home).adviser(p1, vow)
      .favor(p1, 2).bankFavor(Suit.Nomad, 3).ready
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      Vector(MovingMarket.id), Vector(DecisionOptionRef.Button("secret"))))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(market))))
    val moved = accepted(rules.resolveWalker(asked.state, p1,
      MovingMarket.decisionId,
      ChooseOneAnswer(DecisionOptionRef.SiteSlot(giant, 0))))
    // The Trade spent the 2 favor, and Moving Market gave 1.
    assertEquals(Look(Outcomes.ready(moved.state)).favor(p1), 1)

  // ---- REST: with no favor, take 2 favor from any one favor bank ----

  private val use = ActionRef.UsePower(VowOfPoverty.id)
  private val source = DecisionOptionRef.Denizen(vow)

  /** p1's Rest with no favor, holding a faceup Vow, with exactly these banks
    * holding favor. */
  private def restingWithNoFavor(stocked: Map[Suit, Int]): ReadyGame =
    Suit.all.foldLeft(Table.start.turn(p1, Phase.Rest).adviser(p1, vow)
      .favor(p1, 0))((table, suit) =>
      table.bankFavor(suit, stocked.getOrElse(suit, 0))).ready

  private def usedRest(ready: ReadyGame): OathTransition = accepted(
    rules.startWalker(Ready(ready), use, p1, Vector.empty, Vector(source)))

  private def usable(ready: ReadyGame): Boolean = PhasePowerProcedure.usable(
    catalog, ready, p1, PhasePowerCatalog.default(catalog), WalkerPowers.empty)
    .exists(_.power.id == VowOfPoverty.id)

  test("REST: with no favor, its holder takes 2 favor from the bank they " +
      "choose, in a line covering the generic one"):
    val ready = restingWithNoFavor(Map(Suit.Arcane -> 3, Suit.Order -> 3))
    val choice = VowOfPoverty.choiceDecisionId(ready, p1)
    val started = usedRest(ready)
    parked.assertParked(started.state, use, choice, p1)
    val taken = accepted(rules.resolveWalker(started.state, p1, choice,
      ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order))))
    val after = Outcomes.ready(taken.state)
    assertEquals(Look(after).bankFavor(Suit.Order), 3 - 2)
    assertEquals(Look(after).bankFavor(Suit.Arcane), 3)
    assertEquals(Look(after).favor(p1), 2)
    assertEquals(NoteText.said(VowOfPoverty, taken.events),
      Vector(NoteText.Said(NoteKey.Used, "Took 2 favor from the Order bank.",
        covers = true)))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      started.events ++ taken.events), after)

  test("REST: a bank holding 1 gives 1 without asking, and a holder with " +
      "favor is not offered it"):
    val after = Outcomes.ready(usedRest(
      restingWithNoFavor(Map(Suit.Hearth -> 1))).state)
    assertEquals(Look(after).favor(p1), 1)
    assertEquals(Look(after).bankFavor(Suit.Hearth), 0)
    val stocked = restingWithNoFavor(Map(Suit.Hearth -> 3))
    assert(usable(stocked))
    assert(!usable(Table.from(stocked).favor(p1, 1).unchecked))
```

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, add this test after Vow of Beastkin's:

```scala
  test("Vow of Poverty's rule is automatic whatever its flag: the flag is " +
      "false because of its REST"):
    assertEquals(flag("denizen.vow-of-poverty"), Some(false))
    assertEquals(oathdigital.gameplay.powers.economy.VowOfPoverty.resolution,
      PowerResolution.Automatic)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.VowOfPovertySuite"`
Expected: compile failure, `VowOfPoverty` is not found.

- [ ] **Step 3: Write Vow of Poverty**

Remove `object VowOfPovertyCard` from `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/economy/VowOfPoverty.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PhasePower, PowerCtx, Restriction}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.gameplay.powers.cardplay.FavorBankChoice
import oathdigital.model._

object VowOfPovertyCard extends Denizen(DenizenId("193"), "Vow of Poverty", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-poverty"),
    persistent = false, cost = Cost.free,
    text = "You **cannot** gain [favor] from Trade. **REST:** If you " +
      "have no [favor], take [favor] [favor] from any one favor " +
      "bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Vow of Poverty (card 193, adviser-only, locked): "You cannot gain favor
  * from Trade. REST: If you have no favor, take 2 favor from any one favor
  * bank."
  *
  * The rule is a `Restriction` on the Trade's gain node, which refuses a gain
  * of favor for a faceup holder. The node exists once the source is
  * answered, and the restriction look-ahead probes each source with its
  * answer, so every source of a Trade for favor is hidden and that Trade is
  * not offered. Favor a Trade modifier grants, such as Moving Market's, is
  * gained at the Trade's root, outside the node. The hidden sources write no
  * line.
  *
  * The REST is a [[PhasePower]], as Vow of Obedience's is, usable while the
  * holder's board holds no favor and some bank holds favor. It takes 2 favor
  * from the bank the holder chooses, or what that bank holds, and writes
  * "Took {n favor} from {the Order bank}.", covering the generic line. The
  * catalog marks the power `persistent: false` because of its REST, so the
  * rule keeps the default automatic resolution.
  */
case object VowOfPoverty extends PhasePower with ContributingPower:
  val cardId: DenizenId = VowOfPovertyCard.id
  val id: PowerId = VowOfPovertyCard.power.id
  /** What the REST takes, or what the chosen bank holds. */
  val Taken: Int = 2
  /** "Took {n favor} from {the Order bank}." */
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Took "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(".")))

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"vow-of-poverty-${ready.game.current.tracks.round}-${player.value}"

  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(took)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Restriction((ctx, _) =>
      Option.when(holds(ctx.state, ctx.activePlayer) && gainsFavor(ctx))(
        OathViolation.NoPlayableOption(ActionRef.Trade.key)))))

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    PlayerFacts.player(ready, player).exists(_.board.favor == 0) &&
      FavorBankChoice.stocked(ready).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = choiceDecisionId(ready, player)
    Right(Sequence(Vector(
      Branch((state, _) => FavorBankChoice.take(state, player, Taken, choice,
        "Vow of Poverty: take 2 favor from a bank")),
      Note(id, tookNote(source, player), covers = true))))

  /** Whether the hooked gain node gives the trader favor. */
  private def gainsFavor(ctx: PowerCtx): Boolean =
    ctx.operation.children.exists {
      case Gain.Favor(player, _, _) => player == ctx.activePlayer
      case _ => false
    }

  /** Whether `player` holds this card as a faceup adviser. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

  /** The favor `player` took from one bank in the step before the note. */
  private def tookNote(source: DecisionOptionRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    amount = NoteSupport.favor(step, player)
    if amount > 0
    bank <- NoteSupport.bankPaid(step)
  yield took(card, NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Bank(bank))
```

- [ ] **Step 4: Register it, retire the stub, and change the Rest filler**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`, add `VowOfPoverty` to the `gameplay.powers.economy` import, and `Vector(VowOfPoverty) ++` after `Vector(VowOfBeastkin.forCatalog(catalog)) ++`.

In `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`, add `import oathdigital.gameplay.powers.economy.VowOfPoverty` after the `cardplay.VowOfObedience` import, and `Vector(VowOfPoverty) ++` after `Vector(VowOfObedience) ++`.

In `src/main/scala/oathdigital/gameplay/powers/RestPowers.scala`, delete the `object VowOfPoverty extends ReviewedPower(...)` definition (all four of its lines), and change `powers` to:

```scala
  val powers: Vector[Power] = Vector(Naysayers, SilverTongue, Insomnia)
```

Then change the doc comment's "League Treaty and Vow of Obedience are audited" to "League Treaty, Vow of Obedience and Vow of Poverty are audited".

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `VowOfPovertyCard` to the `gameplay.powers.economy` import.

In `src/test/scala/oathdigital/gameplay/RestSuite.scala`, change:

```scala
    val relevant = Map("denizen.vow-of-poverty" -> "Vow of Poverty",
      "denizen.naysayers" -> "Naysayers")
```

to:

```scala
    val relevant = Map("denizen.naysayers" -> "Naysayers")
```

and the comment above it from "Insomnia's REST runs as a phase power, so it records no diagnostic." to "Insomnia's and Vow of Poverty's RESTs run as phase powers, so they record no diagnostic."

- [ ] **Step 5: Run the suite and the Rest suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.RestSuite oathdigital.gameplay.powers.cardplay.VowOfObedienceSuite oathdigital.gameplay.powers.rest.*"`
Expected: PASS, 4 tests in `VowOfPovertySuite` and 1 more in `PowerKindsCatalogSuite`.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/VowOfPoverty.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala \
  src/main/scala/oathdigital/gameplay/powers/RestPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/VowOfPovertySuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/RestSuite.scala
git commit -m "feat(economy): Vow of Poverty forbids Trade for favor and takes 2 favor at Rest"
```

---

### Task 8: Gates and docs

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`

- [ ] **Step 1: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. The server count is the baseline plus 40; the frontend count is unchanged.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

If a suite outside this slice fails, it most likely holds one of these cards as filler (plan-time ruling 14), or it picks a card through `SearchFixture.anyPlainDenizensOf`, which now skips the newly implemented cards. Read the failing assertion. Change the filler to another card only if the test is about something else, and say so in the commit message.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:

- In the status paragraph, change:

  ```markdown
  2026-10-01 playtest. **Phase - Catalog batch 4** is in progress: slices
  1a, 1b, 2, 3 and 4 are done. **Phase - Undo** is designed.
  ```

  to:

  ```markdown
  2026-10-01 playtest. **Phase - Catalog batch 4** is complete. **Phase -
  Undo** is designed.
  ```

- Under "### Phase - Catalog batch 4", replace the bullet that starts "- [ ] 5. Modifiers and restrictions:" and its two continuation lines with:

  ```markdown
  Every slice is done.
  ```

- [ ] **Step 3: Make the spec's slice 5 log table exact**

In `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`, under "### Slice 5: modifiers and restrictions", replace the table (its header row through the row "| Tyrant, key `killed` | ...") with:

```markdown
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
```

Then add this paragraph between the table and the "**No line:**" paragraph:

```markdown
Defame names the chosen player even when the actor chose themself, and
Tyrant names whose warband died, the holder's own included, as Bandit Chief
does. No generic line exists for a favor burn, favor given between boards, a
kill, a warband gain or a phase change, so those rows cover nothing. Mounted
Library doubles through a second secret gain. Its line covers only that
gain's generic line, so the Trade's own "Gained {n} secrets" line stays and
"Mounted Library: Gained {n} more secrets." follows it. Tyrant's bandit kill
has its own key, since no note argument names the bandits.
```

- [ ] **Step 4: Record the slice's implementation notes in the spec**

In the same file, under "## Implementation notes from the verification", add these bullets right after the bullet that starts "- **Special Envoy's `EnterPhase`**" (and its continuation line):

```markdown
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
  facedown.
```

- [ ] **Step 5: Check the docs**

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add docs/ROADMAP.md \
  docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md
git commit -m "docs: record catalog batch 4 slice 5"
```

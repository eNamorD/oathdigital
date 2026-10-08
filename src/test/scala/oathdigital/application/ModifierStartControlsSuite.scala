package oathdigital.application

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, Birdsong}
import oathdigital.gameplay.powers.search.NewsFromAfar
import oathdigital.gameplay.powers.travel.{Tents, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** A start control counts the modifiers its player
  * could select: an action that only a modifier makes affordable is still
  * offered, and a modifier the player cannot pay for unlocks nothing. */
class ModifierStartControlsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)
  private val legal = new LegalActionProjector(catalog, presentation,
    new WalkerDecisionProjector(catalog, presentation))

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  private def controls(ready: ReadyGame, viewer: PlayerId): Vector[String] =
    legal.project(ScopedProjectionContext(ready, Some(viewer))).controls

  /** `viewer` starts `action` at `ready` with `modifiers`; fails the test
    * if the start is refused. */
  private def assertStarts(ready: ReadyGame, viewer: PlayerId,
      action: ActionRef, modifiers: Vector[PowerId])(using munit.Location)
      : Unit =
    accepted(rules.startWalker(OathState.Ready(ready), action, viewer,
      modifiers))
    ()

  /** Why `viewer` may not start `action` at `ready` with `modifiers`; fails
    * the test if the start is accepted. */
  private def refusal(ready: ReadyGame, viewer: PlayerId, action: ActionRef,
      modifiers: Vector[PowerId])(using munit.Location): OathViolation =
    rejected(rules.startWalker(OathState.Ready(ready), action, viewer,
      modifiers))

  /** The refusal of a start that spends `cost` Supply from an empty track. */
  private def noSupplyFor(cost: Int): OathViolation =
    OathViolation.CoreOperationRejected("insufficient-supply",
      s"a supply spend of $cost exceeds the 0 available")

  // ---- Travel ----

  import TravelFixture.board
  import oathdigital.gameplay.powers.PowerFixture.actor

  /** The actor at the Cradle's plains with no Supply, holding `favor`. */
  private def broke(favor: Int, tents: Boolean): ReadyGame =
    val table = Table.from(board(supply = 0)).favor(actor, favor)
    (if tents then table.adviser(actor, CatalogNames.denizen("Tents"))
      else table).unchecked

  test("Travel is offered at 0 Supply when Tents would make it free, and " +
      "the start with Tents is accepted"):
    val ready = broke(favor = 1, tents = true)
    assert(controls(ready, actor).contains("beginTravel"))
    assertStarts(ready, actor, ActionRef.Travel, Vector(Tents.id))
    assertEquals(refusal(ready, actor, ActionRef.Travel, Vector.empty),
      noSupplyFor(1))

  test("without a modifier that waives it, Travel at 0 Supply is not offered"):
    assert(!controls(broke(favor = 1, tents = false), actor)
      .contains("beginTravel"))

  test("Tents with no favor to place on it offers no Travel at 0 Supply"):
    assert(!controls(broke(favor = 0, tents = true), actor)
      .contains("beginTravel"))

  // ---- Muster ----

  /** p1 at Ancient City, which holds Errand Boy, a beast card to muster on,
    * with 4 favor and no Supply. */
  private def beastMuster(playmates: Boolean): ReadyGame =
    val table = Table.start.pawn(p1, at = "Ancient City")
      .denizen(CatalogNames.denizen("Errand Boy"), at = "Ancient City")
      .favor(p1, 4).supply(p1, 0)
    (if playmates then table.adviser(p1,
      CatalogNames.denizen("Animal Playmates")) else table).ready

  test("Muster is offered at 0 Supply when Animal Playmates waives it, and " +
      "the start with it is accepted"):
    val ready = beastMuster(playmates = true)
    assert(controls(ready, p1).contains("beginMuster"))
    assertStarts(ready, p1, ActionRef.Muster, Vector(AnimalPlaymates.id))

  test("without the waiver, Muster at 0 Supply is not offered"):
    assert(!controls(beastMuster(playmates = false), p1)
      .contains("beginMuster"))

  // ---- Trade ----

  /** p1 at Ancient City, which holds Errand Boy, a beast card to trade with,
    * with 4 favor, 2 faceup secrets and no Supply. */
  private def beastTrade(birdsong: Boolean): ReadyGame =
    val table = Table.start.pawn(p1, at = "Ancient City")
      .denizen(CatalogNames.denizen("Errand Boy"), at = "Ancient City")
      .favor(p1, 4).secrets(p1, faceUp = 2).supply(p1, 0)
    (if birdsong then table.adviser(p1, CatalogNames.denizen("Birdsong"))
      else table).ready

  test("Trade is offered at 0 Supply when Birdsong waives it, and the start " +
      "with it is accepted"):
    val ready = beastTrade(birdsong = true)
    assert(controls(ready, p1).contains("beginTradeFavor"))
    accepted(rules.startWalker(OathState.Ready(ready), ActionRef.Trade, p1,
      Vector(Birdsong.id), Vector(DecisionOptionRef.Button("favor"))))
    ()

  test("without the waiver, Trade at 0 Supply is not offered"):
    assert(!controls(beastTrade(birdsong = false), p1)
      .contains("beginTradeFavor"))

  // ---- Search ----

  /** p1 with no Supply, holding `favor`, and News from Afar faceup when
    * `news`. */
  private def brokeSearcher(news: Boolean, favor: Int): ReadyGame =
    val table = Table.start.supply(p1, 0).favor(p1, favor)
    (if news then table.adviser(p1, CatalogNames.denizen("News from Afar"))
      else table).ready

  test("Search is offered when News from Afar would make it free, and the " +
      "start with it is accepted"):
    val ready = brokeSearcher(news = true, favor = 2)
    assert(controls(ready, p1).contains("beginSearch"))
    assertStarts(ready, p1, ActionRef.Search, Vector(NewsFromAfar.id))
    assertEquals(refusal(ready, p1, ActionRef.Search, Vector.empty),
      noSupplyFor(2))

  test("News from Afar with too little favor to pay offers no Search"):
    assert(!controls(brokeSearcher(news = true, favor = 1), p1)
      .contains("beginSearch"))
    assert(!controls(brokeSearcher(news = false, favor = 2), p1)
      .contains("beginSearch"))

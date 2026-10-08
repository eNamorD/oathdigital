package oathdigital.application

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.economy.AnimalPlaymates
import oathdigital.gameplay.powers.travel.{Tents, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** A start control counts the modifiers its player could select: an action
  * that only a modifier makes affordable is still offered, and a modifier the
  * player cannot pay for unlocks nothing. */
class ModifierStartControlsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)
  private val legal = new LegalActionProjector(catalog, presentation,
    new WalkerDecisionProjector(catalog, presentation))

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  private def controls(ready: ReadyGame, viewer: PlayerId): Vector[String] =
    legal.project(ScopedProjectionContext(ready, Some(viewer))).controls

  /** Whether `viewer` may start `action` at `ready` with `modifiers`. */
  private def starts(ready: ReadyGame, viewer: PlayerId, action: ActionRef,
      modifiers: Vector[PowerId]): Boolean =
    rules.startWalker(OathState.Ready(ready), action, viewer, modifiers).isRight

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
    assert(starts(ready, actor, ActionRef.Travel, Vector(Tents.id)))
    assert(!starts(ready, actor, ActionRef.Travel, Vector.empty))

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
    assert(starts(ready, p1, ActionRef.Muster, Vector(AnimalPlaymates.id)))

  test("without the waiver, Muster at 0 Supply is not offered"):
    assert(!controls(beastMuster(playmates = false), p1)
      .contains("beginMuster"))

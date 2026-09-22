package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._

/** Take Wealth's once-per-turn-per-site limit, proved against contribution
  * collection before Wake owns a walker tree (batch-1 Task 6). The rule is
  * stated as a `Restriction` at `PowerWindow.WakeTakeWealth` reading
  * `TurnState.usedPowers` through the state every contribution already sees,
  * which is what keeps that tracking outside the walker: no engine code learns
  * a "power was used" concept and no field joins `PowerCtx`.
  *
  * The site comes from the actor's pawn site, the way the retired
  * `TakeWealthRules` derived it, and deliberately NOT from the tree the
  * restriction is handed. Task 7's tree does turn out to carry a concrete
  * `Take` naming the site, so reading it would work today; it is not read
  * because a restriction that depends on a tree's shape is one tree edit away
  * from matching nothing, and a restriction that matches nothing is
  * indistinguishable from a correct one in every green assertion.
  */
class TakeWealthPowerSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val baseReady = FirstGameSetupFixture.execute()._1 match {
    case OathState.Ready(ready) => ready
    case other => fail(s"expected Ready state, got $other")
  }
  private val actor = baseReady.game.current.turn.activePlayer
  private val powers: Vector[ContributingPower] = Vector(TakeWealthLimit)

  private val inPlay = baseReady.game.current.map.inPlay.sortBy(_.value)
  private val site = inPlay.headOption.getOrElse(fail("no site in play"))
  private val otherSite = inPlay.lift(1).getOrElse(fail("one site in play"))

  /** The pawn stands at `pawn` and this turn has already used `used`. */
  private def ready(pawn: Option[SiteId],
      used: Set[PowerUseRef] = Set.empty): ReadyGame =
    baseReady.updateCurrent(_.copy(
        turn = baseReady.game.current.turn.copy(usedPowers = used),
        players = baseReady.game.current.players.map { player =>
          if (player.player == actor) player.copy(pawnSite = pawn)
          else player
        }))

  /** The turn advance, as `Rest` performs it: a whole new `TurnState`, which
    * is why `usedPowers` is turn-scoped without anything clearing it.
    */
  private def advanceTurn(state: ReadyGame): ReadyGame =
    state.updateCurrent(_.copy(
      turn = TurnState(actor, Phase.Wake, Set.empty)))

  /** Task 7's root shape: the take, under the Wake take-wealth window. */
  private def takeNode(from: SiteId): Sequence =
    Sequence(Vector(Take(Piece.Favor(1), actor, Location.Site(from),
      Location.PlayArea(actor))), Some(PowerWindow.WakeTakeWealth))

  private def ctx(state: ReadyGame, window: PowerWindow, node: Operation,
      power: ContributingPower): PowerCtx =
    PowerCtx(state, actor, power.source, window, Vector.empty, node)

  private def violation(state: ReadyGame, node: Operation,
      window: PowerWindow = PowerWindow.WakeTakeWealth): Option[OathViolation] = {
    val byId = powers.map(power => power.id -> power).toMap
    ContributionCollector.gather(window, powers, ctx(state, window, node, _))
      .restrictions.flatMap { case (id, restriction) =>
        restriction.fn(ctx(state, window, node, byId(id)), node)
      }.headOption
  }

  test("the first take at a site this turn is unrestricted") {
    assertEquals(violation(ready(Some(site)), takeNode(site)), None)
  }

  test("a second take at the same site this turn is blocked") {
    val used = TakeWealthLimit.useRef(site)
    assertEquals(violation(ready(Some(site), Set(used)), takeNode(site)),
      Some(OathViolation.PowerAlreadyUsed(used)))
  }

  test("a take at another site the same turn is unrestricted") {
    assertEquals(violation(ready(Some(otherSite),
      Set(TakeWealthLimit.useRef(site))), takeNode(otherSite)), None)
  }

  test("the limit lifts when the turn advances") {
    // One state, one advance applied to it. Asserting the block first is what
    // keeps this from passing for the same reason the first test does; the
    // advance is modelled on `Rest`'s rather than driven through a whole
    // Rest, which the plan puts at the end-to-end level instead.
    val blocked = ready(Some(site), Set(TakeWealthLimit.useRef(site)))
    assert(violation(blocked, takeNode(site)).isDefined,
      "precondition: the site must be blocked before the turn advances")
    assertEquals(violation(advanceTurn(blocked), takeNode(site)), None)
  }

  test("a missing pawn site leaves the limit silent") {
    // Failing open here is deliberate: `PawnSiteMissing` is the action's own
    // start gate, and a restriction that invented a second spelling for it
    // would report the wrong reason for the rejection.
    assertEquals(violation(ready(None, Set(TakeWealthLimit.useRef(site))),
      takeNode(site)), None)
  }

  test("the limit speaks at no window other than Wake take-wealth") {
    // A restriction gathered anywhere else would reject unrelated actions the
    // moment their tree shares a window with this one.
    assertEquals(TakeWealthLimit.contributions.keySet,
      Set[PowerWindow](PowerWindow.WakeTakeWealth))
    assertEquals(violation(ready(Some(site), Set(TakeWealthLimit.useRef(site))),
      takeNode(site), PowerWindow.TravelCost), None)
  }

  test("the use ref is the one the procedure records") {
    // The read side here and the write side `TakeWealthProcedure` declares
    // must name the same `PowerUseRef` or the limit never fires. Task 6
    // pinned this against the legacy `Wake.takeWealthPower`; Task 7 deleted
    // that object, so the literal is what is left to pin it -- and the
    // procedure builds its own record through `useRef`, never a second
    // literal, which is what keeps the two sides from drifting.
    assertEquals(TakeWealthLimit.useRef(site), PowerUseRef(PowerTiming.Wake,
      PowerSourceRef.Site(site), PowerId("site.take-wealth")))
  }

  test("the walker catalog registers the limit") {
    assert(WalkerPowerCatalog.default(catalog).powers.map(_.id)
      .contains(TakeWealthLimit.id))
  }
}

package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PlayerFacts, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ProcedureWalker
import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.model._

class GarrisonSuite extends munit.FunSuite with WalkerRecordedOpsReducer {
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = Garrison.forCatalog(catalog).get
  private val card = power.cardId
  private val kind = PlayerFacts.forceKind(base, actor).toOption.get

  /** The actor rules exactly `counts.size` sites, holding `counts` warbands
    * each. Every other site is emptied. Returns the state and the sites in
    * value order.
    */
  private def ruling(counts: Vector[Int], onBoard: Int)
      : (ReadyGame, Vector[SiteId]) = {
    val current = base.game.current
    val sites = current.map.inPlay.toVector.sortBy(_.value).take(counts.size)
    val cleared = current.map.sites.map { case (id, site) =>
      id -> site.copy(forces = SiteForces.Empty) }
    val ruled = sites.zip(counts).foldLeft(cleared) { case (all, (id, n)) =>
      all.updated(id, all(id).copy(forces = SiteForces.Occupied(kind, n))) }
    val staged = base.updateCurrent(_.copy(map = current.map.copy(sites = ruled)))
    (withBoard(asAdviser(staged, card))(_.copy(warbands = onBoard)), sites)
  }

  private def forcesAt(ready: ReadyGame, site: SiteId): SiteForces =
    ready.game.current.map.sites(site).forces

  test("Garrison is in the default walker catalog") {
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
  }

  test("it gains one warband per ruled site and puts one on each") {
    val (ready, sites) = ruling(Vector(1, 1, 1), onBoard = 5)
    val done = finished(play(ready, power, card))
    sites.foreach(site => assertEquals(forcesAt(done.treeless, site),
      SiteForces.Occupied(kind, 2)))
    assertEquals(player(done.treeless).board.warbands, 5)
    assertEquals(replayed(ready, done.events), done.treeless)
  }

  test("a player who rules no site gains and places nothing") {
    val (ready, _) = ruling(Vector.empty, onBoard = 5)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(player(done.treeless).board.warbands, 5)
  }

  test("a short bank leaves a short board, and the player chooses the sites") {
    // 13 warbands sit at the three ruled sites and none on the board, so the
    // bank holds one and the gain is capped at one.
    val (ready, sites) = ruling(Vector(4, 4, 5), onBoard = 0)
    assertEquals(warbandBank(ready, kind), 1)
    val first = parked(play(ready, power, card))
    val atChoice = foldRecordedOps(ready, first.events, "gain did not replay")
    assertEquals(player(atChoice).board.warbands, 1)
    val decide = ProcedureWalker.parkedDecide(atChoice, hook(card), first.tree,
      powers(power)).get
    assertEquals(decide.decisionId, Garrison.decisionId)
    assertEquals(decide.owner, actor)
    assertEquals(decide.query, DecisionQuery.ChooseMany(1, 1, sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))),
      decide.query.heading))

    val chosen = sites(1)
    val done = finished(ProcedureWalker.resolve(atChoice, hook(card),
      first.tree, Answered(Garrison.decisionId, DecisionAnswer.ChooseManyAnswer(
        Vector(DecisionOptionRef.Site(chosen))), actor), powers(power)))
    assertEquals(forcesAt(done.treeless, chosen), SiteForces.Occupied(kind, 5))
    assertEquals(forcesAt(done.treeless, sites.head), SiteForces.Occupied(kind, 4))
    assertEquals(forcesAt(done.treeless, sites(2)), SiteForces.Occupied(kind, 5))
    assertEquals(player(done.treeless).board.warbands, 0)
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
  }

  test("an empty board and an empty bank ask nothing") {
    val (ready, _) = ruling(Vector(5, 5, 4), onBoard = 0)
    assertEquals(warbandBank(ready, kind), 0)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)
  }

  test("a choice naming an unruled site is rejected") {
    val (ready, sites) = ruling(Vector(4, 4, 5), onBoard = 0)
    val first = parked(play(ready, power, card))
    val atChoice = foldRecordedOps(ready, first.events, "gain did not replay")
    val unruled = base.game.current.map.inPlay.toVector
      .find(!sites.contains(_)).get
    assert(ProcedureWalker.resolve(atChoice, hook(card), first.tree,
      Answered(Garrison.decisionId, DecisionAnswer.ChooseManyAnswer(
        Vector(DecisionOptionRef.Site(unruled))), actor), powers(power)).isLeft)
  }
}

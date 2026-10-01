package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class QuickExitSuite extends munit.FunSuite:
  import TargetsFixture._

  private val exit = CatalogNames.denizen("Quick Exit")
  private val source = DecisionOptionRef.Denizen(exit)
  private val home = Table.homeOf(p1)
  private val away = Table.homeOf(p3)

  /** p1's Act holding Quick Exit with `secrets` faceup secrets. p2 stands at
    * p1's site and p3 at its own. */
  private def staged(secrets: Int = 1): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, exit).secrets(p1, secrets)
      .pawn(p2, at = home).ready

  private def target(t: OathTransition, who: PlayerId): OathTransition =
    answer(t, p1, QuickExit.targetDecisionId,
      pick(DecisionOptionRef.Player(who))).toOption.get

  test("it places a secret and offers only the other pawns at the site"):
    val t = use(staged(), QuickExit, source).toOption.get
    assert(awaits(t, QuickExit.targetDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(exit), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("player" -> p2.value)))

  test("it then offers every other site in play"):
    val ready = staged()
    val chosen = target(use(ready, QuickExit, source).toOption.get, p2)
    assert(awaits(chosen, QuickExit.siteDecisionId),
      parked.parkedDecision(chosen.state).toString)
    assertEquals(offered(chosen, p1), Some(ready.game.current.map.inPlay
      .filter(_ != home).map(site => "site" -> site.value)))

  test("the pawn is placed at the chosen site, and the line says where"):
    val ready = staged()
    val t = use(ready, QuickExit, source).toOption.get
    val chosen = target(t, p2)
    val done = answer(chosen, p1, QuickExit.siteDecisionId,
      pick(DecisionOptionRef.Site(away))).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).pawn(p2), away)
    assertEquals(Look(after(done)).pawn(p1), home)
    val events = t.events ++ chosen.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))
    assertEquals(NoteText.said(QuickExit, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Placed ${p2.value} at ${away.value}.", covers = false)))

  test("with no other pawn at the site, the cost stays paid and the line " +
      "says so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, exit)
      .secrets(p1, 1).ready
    val done = use(ready, QuickExit, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(exit), Tokens(0, 1))
    assertEquals(NoteText.said(QuickExit, done.events), Vector(NoteText.Said(
      "used.none", s"No other pawn was at ${home.value}.", covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0)
    assert(!usableNow(broke).exists(_.power.id == QuickExit.id))
    assert(use(broke, QuickExit, source).isLeft)

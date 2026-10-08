package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class QuickExitSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val exit = CatalogNames.denizen("Quick Exit")
  private val source = DecisionOptionRef.Denizen(exit)
  private val home = CatalogNames.site("Ancient City")
  private val away = CatalogNames.site("Buried Giant")

  /** p1's Act at Ancient City holding Quick Exit with `secrets` faceup
    * secrets. p2 stands at Ancient City too and p3 at Buried Giant. */
  private def staged(secrets: Int = 1): ReadyGame =
    Table.start.turn(p1, Phase.Act).pawn(p1, home).adviser(p1, exit)
      .secrets(p1, secrets).pawn(p2, at = home).pawn(p3, at = away).ready

  private def target(t: OathTransition, who: PlayerId): OathTransition =
    accepted(answer(t, p1, QuickExit.targetDecisionId,
      pick(DecisionOptionRef.Player(who))))

  test("it places a secret and offers only the other pawns at the site"):
    val t = accepted(use(staged(), QuickExit, source))
    assert(awaits(t, QuickExit.targetDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(exit), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("player" -> p2.value)))

  test("it then offers every other site in play"):
    val ready = staged()
    val chosen = target(accepted(use(ready, QuickExit, source)), p2)
    assert(awaits(chosen, QuickExit.siteDecisionId),
      parked.parkedDecision(chosen.state).toString)
    assertEquals(offered(chosen, p1), Some(ready.game.current.map.inPlay
      .filter(_ != home).map(site => "site" -> site.value)))

  test("the pawn is placed at the chosen site, and the line says where"):
    val ready = staged()
    val t = accepted(use(ready, QuickExit, source))
    val chosen = target(t, p2)
    val done = accepted(answer(chosen, p1, QuickExit.siteDecisionId,
      pick(DecisionOptionRef.Site(away))))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).pawn(p2), away)
    assertEquals(Look(after(done)).pawn(p1), home)
    val events = t.events ++ chosen.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)
    assertEquals(NoteText.said(QuickExit, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Placed ${p2.value} at ${away.value}.", covers = false)))

  test("with no other pawn at the site, the cost stays paid and the line " +
      "says so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, home)
      .pawn(p2, at = away).pawn(p3, at = away).adviser(p1, exit)
      .secrets(p1, 1).ready
    val done = accepted(use(ready, QuickExit, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(exit), Tokens(0, 1))
    assertEquals(NoteText.said(QuickExit, done.events), Vector(NoteText.Said(
      "used.none", s"No other pawn was at ${home.value}.", covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0)
    assert(!usableNow(broke).exists(_.power.id == QuickExit.id))
    assertEquals(use(broke, QuickExit, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))

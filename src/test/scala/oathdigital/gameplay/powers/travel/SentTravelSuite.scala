package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureDequeued, QueuedStart}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{Look, Outcomes}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** A Travel a power sends a player on: to the site its entry names, with
  * no destination question, and past the sender's own waivable
  * restrictions (catalog batch 8, slice 15, rulings 3 and 6). */
class SentTravelSuite extends munit.FunSuite:
  import TravelFixture._

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val palanquin = PowerSourceRef.Card(DenizenId("107"))
  private val dunes = DecisionOptionRef.Site(provincePlains)
  private val passBlocked = OathViolation.TravelPassBlocked(pass,
    provincePlains)

  /** p2's Travel to `site`, sent by p1 with Palanquin as its source. */
  private def p2SentTo(site: SiteId): QueuedProcedure =
    QueuedProcedure(p2, ActionRef.Travel, Vector(DecisionOptionRef.Site(site)),
      Some(palanquin), QueueKind.Run, sender = Some(p1))

  /** The board with p2 beside p1 at the Cradle's plains. The Narrow Pass is
    * held by `passHolder`'s warband, or by its bandits without one. */
  private def p2BesideP1(passHolder: Option[PlayerId] = None): ReadyGame =
    on(board())(table => passHolder.fold(table)(holder =>
      table.warbandsAt(pass, holder, 1)).pawn(p2, cradlePlains))

  /** `entry` started on `ready`, as the driver starts a dequeued `Run`. */
  private def startedFromTheQueue(ready: ReadyGame, entry: QueuedProcedure)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(QueuedStart.startingFrom(ready, entry)),
      entry.procedure, entry.actor, entry.startArgs)

  private def startRefusal(ready: ReadyGame, entry: QueuedProcedure)
      : Option[OathViolation] =
    QueuedStart.refusal(catalog, ready, entry, powers(Vector.empty),
      PhasePowerCatalog.default(catalog))

  /** `ready` remembering p3's grant of Dunes as p2's destination. */
  private def p3GrantedDunesToP2(ready: ReadyGame): ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(
      ConsentKey(NarrowPassSite.pass, p2,
        TravelProcedure.destinationDecisionId, dunes) ->
        ConsentAnswer.Granted.by(p3)),
      turn = Some(ConsentTurn.of(ready))))

  test("a sent Travel goes to its site with no destination question, for " +
      "no Supply"):
    val ready = p2BesideP1()
    val done = accepted(startedFromTheQueue(ready, p2SentTo(coast)))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.pawn(p2), coast)
    assertEquals(after.supply(p2), Look(ready).supply(p2))
    assertEquals(after.active, p1)

  test("a site start argument without a matching queued entry is refused"):
    val refused = rejected(rules.startWalker(Ready(p2BesideP1()),
      ActionRef.Travel, p1, Vector(DecisionOptionRef.Site(coast))))
    assertEquals(refused, OathViolation.InvalidEventOrder(
      "travel takes no start selection but a power's queued destination"))

  test("a bandit-held Narrow Pass refuses a sent Travel into its region"):
    assertEquals(startRefusal(p2BesideP1(), p2SentTo(provincePlains)),
      Some(passBlocked))

  test("the Pass ruler's grant of the queued destination lets it through"):
    val ready = p2BesideP1(passHolder = Some(p3))
    assertEquals(startRefusal(ready, p2SentTo(provincePlains)),
      Some(passBlocked))
    val granted = p3GrantedDunesToP2(ready)
    assertEquals(startRefusal(granted, p2SentTo(provincePlains)), None)
    val done = accepted(startedFromTheQueue(granted, p2SentTo(provincePlains)))
    assertEquals(Look(Outcomes.ready(done.state)).pawn(p2), provincePlains)

  test("the sender's own Narrow Pass does not block it, another's does"):
    val ready = p2BesideP1(passHolder = Some(p1))
    assertEquals(startRefusal(ready, p2SentTo(provincePlains)), None)
    assertEquals(startRefusal(ready,
      p2SentTo(provincePlains).copy(sender = Some(p3))), Some(passBlocked))

  test("a sent entry's sender survives the wire"):
    assertWireRoundTrips(Vector(ProcedureDequeued(p2SentTo(coast))))

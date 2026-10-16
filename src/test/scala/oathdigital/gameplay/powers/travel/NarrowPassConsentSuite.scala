package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.{ModifierPlay, OathRules}
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Restriction}
import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PlacementPicks}
import oathdigital.testkit.Table.p2
import oathdigital.testkit.Unwrap.{accepted, present, rejected}

/** Which Travel destinations the Narrow Pass makes askable (consent
  * design, rulings 4 to 8 and 12, "Walker and powers"). The fixture board's
  * Pass holds bandits; `ruled` hands it to p2. */
class NarrowPassConsentSuite extends munit.FunSuite:
  import PowerFixture.actor
  import TravelFixture._

  private val ruled = on(board())(_.warbandsAt(pass, p2, 1))
  private val dunes = DecisionOptionRef.Site(provincePlains)
  private val passPower = NarrowPassSite.pass
  private val destination = TravelProcedure.destinationDecisionId
  private val key = ConsentKey(passPower, actor, destination, dunes)

  /** `ready` remembering `answer` for Dunes, for `turn`. */
  private def remembering(ready: ReadyGame, answer: ConsentAnswer,
      turn: ConsentTurn): ReadyGame = ready.copy(consent = ConsentState(
    answers = Map(key -> answer.by(p2)), turn = Some(turn)))

  /** A restriction nobody can waive, on Dunes too. */
  private object DunesBarrier extends ContributingPower:
    val id: PowerId = PowerId("test.dunes-barrier")
    val source: RuleSourceRef = RuleSourceRef.Site(provincePlains)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.TravelActionEligibility -> Vector(Restriction(
        (ctx, _) => TravelRoute.pawnMove(ctx.operation)
          .filter(_.destination == provincePlains)
          .map(_ => OathViolation.InvalidEventOrder("the barrier")),
        (_, _) => Some(NarrowPassSitePower.noTravel(
          PowerSourceRef.Site(provincePlains))))))

  /** Dunes as the parked destination decision offers it, once `actor`
    * starts a Travel with `modifiers` under the catalog's powers and
    * `extra`. */
  private def dunesOffered(ready: ReadyGame,
      modifiers: Vector[PowerId] = Vector.empty,
      extra: Vector[ContributingPower] = Vector.empty)(using munit.Location)
      : DecisionOption =
    val powers = WalkerPowers(WalkerPowerCatalog.default(catalog).powers ++
      extra)
    val started = accepted(ModifierPlay.started(
      new OathRules(catalog, walkerPowerCatalog = powers), Ready(ready),
      ActionRef.Travel, actor, PowerWindow.TravelModifierSelection,
      modifiers, powers))
    present(PlacementPicks.parkedOptions(started.state,
      WalkerPowers.selected(powers, modifiers)).find(_.ref == dunes),
      "Dunes among the destinations")

  private def assertNotedBy(option: DecisionOption, noteKey: String)(
      using munit.Location): Unit = option match
    case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
      assertEquals(note.key, noteKey)
    case other => fail(s"expected an unavailable option noted $noteKey, got $other")

  private def askable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case _ => false

  test("a destination only a player-ruled Pass blocks is askable of its ruler"):
    dunesOffered(ruled) match
      case DecisionOption.Unavailable(_, OptionReason.Askable(power, note,
          asks, _)) =>
        assertEquals(power, passPower)
        assertEquals(note, NarrowPassSitePower.noTravelConsent(
          PowerSourceRef.Site(NarrowPassSite.id)))
        assertEquals(asks, Vector(ConsentAsk(p2, Vector(passPower))))
      case other => fail(s"expected an askable option, got $other")

  test("a Pass the bandits hold blocks with its ordinary reason: bandits " +
      "never consent"):
    assertNotedBy(dunesOffered(board()), "no-travel")

  test("a Pass the traveller rules blocks nothing"):
    assert(dunesOffered(on(board())(_.warbandsAt(pass, actor, 1))).available)

  test("Forest Paths drops the Pass before anyone is asked"):
    val ready = on(ruled)(_.adviser(actor, CatalogNames.denizen("Forest Paths"))
      .favor(actor, 1).denizen(CatalogNames.denizen("Errand Boy"),
        at = provincePlains))
    assert(dunesOffered(ready, Vector(ForestPaths.id)).available)

  test("a block nobody can waive beside the Pass leaves the destination " +
      "unavailable, not askable"):
    dunesOffered(ruled, extra = Vector(DunesBarrier)) match
      case DecisionOption.Unavailable(_, _: OptionReason.Noted) => ()
      case other => fail(s"expected a plain unavailable option, got $other")

  test("a destination a grant would still not open is not askable: the " +
      "traveller cannot pay for it"):
    // One Supply pays for the Cradle's coast, not for a journey to the
    // Provinces.
    val poor = on(board(supply = 1))(_.warbandsAt(pass, p2, 1))
    assertNotedBy(dunesOffered(poor), "no-travel")

  test("a grant remembered this turn opens the destination, and the Travel " +
      "goes there"):
    val granted = remembering(ruled, ConsentAnswer.Granted,
      ConsentTurn.of(ruled))
    assert(dunesOffered(granted).available)
    val result = Outcomes.ready(accepted(travel(granted,
      provincePlains)).state)
    assertEquals(Look(result).player(actor).pawnSite, Some(provincePlains))

  test("a refusal remembered this turn leaves the ordinary reason"):
    val refused = remembering(ruled, ConsentAnswer.Refused,
      ConsentTurn.of(ruled))
    assertNotedBy(dunesOffered(refused), "no-travel")
    assertEquals(rejected(travel(refused, provincePlains)),
      destinationNotOffered)

  test("a grant from another turn counts for nothing"):
    val stale = remembering(ruled, ConsentAnswer.Granted,
      ConsentTurn(ruled.game.current.tracks.round, p2))
    assert(askable(dunesOffered(stale)))

  test("the asks of an askable destination, for its owner only"):
    val started = accepted(rules.startWalker(Ready(ruled), ActionRef.Travel,
      actor, Vector.empty)).state
    assertEquals(rules.consentAsks(started, actor, destination, dunes),
      Right(Vector(ConsentAsk(p2, Vector(passPower)))))
    val coastRef = DecisionOptionRef.Site(coast)
    assertEquals(rules.consentAsks(started, actor, destination, coastRef),
      Left(OathViolation.ConsentUnavailable(
        s"${coastRef.wireId} is not an option to ask consent for")))
    assertEquals(rules.consentAsks(started, p2, destination, dunes),
      Left(OathViolation.ConsentUnavailable(
        s"${dunes.wireId} is not an option to ask consent for")))
    assertEquals(rules.consentAsks(started, actor, "travel.elsewhere", dunes),
      Left(OathViolation.ConsentUnavailable(
        s"${dunes.wireId} is not an option to ask consent for")))

package oathdigital.application

import oathdigital.application.gamelog.LogScripts.{formatter, texts}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.poweruse.{CityWall, ForestCouncil}
import oathdigital.gameplay.powers.wake.{Hunger, HungerCard}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, Outcomes,
  ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Hunger refused by City Wall and Forest Council: every ruler is asked
  * at once, and the first deactivation settles it (automatic power
  * consent design, rulings 6 and 9; slice 2). */
class HungerConsentServiceSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val projector = new GameProjector(catalog)
  private val peaks = CatalogNames.site("Broken Peaks")
  private val woods = CatalogNames.site("Deep Woods")

  /** HungerSuite's board: p2 holds Hunger faceup and Wrestlers, and p3
    * holds Rain Boots, both at Broken Peaks, in p1's Act. City Wall
    * stands at Broken Peaks, ruled by `peaksRuler`, and Forest Council at
    * Deep Woods, ruled by `woodsRuler`. */
  private def hungerUnder(peaksRuler: PlayerId, woodsRuler: PlayerId)
      : ReadyGame =
    Table.start.adviser(p2, HungerCard.id).adviser(p2, "Wrestlers")
      .adviser(p3, "Rain Boots").pawn(p2, at = peaks).pawn(p3, at = peaks)
      .denizen(CatalogNames.denizen("City Wall"), at = peaks)
      .warbandsAt(peaks, peaksRuler, 1)
      .denizen(CatalogNames.denizen("Forest Council"), at = woods)
      .warbandsAt(woods, woodsRuler, 1).ready

  /** p1 begins Rest, and p2's forced Wake reaches Hunger. */
  private def rested(service: GameApplicationService)(
      using munit.Location): GameAccepted =
    accepted(service.handle(gameId, 0L,
      MenuPlay.starting(p1, PhaseTransitionRef.BeginRest)))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice, granter: PlayerId)(using munit.Location)
      : GameAccepted = accepted(service.handle(gameId, at.nextSequence,
    GameCommand.AnswerConsent(granter, choice)))

  private def readyAfter(at: GameAccepted)(using munit.Location)
      : ReadyGame = Outcomes.ready(at.state)

  /** The id of the decision `at` is parked on, read with the catalog's
    * phase powers, which the forced Wake is built from. */
  private def parkedDecisionId(at: GameAccepted)(using munit.Location)
      : String =
    ParkedNode.of(at.state, catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)) match
      case Right(Some(node)) => node.decisionId
      case other => fail(s"expected a parked decision, got $other")

  private def log(service: GameApplicationService, viewer: PlayerId)(
      using munit.Location): Vector[String] =
    texts(formatter.format(present(accepted(service.history(gameId)),
      "a stored game").steps, Some(viewer)))

  test("Hunger refused by two rulers' cards asks both, and the first " +
      "deactivation settles it"):
    // p1 rules Broken Peaks (City Wall), p3 rules Deep Woods (Forest
    // Council).
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p3))
    val waiting = rested(service)
    val request = present(readyAfter(waiting).consent.pending, "the request")
    assertEquals(request.asks.map(ask => ask.granter -> ask.powers.toSet)
      .toSet, Set(p1 -> Set(CityWall.id), p3 -> Set(ForestCouncil.id)))
    val no = answering(service, waiting, ConsentChoice.No, granter = p3)
    assertEquals(readyAfter(no).consent.pending, None)
    assertEquals(Look(readyAfter(no)).player(p2).advisers.size, 2)
    assertEquals(Look(readyAfter(no)).player(p3).advisers.size, 1)
    // The cards and granters follow the restrictions' catalog order.
    val lines = log(service, p2)
    assert(lines.exists(line => line.contains("City Wall") &&
      line.contains("Forest Council") && line.contains(", ") &&
      line.endsWith(" decide whether Hunger works.")), lines)
    assert(lines.contains("p3 deactivated Hunger with Forest Council."),
      lines)

  test("a grant beside another ruler's deactivation would have let Hunger " +
      "work, and did not"):
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p3))
    val waiting = rested(service)
    answering(service, answering(service, waiting, ConsentChoice.Yes,
      granter = p1), ConsentChoice.No, granter = p3)
    val lines = log(service, p2)
    assert(lines.contains("p1 would let Hunger work."), lines)
    assert(!lines.contains("p1 let Hunger work."), lines)
    assert(lines.contains("p3 deactivated Hunger with Forest Council."),
      lines)

  test("one ruler of both cards is asked once, about both"):
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p1))
    val request = present(readyAfter(rested(service)).consent.pending,
      "the request")
    assertEquals(request.asks.map(ask => ask.granter -> ask.powers.toSet),
      Vector(p1 -> Set(CityWall.id, ForestCouncil.id)))

  test("one ruler's deactivation names every card of theirs that refused"):
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p1))
    answering(service, rested(service), ConsentChoice.No, granter = p1)
    val lines = log(service, p2)
    assert(lines.exists(line => line.startsWith("p1 deactivated Hunger " +
      "with ") && line.contains("City Wall") &&
      line.contains("Forest Council")), lines)

  test("when every ruler lets Hunger work, its holder is asked whom to " +
      "bury"):
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p3))
    val waiting = rested(service)
    val both = answering(service, answering(service, waiting,
      ConsentChoice.Yes, granter = p1), ConsentChoice.Yes, granter = p3)
    assertEquals(parkedDecisionId(both), Hunger.decisionId)

  test("while Hunger's rulers decide, nobody may undo: the Rest that " +
      "walked there is a barrier"):
    val (service, _) = ConsentFixture.service(hungerUnder(peaksRuler = p1,
      woodsRuler = p3))
    val waiting = rested(service)
    for viewer <- Vector(p1, p2, p3) do
      assertEquals(projector.project(gameId, waiting.loaded, viewer).undo,
        None)
    assertEquals(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p1)), Left(GameApplicationError.CommandRejected(
        OathViolation.ConsentPending)))

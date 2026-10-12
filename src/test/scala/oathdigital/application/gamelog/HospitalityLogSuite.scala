package oathdigital.application.gamelog

import oathdigital.application.{GameApplicationService, GameCommand,
  ParkedServiceFixture, TreeDecision}
import oathdigital.gameplay.powers.travel.Hospitality
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}
import LogScripts.formatter

/** How Hospitality's question reads in the Game Log: no "Chose" line and no
  * line of its own; the generic gain line names the bank. */
class HospitalityLogSuite extends munit.FunSuite:
  private val destination = CatalogNames.site("Broken Peaks")

  /** p1 holds Hospitality (Nomad) and Messenger (Order) faceup and Travels
    * from Ancient City to Broken Peaks, where Elders (Nomad) and Siege
    * Engines (Order) stand. Only the Nomad and Order banks hold favor, and
    * p1 picks the Order bank. The log as p2 sees it. */
  private def travelledToBrokenPeaksPickingOrder: Vector[String] =
    val table = Table.start.pawn(p1, "Ancient City").supply(p1, 7)
      .adviser(p1, "Hospitality").adviser(p1, "Messenger")
      .denizen("Elders", "Broken Peaks")
      .denizen("Siege Engines", "Broken Peaks")
      .banksHoldingOnly(Suit.Nomad -> 2, Suit.Order -> 2)
    val (service, _) = table.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val picked = accepted(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, destination)))
    accepted(service.handle("g", picked.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(Hospitality.decisionId,
        DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.FavorBank(Suit.Order))))))
    texts(service)

  private def texts(service: GameApplicationService): Vector[String] =
    formatter.format(present(accepted(service.history("g")),
      "a stored game").steps, Some(p2)).map(LogScripts.text)

  test("the bank question posts no Chose line and the gain line names the " +
      "bank"):
    val log = travelledToBrokenPeaksPickingOrder
    assert(log.exists(_.startsWith("Gained 1 favor from the Order bank")),
      log.toString)
    assert(!log.exists(_.startsWith("Chose")), log.toString)
    assert(!log.exists(_.startsWith("Hospitality")), log.toString)

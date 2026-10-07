package oathdigital.application.gamelog

import oathdigital.application.{GameApplicationService, GameCommand,
  ParkedServiceFixture, TreeDecision}
import oathdigital.gameplay.powers.travel.WayStation
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted
import LogScripts.formatter

/** How a Travel through Way Station reads in the Game Log: the question posts
  * no "Chose" line, the favor answer posts the toll lines, and the Travel tag
  * waits for the step that spends (travel destination design, ruling 9). */
class WayStationLogSuite extends munit.FunSuite:
  private val destination = Table.start.state match
    case OathState.Ready(ready) => ready.game.current.map.inPlay
      .find(_.value == "site:broken-peaks").get
    case other => fail(s"not ready: $other")

  /** p2 rules Way Station at Broken Peaks; p1 Travels there holding `favor`. */
  private def travelled(favor: Int, answer: DecisionOptionRef.Button)
      : Vector[String] =
    val table = Table.start.denizen("Way Station", "Broken Peaks")
      .warbandsAt("Broken Peaks", p2, 1).favor(p1, favor).banditsRefilled
    val (service, _) = table.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val picked = accepted(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, destination)))
    accepted(service.handle("g", picked.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(WayStation.decisionId,
        DecisionAnswer.ChooseOneAnswer(answer)))))
    texts(service)

  private def texts(service: GameApplicationService): Vector[String] =
    formatter.format(service.history("g").toOption.flatten
      .getOrElse(fail("no history")).steps, Some(p2)).map(LogScripts.text)

  test("the favor answer posts the toll line and no Chose line"):
    val log = travelled(favor = 1, WayStation.favorOption)
    // The tag, then the toll, then where the Travel went.
    val order = Vector(log.indexOf("Travel"),
      log.indexOf("Way Station: Gave 1 favor to p2."),
      log.indexWhere(_.startsWith("Travelled to ")))
    assert(order.forall(_ >= 0) && order == order.sorted, log.toString)
    assert(!log.exists(_.startsWith("Chose")), log.toString)

  test("the Supply answer posts the Travel tag with its cost and no toll"):
    val log = travelled(favor = 1, WayStation.supplyOption)
    assert(log.exists(_.startsWith("Travel")), log.toString)
    assert(!log.exists(_.contains("favor to")), log.toString)
    assert(!log.exists(_.startsWith("Chose")), log.toString)

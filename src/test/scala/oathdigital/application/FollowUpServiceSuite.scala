package oathdigital.application

import oathdigital.gameplay.FollowUpPlay
import oathdigital.gameplay.walker.{FollowUpOffer, FollowUpSkipped}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** The follow-up offer is a parked walker decision, so the service holds
  * every other command until its player answers (walker-driven game loop
  * design, ruling 6). */
class FollowUpServiceSuite extends munit.FunSuite:
  private val ancientCity = CatalogNames.site("Ancient City")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))
  private val campaign = QueuedProcedure(p1, ActionRef.Campaign, Vector.empty,
    Some(knights), QueueKind.Offer)

  /** A fresh service at p1's Act with a Campaign offer parked for p1: two
    * bandits rule Ancient City, so it can start. */
  private def offered(using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository) =
    Table.start.pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").bandits(ancientCity, 2)
      .offering(campaign).service()

  private def answer(chosen: DecisionAnswer, by: PlayerId = p1) =
    GameCommand.ResolveWalker(by, TreeDecision(FollowUpOffer.decisionId,
      chosen))

  test("Skip is an answer, and writes FollowUpSkipped"):
    val skipped = offered._1.handle("game", 0L, answer(FollowUpPlay.skip))
    assertEquals(skipped.map(_.events.collect {
      case skip: FollowUpSkipped => skip }),
      Right(Vector(FollowUpSkipped(campaign))))

  test("Undo after taking the offer returns to the offer"):
    val (service, _) = offered
    val taken = accepted(service.handle("game", 0L, answer(FollowUpPlay.take)))
    val undone = accepted(service.handle("game", taken.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(FollowUpPlay.offered(undone.state), Some(campaign))

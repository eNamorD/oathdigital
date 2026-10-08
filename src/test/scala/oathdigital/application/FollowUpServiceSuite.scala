package oathdigital.application

import oathdigital.application.GameApplicationError.CommandRejected
import oathdigital.gameplay.walker.FollowUpSkipped
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** While an action is queued, the service accepts only its start and its
  * skip, from its player (follow-up actions design, ruling 3). */
class FollowUpServiceSuite extends munit.FunSuite:
  private val ancientCity = CatalogNames.site("Ancient City")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))
  private val campaign = FollowUp(p1, ActionRef.Campaign, knights)

  /** A fresh service at p1's Act with a Campaign owed to p1: two bandits rule
    * Ancient City, so it can start. */
  private def queued(using munit.Location): GameApplicationService =
    Table.start.pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").bandits(ancientCity, 2)
      .update(_.updateCurrent(_.copy(followUps = Vector(campaign))))
      .service()._1

  private def refused(command: GameCommand)(using munit.Location)
      : Option[OathViolation] =
    queued.handle("game", 0L, command) match
      case Left(CommandRejected(violation)) => Some(violation)
      case _ => None

  test("other actions, minor actions and other players are refused"):
    val pending = Some(OathViolation.FollowUpPending(campaign))
    assertEquals(refused(GameCommand.StartWalker(ActionRef.Travel,
      StartPayload(p1))), pending)
    assertEquals(refused(GameCommand.PeekSiteRelics(p1)), pending)
    assertEquals(refused(GameCommand.BeginRest(p1)), pending)
    assertEquals(refused(GameCommand.StartWalker(ActionRef.Campaign,
      StartPayload(p2))), pending)
    assertEquals(refused(GameCommand.SkipFollowUp(p2)), pending)

  test("the head's start and its skip are accepted"):
    assert(queued.handle("game", 0L, GameCommand.StartWalker(
      ActionRef.Campaign, StartPayload(p1))).isRight)
    val skipped = queued.handle("game", 0L, GameCommand.SkipFollowUp(p1))
    assertEquals(skipped.map(_.events.collect {
      case skip: FollowUpSkipped => skip }),
      Right(Vector(FollowUpSkipped(campaign))))

package oathdigital.application

import oathdigital.application.GameApplicationError.CommandRejected
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** A phase menu through the service (walker-driven game loop design,
  * rulings 11 and 12). */
class PhaseMenuServiceSuite extends munit.FunSuite:
  private val ancientCity = CatalogNames.site("Ancient City")
  private val favor = PhaseMenu.takeWealth(WakeResource.Favor)

  /** A fresh service at p1's Wake, the Wake menu parked, one favor to take
    * at Ancient City. */
  private def waking(using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository) =
    Table.start.pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").turn(p1, Phase.Wake)
      .siteTokens(ancientCity, favor = 1, secrets = 0)
      .phaseMenuParked.service()

  test("other commands wait for the menu"):
    val started = waking._1.handle("game", 0L, GameCommand.StartWalker(
      ActionRef.TakeWealth, StartPayload(p1,
        TakeWealthProcedure.selection(WakeResource.Favor))))
    assertEquals(started.left.toOption.collect {
      case CommandRejected(violation) => violation
    }, Some(OathViolation.InvalidEventOrder("a walker procedure is " +
      "pending; only walker resume commands are legal")))

  test("Undo after a pick returns to the menu"):
    val (service, _) = waking
    val taken = accepted(service.handle("game", 0L,
      MenuPlay.picking(p1, favor)))
    val undone = accepted(service.handle("game", taken.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(MenuPlay.parked(undone.state), Some(PhaseMenu.Menu.Wake))

package oathdigital.application

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.action.{Palanquin, PalanquinCard}
import oathdigital.gameplay.powers.travel.{TravelFixture, TyrantCard}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, Outcomes}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Undo and Redo around an automatic power's request (automatic power
  * consent design, "Undo coupling" and ruling 9). */
class AutomaticUseUndoSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val projector = new GameProjector(catalog)
  private val wall = CatalogNames.denizen("City Wall")
  private val coast = TravelFixture.coast
  private val home = TravelFixture.cradlePlains

  /** p1 holds Tyrant faceup at the Cradle's plains; City Wall stands at
    * the coast, which p2 rules with 2 warbands. */
  private val tyrantBesideAWalledCoast: ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(_
      .adviser(p1, "Tyrant").denizen(wall, at = coast)
      .warbandsAt(coast, p2, 2))

  /** p1 travels to `site` at `sequence`; returns the pick's batch. */
  private def travelling(service: GameApplicationService, sequence: Long,
      site: SiteId)(using munit.Location): GameAccepted =
    ParkedServiceFixture.travelTo(service, gameId, sequence, p1, site).picked

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  private def readyAfter(at: GameAccepted)(using munit.Location)
      : ReadyGame = Outcomes.ready(at.state)

  private def coastWarbands(at: GameAccepted)(using munit.Location): Int =
    Look(readyAfter(at)).forces(coast) match
      case SiteForces.Occupied(_, count) => count
      case SiteForces.Empty => 0

  private def undoing(service: GameApplicationService, at: GameAccepted,
      actor: PlayerId = p1)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence, GameCommand.Undo(actor)))

  private def redoing(service: GameApplicationService, at: GameAccepted)(
      using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence, GameCommand.Redo(p1)))

  private def parkedOnTheDestination(at: GameAccepted)(
      using munit.Location): Boolean =
    TravelFixture.parkedDecision(at.state).decisionId ==
      TravelProcedure.destinationDecisionId

  test("the traveller's Undo while the ruler decides withdraws the request " +
      "and takes back the Travel"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val undone = undoing(service, travelling(service, 0L, coast))
    assertEquals(readyAfter(undone).consent.pending, None)
    assertEquals(readyAfter(undone).consent.answers, Map.empty)
    assertEquals(Look(readyAfter(undone)).pawn(p1), home)
    assert(parkedOnTheDestination(undone))

  test("nobody else may withdraw it"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    assertEquals(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p2)), Left(GameApplicationError.CommandRejected(
        OathViolation.ConsentPending)))

  test("while the ruler decides, the traveller is offered Undo and the " +
      "ruler is not"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    assert(projector.project(gameId, waiting.loaded, p1).undo.nonEmpty)
    assertEquals(projector.project(gameId, waiting.loaded, p2).undo, None)

  test("one Undo after either answer takes back the answer and the " +
      "Travel, and the answer stays remembered"):
    for choice <- Vector(ConsentChoice.Yes, ConsentChoice.No) do
      val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
      val waiting = travelling(service, 0L, coast)
      val answered = answering(service, waiting, choice)
      assertEquals(projector.project(gameId, answered.loaded, p1).undo,
        projector.project(gameId, waiting.loaded, p1).undo)
      val undone = undoing(service, answered)
      assertEquals(Look(readyAfter(undone)).pawn(p1), home)
      assertEquals(coastWarbands(undone), 2)
      assert(parkedOnTheDestination(undone))
      assertEquals(readyAfter(undone).consent.answers.values.map(_.answer)
        .toVector, Vector(if choice.granted then ConsentAnswer.Granted
          else ConsentAnswer.Refused))

  test("Redo restores the Travel and the answer"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val undone = undoing(service, answering(service,
      travelling(service, 0L, coast), ConsentChoice.Yes))
    val redone = redoing(service, undone)
    assertEquals(Look(readyAfter(redone)).pawn(p1), coast)
    assertEquals(coastWarbands(redone), 1)
    assertEquals(readyAfter(redone).consent.pending, None)

  test("Redo after a withdrawal asks the ruler again"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    val redone = redoing(service, undoing(service, waiting))
    assertEquals(readyAfter(redone).consent.pending,
      readyAfter(waiting).consent.pending)

  test("an off-turn Travel's request is withdrawn by the player whose " +
      "command walked there, not by its traveller"):
    // p1 holds Palanquin and sends p3, who holds Tyrant, to the coast.
    val sent = TravelFixture.on(TravelFixture.board())(_
      .adviser(p1, PalanquinCard.id).favor(p1, 1)
      .pawn(p3, home).adviser(p3, "Tyrant")
      .denizen(wall, at = coast).warbandsAt(coast, p2, 2))
    val (service, _) = ConsentFixture.service(sent)
    val used = accepted(service.handle(gameId, 0L, MenuPlay.starting(p1,
      ActionRef.UsePower(Palanquin.id),
      Vector(DecisionOptionRef.Denizen(PalanquinCard.id)))))
    val aimed = accepted(service.handle(gameId, used.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(Palanquin.targetDecisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(p3))))))
    val waiting = accepted(service.handle(gameId, aimed.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(
        Palanquin.destinationDecisionId, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Site(coast))))))
    val request = present(readyAfter(waiting).consent.pending, "the request")
    assertEquals((request.requester, request.use),
      (p3, Some(ConsentUse(TyrantCard.id, p1))))
    assertEquals(projector.project(gameId, waiting.loaded, p3).undo, None)
    assert(projector.project(gameId, waiting.loaded, p1).undo.nonEmpty)
    assertEquals(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p3)), Left(GameApplicationError.CommandRejected(
        OathViolation.ConsentPending)))
    val undone = undoing(service, waiting, actor = p1)
    assertEquals(readyAfter(undone).consent.pending, None)
    assertEquals(Look(readyAfter(undone)).pawn(p3), home)

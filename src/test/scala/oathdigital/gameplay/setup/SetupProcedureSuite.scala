package oathdigital.gameplay.setup

import oathdigital.catalog.SiteOnly
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{Answers, CatalogNames, Outcomes, Situation}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, as, present}

class SetupProcedureSuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val ready = FirstGameSetupFixture.freshReady

  test("the setup walk opens on the first player's pawn placement"):
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    val first = ready.game.current.turn.activePlayer

    val pending1 = Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)).tree
    val decide1 = present(ProcedureWalker.parkedDecide(ready, tree, pending1,
      WalkerPowers.empty), "the first parked Decide")
    assertEquals(decide1.decisionId, SetupProcedure.pawnDecisionId(first))
    assertEquals(decide1.owner, first)

  test("the whole procedure finishes in Wake with the first player active, " +
      "three pawns, three advisers and no temporary hands"):
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    val finished = SetupWalkDriver.driveToCompletion(ready, tree,
      TriggeredProcedureRef.Setup, WalkerPowers.empty)
    assertEquals(finished.game.current.turn.phase, Phase.Wake)
    assertEquals(finished.game.current.turn.activePlayer,
      ready.game.current.turn.activePlayer)
    assertEquals(finished.game.current.players.count(_.pawnSite.nonEmpty), 3)
    assertEquals(finished.game.current.players.count(_.advisers.size == 1), 3)
    assert(finished.game.current.temporaryHands.values.forall(_.isEmpty))

  private val revealAll: Answers =
    case park if park.decisionId ==
        SetupProcedure.revealDecisionId(park.awaiting) =>
      val options = as[DecisionQuery.ChooseMany](park.decide.query).options
      DecisionAnswer.ChooseManyAnswer(options.map(_.ref))

  private val revealNone: Answers =
    case park if park.decisionId ==
        SetupProcedure.revealDecisionId(park.awaiting) =>
      DecisionAnswer.ChooseManyAnswer(Vector.empty)

  private def advisers(woken: Situation): Vector[AdviserState] =
    woken.ready.game.current.players.flatMap(_.advisers)

  test("Reveal Cards turns a chosen adviser faceup"):
    val woken = Situation.wake(Situation.rules(catalog).withAnswers(revealAll))
    val revealable = advisers(woken).collect { case d: DenizenState => d }
      .filterNot(d => catalog.denizen(d.id).exists(_.isInstanceOf[SiteOnly]))
    assert(revealable.nonEmpty, advisers(woken))
    assert(revealable.forall(_.orientation == Orientation.FaceUp),
      advisers(woken))
    assertNotEquals(woken.ready.game.current.turn.phase, Phase.Setup)

  test("Reveal Cards chosen empty keeps every adviser facedown"):
    val woken = Situation.wake(Situation.rules(catalog).withAnswers(revealNone))
    assert(advisers(woken).forall {
      case d: DenizenState => d.orientation == Orientation.FaceDown
      case v: VisionState => v.orientation == Orientation.FaceDown
    }, advisers(woken))

  test("Reveal Cards offers facedown advisers except site-only ones, then " +
      "facedown relics"):
    val woken = Situation.wake(Situation.rules(catalog))
    val siteOnly = CatalogNames.denizen("Martial Culture")
    assert(catalog.denizen(siteOnly).exists(_.isInstanceOf[SiteOnly]))
    val open = CatalogNames.denizen("Wrestlers")
    val faceup = CatalogNames.denizen("Defame")
    val relic = CatalogNames.relic("Sticky Fire")
    // No Table step clears a player's advisers and relics, and p1's dealt
    // adviser must not stay, so p1's hand is replaced wholesale.
    val staged = woken.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if p.player != p1 then p else
        p.copy(
          advisers = Vector(
            DenizenState(siteOnly, Orientation.FaceDown, Tokens.empty),
            DenizenState(open, Orientation.FaceDown, Tokens.empty),
            DenizenState(faceup, Orientation.FaceUp, Tokens.empty)),
          relics = Vector(RelicState(relic, Orientation.FaceDown, Tokens.empty))))))
    // Only slot 1 (Wrestlers) is offered among the advisers: slot 0 is
    // site-only and slot 2 is already faceup. Sticky Fire is relic slot 0.
    assertEquals(SetupProcedure.revealable(catalog, staged, p1).map(_.ref),
      Vector(DecisionOptionRef.AdviserSlot(p1, 1),
        DecisionOptionRef.RelicSlot(p1, 0)))

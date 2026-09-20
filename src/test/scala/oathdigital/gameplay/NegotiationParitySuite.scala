package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.NegotiationFixture.Board
import oathdigital.gameplay.actions.NegotiationCommand
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathState.Ready

object NegotiationParitySuite {
  sealed trait Move { def by: PlayerId }
  final case class Propose(by: PlayerId, terms: NegotiationTerms) extends Move
  final case class Accept(by: PlayerId) extends Move
  final case class Decline(by: PlayerId) extends Move
}

/** The legacy Negotiation and the walker Negotiation, run on the same board
  * and compared on the state they leave and on what each participant is shown
  * while the deal is open. Parity is possible only in the states legacy
  * accepts, so every fixture is the first-game one.
  */
class NegotiationParitySuite extends munit.FunSuite {
  import NegotiationParitySuite._

  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private val legacyId = DecisionId("parity")

  private def legacyCommand(move: Move): NegotiationCommand = move match {
    case Propose(by, terms) => NegotiationCommand.ReplaceTerms(by, legacyId, terms)
    case Accept(by) => NegotiationCommand.Accept(by, legacyId)
    case Decline(by) => NegotiationCommand.Decline(by, legacyId)
  }

  private def legacy(b: Board, who: Vector[PlayerId],
      script: Vector[Move]): OathTransition = {
    val begun = rules.handle(Ready(b.ready),
      NegotiationCommand.Begin(b.actor, legacyId, who))
      .getOrElse(fail("legacy Begin must succeed"))
    script.foldLeft(begun) { (current, move) =>
      rules.handle(current.state, legacyCommand(move))
        .getOrElse(fail(s"legacy $move must succeed"))
    }
  }

  private def walkerOpen(b: Board, who: Vector[PlayerId]): OathTransition = {
    val started = rules.startWalker(Ready(b.ready), ActionRef.Negotiation,
      b.actor).getOrElse(fail("walker start must succeed"))
    if (NegotiationDeal.eligible(b.ready, b.actor).size < 2) started
    else rules.resolveWalker(started.state, b.actor,
      NegotiationDeal.negotiatorsDecisionId, ChooseManyAnswer(
        who.map(DecisionOptionRef.Player(_))))
      .getOrElse(fail("walker negotiators must be accepted"))
  }

  private def walker(b: Board, who: Vector[PlayerId],
      script: Vector[Move]): OathTransition =
    script.foldLeft(walkerOpen(b, who)) { (current, move) =>
      val answer: DecisionAnswer = move match {
        case Propose(_, terms) => ProposeTerms(terms)
        case Accept(_) => AcceptDeal
        case Decline(_) => DeclineDeal
      }
      rules.resolveWalker(current.state, move.by,
        NegotiationDeal.dealDecisionId, answer)
        .getOrElse(fail(s"walker $move must be accepted"))
    }

  private def assertSameOutcome(b: Board, who: Vector[PlayerId],
      script: Vector[Move]): Unit = {
    val expected = legacy(b, who, script)
    val actual = walker(b, who, script)
    val (x, y) = (ready(expected.state), ready(actual.state))
    assertEquals(y.game.current.players, x.game.current.players)
    assertEquals(y.game.current.map, x.game.current.map)
    assertEquals(y.game.current.banners, x.game.current.banners)
    assertEquals(y.game.current.turn, x.game.current.turn)
    assertEquals(y.knowledge, x.knowledge)
    assertEquals(y.banks, x.banks)
    assertEquals(y.game.current.pending, None)
    assertEquals(y.game.current.walkerPending, None)
    assertEquals(actual.continue, expected.continue)
    (b.players.map(_.player)).foreach(viewer => assertEquals(
      projector.project("parity", LoadedGame(actual.state, 30), viewer),
      projector.project("parity", LoadedGame(expected.state, 30), viewer)))
    assertEquals(projector.projectPublic("parity", LoadedGame(actual.state, 30)),
      projector.projectPublic("parity", LoadedGame(expected.state, 30)))
  }

  private def gift(to: PlayerId, favor: Int, relics: Vector[RelicId] = Vector.empty) =
    NegotiationTerms(Vector(NegotiationTransfer(to, favor, relics)))

  test("a bilateral favor and relic transfer") {
    val b = NegotiationFixture.board()
    assertSameOutcome(b, Vector(b.second), Vector(
      Propose(b.actor, gift(b.second, 3, Vector(b.actorRelic))),
      Accept(b.second), Accept(b.actor)))
  }

  test("three players, a replaced term clearing consent, any acceptance order") {
    val b = NegotiationFixture.board()
    assertSameOutcome(b, Vector(b.second, b.third), Vector(
      Propose(b.actor, gift(b.second, 1)), Accept(b.second),
      Propose(b.third, gift(b.actor, 2)), Accept(b.third), Accept(b.second),
      Accept(b.actor)))
  }

  test("a disclosure deal grants selective durable knowledge") {
    val b = NegotiationFixture.board()
    val adviser = b.players.head.advisers.head.id.asInstanceOf[WorldCardId]
    val terms = NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.Adviser(b.actor, adviser)),
      NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.SiteRelic(b.site, b.siteRelic))))
    assertSameOutcome(b, Vector(b.second, b.third), Vector(
      Propose(b.actor, terms), Accept(b.actor), Accept(b.second),
      Accept(b.third)))
  }

  test("information for assets: a held-relic disclosure for favor") {
    val b = NegotiationFixture.board()
    val theirs = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
      b.actor, NegotiationDisclosureRef.HeldRelic(b.second, b.otherRelic))))
    assertSameOutcome(b, Vector(b.second), Vector(
      Propose(b.actor, gift(b.second, 2)), Propose(b.second, theirs),
      Accept(b.actor), Accept(b.second)))
  }

  test("a decline applies nothing and runs the action boundary") {
    val b = NegotiationFixture.board()
    assertSameOutcome(b, Vector(b.second), Vector(
      Propose(b.actor, gift(b.second, 3)), Decline(b.second)))
  }

  test("what each participant is shown mid-deal matches") {
    val b = NegotiationFixture.board()
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(b.second, 3, Vector(b.actorRelic))),
      Vector(NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.HeldRelic(b.actor, b.actorRelic))))
    val who = Vector(b.second, b.third)
    val script = Vector[Move](Propose(b.actor, terms), Accept(b.second))
    val old = legacy(b, who, script).state
    val now = walker(b, who, script).state
    Vector(b.actor, b.second, b.third).foreach { viewer =>
      val before = projector.project("parity", LoadedGame(old, 30), viewer)
        .negotiation.getOrElse(fail("legacy projects the deal to participants"))
      val after = projector.project("parity", LoadedGame(now, 30), viewer)
        .walkerDecision.flatMap(_.query).flatMap(_.deal)
        .getOrElse(fail("the walker projects the deal to owners"))
      assertEquals(after.participantPlayerIds, before.participantPlayerIds)
      assertEquals(after.acceptedPlayerIds, before.acceptedPlayerIds)
      assertEquals(after.transfers, before.transfers)
      assertEquals(after.disclosures, before.disclosures)
      val editing = after.editing.getOrElse(fail("participants get editing"))
      assertEquals((editing.editableFavor, editing.editableRelics,
        editing.editableAdvisers, editing.editableSiteRelics),
        (before.editableFavor, before.editableRelics, before.editableAdvisers,
          before.editableSiteRelics))
    }
  }

  test("both paths refuse an over-budget proposal, an empty accept and an outsider") {
    val b = NegotiationFixture.board()
    val who = Vector(b.second)
    val begun = rules.handle(Ready(b.ready),
      NegotiationCommand.Begin(b.actor, legacyId, who)).toOption.get.state
    assert(rules.handle(begun, legacyCommand(Propose(b.actor, gift(b.second, 6)))).isLeft)
    assert(rules.handle(begun, legacyCommand(Accept(b.second))).isLeft)
    assert(rules.handle(begun, legacyCommand(Propose(b.third, gift(b.actor, 1)))).isLeft)
    val parked = walkerOpen(b, who).state
    def say(by: PlayerId, answer: DecisionAnswer) = rules.resolveWalker(parked,
      by, NegotiationDeal.dealDecisionId, answer)
    assert(say(b.actor, ProposeTerms(gift(b.second, 6))).isLeft)
    assert(say(b.second, AcceptDeal).isLeft)
    assert(say(b.third, ProposeTerms(gift(b.actor, 1))).isLeft)
  }
}

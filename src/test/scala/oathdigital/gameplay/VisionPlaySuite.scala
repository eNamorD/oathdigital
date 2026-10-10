package oathdigital.gameplay

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PlacementPicks, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

class VisionPlaySuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val walkerParked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))
  private val conspiracy = VisionRules.Conspiracy
  private val newVision = VisionRules.Faith
  private val existingVision = VisionRules.Conquest
  /** The answer that plays the card faceup at the placement `state` is
    * parked on. */
  private def faceup(state: OathState): DecisionAnswer.ChooseOneAnswer =
    PlacementPicks.parkedAnswer(state, PlayedAs.Faceup,
      WalkerPowerCatalog.default(catalog))

  private def placeId(card: VisionId) =
    PlacementFixture.cardPlayDecisionId(card, "place")
  private val actor = p1
  private val home = "Ancient City"

  /** The quiet table with p1's pawn stated at Ancient City, in the Cradle:
    * its next region is the Provinces, where p1's discards go. */
  private def atHome: Table = Table.start.pawn(actor, at = home)

  /** `newVision` tops the world deck and p1's world Search has started, so
    * the walk parks on `newVision`'s own `cardplay.place.*` decision with
    * nothing else drawn. `revealed`, if given, is p1's revealed Vision before
    * the play.
    */
  private def visionInTemporaryHand(revealed: Option[VisionId] = None)
      : OathState =
    val stacked = atHome.worldDeckTop(newVision)
    val initial = revealed.fold(stacked)(stacked.revealedVision(actor, _))
      .ready
    accepted(SearchPlay.searched(rules, Ready(initial), actor)).state

  /** The `Decide` node the walk is parked on, rebuilt the same way the
    * projector reads a parked position. */
  private def parkedPlacement(state: OathState): Decide =
    val ready = Outcomes.ready(state)
    val current = ready.game.current
    val procedure = present(current.walkerProcedure, "the parked procedure")
    val pending = present(current.walkerPending, "the parked walk")
    val tree = accepted(WalkerProcedureRegistry.rebuild(procedure, catalog,
      ready, actor, current.walkerStartArgs))
    present(ProcedureWalker.parkedDecide(ready, tree, pending,
      WalkerPowers.empty), "the parked decision")

  private def answerPlacementResult(state: OathState, ref: DecisionOptionRef)
      : Either[OathViolation, OathState] =
    rules.resolveWalker(state, actor, placeId(newVision),
      DecisionAnswer.ChooseOneAnswer(ref)).map(_.state)

  private def answerPlacement(state: OathState, ref: DecisionOptionRef)
      : OathState = accepted(answerPlacementResult(state, ref))

  private def walkerIsFinished(state: OathState): Boolean =
    MenuPlay.pendingBeyondMenu(Outcomes.ready(state)).isEmpty

  private def revealedVisionOf(state: OathState): Option[VisionState] =
    Look(state).player(actor).revealedVision

  private def provincesDiscard(state: OathState): Vector[WorldCardId] =
    Outcomes.ready(state).game.current.commonCards.discard(Region.Provinces)

  test("a Conspiracy kept faceup from Search parks on its target, which only " +
      "its owner sees, takes it and leaves the game"):
    // p2, the enemy, shares p1's site, Ancient City, and holds the People's
    // Favor; Conspiracy tops the deck.
    val enemy = p2
    val initial = atHome.pawn(enemy, at = home)
      .peoplesFavor(Some(enemy), favor = 1).darkestSecret(None, secrets = 1)
      .worldDeckTop(conspiracy).ready
    val started = accepted(
      SearchPlay.searched(rules, OathState.Ready(initial), actor))
    val parked = accepted(
      rules.resolveWalker(started.state, actor, placeId(conspiracy),
        faceup(started.state)))
    walkerParked.assertParked(parked.state, ActionRef.Search,
      "cardplay.conspiracy.target", actor)
    assertEquals(Outcomes.ready(parked.state)
      .game.current.temporaryHands(actor), Vector[WorldCardId](conspiracy))

    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(parked.state, 11)
    val owner = projector.project("searched-conspiracy", loaded, actor)
    val hidden = projector.project("searched-conspiracy", loaded, enemy)
    assertEquals(owner.walkerDecision.map(_.decisionId),
      Some("cardplay.conspiracy.target"))
    assertEquals(owner.walkerDecision.flatMap(_.query)
      .map(_.offeredOptions.map(option => (option.kind, option.id))),
      Some(Vector(("banner", "peoples-favor"))))
    assertEquals(hidden.walkerDecision, None)

    val resolved = accepted(rules.resolveWalker(parked.state, actor,
      "cardplay.conspiracy.target", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Banner(Banner.PeoplesFavor))))
    val after = Outcomes.ready(resolved.state)
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(MenuPlay.pendingBeyondMenu(after), None)
    assert(!accepted(CardIndex.from(after.game)).ids.contains(conspiracy))

  test("Reveal is a facedown Vision played faceup: it replaces the revealed " +
      "Vision and costs no Supply"):
    val old = VisionRules.Conquest
    val revealed = VisionRules.Faith
    val staged = atHome.adviser(actor, revealed, facedown = true)
      .revealedVision(actor, old).ready
    val before = Look(staged).player(actor)
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      OathState.Ready(staged), actor, revealed))
    val done = accepted(
      rules.resolveWalker(started.state, actor, placeId(revealed),
        faceup(started.state)))
    val after = Outcomes.ready(done.state)
    val updated = Look(after).player(actor)
    assertEquals(updated.board.supply, before.board.supply)
    assertEquals(updated.revealedVision.map(_.id), Some(revealed))
    assert(!updated.advisers.exists(_.id == revealed))
    assert(provincesDiscard(done.state).contains(old))
    assertEquals(MenuPlay.pendingBeyondMenu(after), None)

  test("a Conspiracy played from a facedown adviser through the service " +
      "with nothing to take is boxed"):
    // p1 holds Conspiracy facedown; p2 and p3 stand elsewhere, so no one
    // shares p1's site and it has nothing to take.
    val staged = atHome.pawn(p2, at = "Broken Peaks").pawn(p3, at = "Buried Giant")
      .adviser(actor, conspiracy, facedown = true).ready
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      OathState.Ready(staged), actor, conspiracy))
    val done = accepted(
      rules.resolveWalker(started.state, actor, placeId(conspiracy),
        faceup(started.state)))
    val after = Outcomes.ready(done.state)
    assert(!Look(after).player(actor).advisers.exists(_.id == conspiracy))
    assertEquals(MenuPlay.pendingBeyondMenu(after), None)
    assert(!accepted(CardIndex.from(after.game)).ids.contains(conspiracy))

  test("a Vision's placement offers the discard pile, the revealed Vision " +
      "spot and a facedown adviser slot, never a site"):
    val decision = parkedPlacement(visionInTemporaryHand())
    val offered = decision.query match
      case DecisionQuery.ChooseOne(options, _, _) => options.map(_.ref)
      case other => fail(s"expected a choose-one, got $other")
    assertEquals(offered, Vector[DecisionOptionRef](
      DecisionOptionRef.DiscardPile(Region.Provinces),
      DecisionOptionRef.RevealedVision(actor),
      DecisionOptionRef.AdviserPlay(actor, 0, faceup = false)))

  test("playing a Vision over a revealed one asks nothing more"):
    val state = visionInTemporaryHand(revealed = Some(existingVision))
    val after = answerPlacement(state, DecisionOptionRef.RevealedVision(actor))
    assertEquals(walkerIsFinished(after), true)
    assertEquals(revealedVisionOf(after).map(_.id), Some(newVision))
    assert(provincesDiscard(after).contains(existingVision))

  test("a Vision answer naming a site slot is rejected -- it is not offered"):
    val state = visionInTemporaryHand()
    assertEquals(answerPlacementResult(state, DecisionOptionRef.SiteSlot(
      CatalogNames.site("Ancient City"), 0)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${placeId(newVision)} does not offer the selected option")))

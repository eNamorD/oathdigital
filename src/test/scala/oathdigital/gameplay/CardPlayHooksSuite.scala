package oathdigital.gameplay

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

/** The played-card windows: a faceup play and a facedown play each visit their
  * own window, and a discard visits neither.
  *
  * A probe power adds Supply in each window (1 faceup, 2 facedown), so the
  * Supply the actor ends with says which hooks ran.
  */
class CardPlayHooksSuite extends munit.FunSuite {
  private val probeId = PowerId("test.card-play-hooks")
  private val probe: ContributingPower = new ContributingPower {
    def id: PowerId = probeId
    def source: RuleSourceRef = RuleSourceRef.GameRule(probeId.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, ops) =>
        ops :+ GainSupply(ctx.activePlayer, 1))),
      PowerWindow.ActionCardPlayedFacedown -> Vector(Transform((ctx, ops) =>
        ops :+ GainSupply(ctx.activePlayer, 2))))
  }
  private val powers = WalkerPowers(Vector(probe))
  private val startSupply = 3

  private def plainDenizen(ready: ReadyGame): DenizenId =
    ready.game.current.commonCards.worldDeck.collectFirst {
      case id: DenizenId if catalog.denizens.exists(d => d.id.value == id.value &&
        d.restrictions == CardRestrictions.Unrestricted) => id
    }.get

  /** The active player holds `card` in a temporary hand, with Supply 3. */
  private def inHand(card: WorldCardId): (ReadyGame, PlayerId) = {
    val base = initialReady
    val actor = base.game.current.turn.activePlayer
    val current = base.game.current
    (base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(card)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(board = p.board.copy(supply = SupplyTrack(startSupply))) else p))),
      actor)
  }

  private def supplyOf(ready: ReadyGame, actor: PlayerId): Int =
    ready.game.current.players.find(_.player == actor).get.board.supply.supply

  /** Plays `card` from the hand with `button`, returning the finished walk. */
  private def play(ready: ReadyGame, actor: PlayerId, card: WorldCardId,
      button: String): WalkerOutcome.Finished = {
    val tree = CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get
    val parked = ProcedureWalker.advance(ready, tree, None, powers).toOption.get
      .asInstanceOf[WalkerOutcome.Parked].tree
    ProcedureWalker.resolve(ready, tree, parked, Answered(
      s"cardplay.place.${card.kind}.${card.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button(button)), actor),
      powers).toOption.get.asInstanceOf[WalkerOutcome.Finished]
  }

  private def attributed(finished: WalkerOutcome.Finished): Boolean =
    finished.events.collect { case step: WalkerStepRecorded => step }
      .exists(_.contributions.contains(probeId))

  test("the faceup window keeps the persisted key of the single window") {
    assertEquals(PowerWindow.ActionCardPlayedFaceup.key, "action.card-played")
    assertEquals(PowerWindow.ActionCardPlayedFacedown.key,
      "action.card-played-facedown")
  }

  test("a card played to a site visits the faceup window only") {
    val card = plainDenizen(initialReady)
    val (ready, actor) = inHand(card)
    val done = play(ready, actor, card, "site")
    assertEquals(supplyOf(done.treeless, actor), startSupply + 1)
    assert(attributed(done))
  }

  test("a card played as a faceup adviser visits the faceup window only") {
    val card = plainDenizen(initialReady)
    val (ready, actor) = inHand(card)
    val done = play(ready, actor, card, "adviser-faceup")
    assertEquals(supplyOf(done.treeless, actor), startSupply + 1)
  }

  test("a denizen played as a facedown adviser visits the facedown window only") {
    val card = plainDenizen(initialReady)
    val (ready, actor) = inHand(card)
    val done = play(ready, actor, card, "adviser-facedown")
    assertEquals(supplyOf(done.treeless, actor), startSupply + 2)
    assert(attributed(done))
  }

  test("a Vision played as a facedown adviser visits the facedown window") {
    val (ready, actor) = inHand(VisionRules.Faith)
    val done = play(ready, actor, VisionRules.Faith, "adviser-facedown")
    assertEquals(supplyOf(done.treeless, actor), startSupply + 2)
  }

  test("a discard visits neither window") {
    val card = plainDenizen(initialReady)
    val (ready, actor) = inHand(card)
    val done = play(ready, actor, card, "discard")
    assertEquals(supplyOf(done.treeless, actor), startSupply)
    assert(!attributed(done))
  }

  test("a facedown adviser played faceup visits the faceup window") {
    val card = plainDenizen(initialReady)
    val base = initialReady
    val actor = base.game.current.turn.activePlayer
    val current = base.game.current
    val ready = base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(board = p.board.copy(supply = SupplyTrack(startSupply)),
          advisers = p.advisers :+ DenizenState(card, Orientation.FaceDown,
            Tokens.empty)) else p)))
    val tree = CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.FacedownAdviser).toOption.get
    val parked = ProcedureWalker.advance(ready, tree, None, powers).toOption.get
      .asInstanceOf[WalkerOutcome.Parked].tree
    val done = ProcedureWalker.resolve(ready, tree, parked, Answered(
      s"cardplay.place.${card.kind}.${card.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("adviser-faceup")),
      actor), powers).toOption.get.asInstanceOf[WalkerOutcome.Finished]
    assertEquals(supplyOf(done.treeless, actor), startSupply + 1)
  }

  test("the facedown hook names the card and the player who played it") {
    val hook = CardPlayedFacedown(VisionRules.Faith, PlayerId("p1"))
    assertEquals(hook.window, Some(PowerWindow.ActionCardPlayedFacedown))
    assertEquals(hook.children, Vector.empty[Operation])
    assertEquals((hook.card, hook.player), (VisionRules.Faith, PlayerId("p1")))
  }
}

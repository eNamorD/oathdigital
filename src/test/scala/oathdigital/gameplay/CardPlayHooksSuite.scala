package oathdigital.gameplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Unwrap.accepted

/** The played-card windows: a faceup play and a facedown play each visit their
  * own window, and a discard visits neither.
  *
  * A probe power adds Supply in each window (1 faceup, 2 facedown), so the
  * Supply the actor ends with says which hooks ran.
  */
class CardPlayHooksSuite extends munit.FunSuite:
  private val probeId = PowerId("test.card-play-hooks")
  /** Stub of ContributingPower: adds 1 Supply in the faceup window and 2 in
    * the facedown window. */
  private val probe: ContributingPower = new ContributingPower:
    def id: PowerId = probeId
    def source: RuleSourceRef = RuleSourceRef.GameRule(probeId.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, ops) =>
        ops :+ GainSupply(ctx.activePlayer, 1))),
      PowerWindow.ActionCardPlayedFacedown -> Vector(Transform((ctx, ops) =>
        ops :+ GainSupply(ctx.activePlayer, 2))))
  private val powers = WalkerPowers(Vector(probe))
  private val startSupply = 3
  private val actor = Table.p1
  // An unrestricted denizen whose power these tests do not use.
  private val card = CatalogNames.denizen("Errand Boy")

  /** p1 holds `held` in a temporary hand, with `startSupply` Supply. */
  private def inHand(held: WorldCardId): ReadyGame =
    Table.start.hand(actor, held).supply(actor, startSupply).ready

  /** Plays `played` from `origin` with `button`, returning the finished
    * walk. */
  private def play(ready: ReadyGame, played: WorldCardId, button: String,
      origin: CardPlayProcedure.Origin = CardPlayProcedure.Origin.TemporaryHand)
      : WalkerOutcome.Finished =
    val tree = accepted(CardPlayProcedure.build(catalog, ready, actor, played,
      origin))
    val parked = Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, powers)).tree
    val answer = Answered(PlacementFixture.cardPlayDecisionId(played, "place"),
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button(button)), actor)
    Outcomes.finished(
      ProcedureWalker.resolve(ready, tree, parked, answer, powers))

  private def supplyAfter(done: WalkerOutcome.Finished): Int =
    Look(done.treeless).supply(actor)

  private def attributed(finished: WalkerOutcome.Finished): Boolean =
    finished.events.collect { case step: WalkerStepRecorded => step }
      .exists(_.contributions.contains(probeId))

  test("a card played to a site visits the faceup window only"):
    val done = play(inHand(card), card, "site")
    assertEquals(supplyAfter(done), startSupply + 1)
    assert(attributed(done))

  test("a card played as a faceup adviser visits the faceup window only"):
    val done = play(inHand(card), card, "adviser-faceup")
    assertEquals(supplyAfter(done), startSupply + 1)

  test("a denizen played as a facedown adviser visits the facedown window only"):
    val done = play(inHand(card), card, "adviser-facedown")
    assertEquals(supplyAfter(done), startSupply + 2)
    assert(attributed(done))

  test("a Vision played as a facedown adviser visits the facedown window"):
    val done = play(inHand(VisionRules.Faith), VisionRules.Faith,
      "adviser-facedown")
    assertEquals(supplyAfter(done), startSupply + 2)

  test("a discard visits neither window"):
    val done = play(inHand(card), card, "discard")
    assertEquals(supplyAfter(done), startSupply)
    assert(!attributed(done))

  test("a facedown adviser played faceup visits the faceup window"):
    val ready = Table.start.adviser(actor, card, facedown = true)
      .supply(actor, startSupply).ready
    val done = play(ready, card, "adviser-faceup",
      CardPlayProcedure.Origin.FacedownAdviser)
    assertEquals(supplyAfter(done), startSupply + 1)

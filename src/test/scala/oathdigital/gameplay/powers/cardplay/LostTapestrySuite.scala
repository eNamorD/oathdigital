package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.{CardPlayRules, VisionRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Unwrap
import oathdigital.testkit.Unwrap.{accepted, present}

class LostTapestrySuite extends munit.FunSuite:
  private val actor = Table.p1
  private val tapestry = CatalogNames.relic("Lost Tapestry")
  private val faith = VisionRules.Faith
  private val plain = Vector("Threatening Roar", "Fae Merchant", "Wrestlers")
    .map(CatalogNames.denizen(_))
  /** A plain denizen in hand. */
  private val played = CatalogNames.denizen("Errand Boy")

  /** p1's Act at Ancient City, holding Lost Tapestry (facedown when
    * `facedownTapestry`), `held` as facedown advisers and `card` in hand. */
  private def holding(card: WorldCardId, held: Vector[WorldCardId],
      facedownTapestry: Boolean = false): ReadyGame =
    held.foldLeft(Table.start.turn(actor, Phase.Act)
      .pawn(actor, "Ancient City")
      .relic(actor, tapestry, facedown = facedownTapestry)
      .hand(actor, card))(_.adviser(actor, _, facedown = true)).ready

  /** The placement options of `card` from hand, with Lost Tapestry's power
    * in force, that play it `as`. */
  private def placementOptions(ready: ReadyGame, card: WorldCardId,
      as: PlayedAs): Vector[DecisionOption] =
    val tree = accepted(CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand))
    val powers = WalkerPowers(Vector(LostTapestry))
    val parked = Unwrap.as[WalkerOutcome.Parked](accepted(
      ProcedureWalker.advance(ready, tree, None, powers))).tree
    Unwrap.as[DecisionQuery.ChooseOne](present(
      ProcedureWalker.parkedDecide(ready, tree, parked, powers),
      "the placement decision").query).options
      .filter(option => CardPlayProcedure.playedAs(option.ref).contains(as))

  private def adviserPlay(index: Int, faceup: Boolean): DecisionOption =
    DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(actor, index, faceup))

  test("a Vision played facedown at the limit takes a free slot"):
    val ready = holding(faith, plain)
    assertEquals(placementOptions(ready, faith, PlayedAs.Facedown),
      Vector(adviserPlay(3, faceup = false)))

  test("a held facedown Vision is shown unavailable for Replace at the " +
      "limit"):
    val ready = holding(played, faith +: plain)
    assertEquals(placementOptions(ready, played, PlayedAs.Faceup), Vector(
      DecisionOption.Unavailable(DecisionOption.Replacing(
        adviserPlay(0, faceup = true), faith),
        OptionReason.Written(CardPlayRules.notCounted))) ++
      plain.zipWithIndex.map((card, index) => DecisionOption.Replacing(
        adviserPlay(index + 1, faceup = true), card)))

  test("a facedown Lost Tapestry keeps nothing from counting"):
    val ready = holding(faith, plain, facedownTapestry = true)
    assertEquals(placementOptions(ready, faith, PlayedAs.Facedown),
      plain.zipWithIndex.map((card, index) => DecisionOption.Replacing(
        adviserPlay(index, faceup = false), card)))

package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.SearchFixture._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** A When Played power that asks the player something asks it after the card
  * has left the temporary hand (or the facedown slot). The walker rebuilds the
  * tree from the state at every command, so the tree must not depend on the
  * card still being where the play started. Family Heirloom is the power with
  * such a question; these run it through the whole rules path.
  */
class WhenPlayedDecisionAfterPlaySuite extends munit.FunSuite {
  private val heirloom = FamilyHeirloom.forCatalog(catalog).get.cardId
  private val take = DecisionAnswer.ChooseOneAnswer(FamilyHeirloom.keep)
  private val bottom = DecisionAnswer.ChooseOneAnswer(FamilyHeirloom.bottom)

  private def answer(from: OathTransition, choice: DecisionAnswer) =
    rules.resolveWalker(from.state, PowerFixture.actor,
      FamilyHeirloom.decisionId, choice)

  private def relics(ready: ReadyGame) = PowerFixture.player(ready).relics

  test("a searched Family Heirloom played faceup asks its question and keeps " +
      "the relic") {
    val ready = staged(Vector(heirloom))
    val top = ready.game.current.commonCards.relicDeck.head
    val done = (for {
      started <- start(ready)
      chosen <- keep(started, heirloom)
      placed <- place(chosen, heirloom, "adviser-faceup")
      answered <- answer(placed, take)
    } yield answered).fold(error => fail(error.toString), value => value)
    assert(relics(after(done)).exists(_.id == top))
  }

  test("a searched Family Heirloom played faceup can put the relic on the " +
      "bottom of the deck") {
    val ready = staged(Vector(heirloom))
    val top = ready.game.current.commonCards.relicDeck.head
    val done = (for {
      started <- start(ready)
      chosen <- keep(started, heirloom)
      placed <- place(chosen, heirloom, "adviser-faceup")
      answered <- answer(placed, bottom)
    } yield answered).fold(error => fail(error.toString), value => value)
    assert(!relics(after(done)).exists(_.id == top))
    assertEquals(after(done).game.current.commonCards.relicDeck.last, top)
  }

  test("a facedown Family Heirloom played faceup asks its question and keeps " +
      "the relic") {
    val ready = PowerFixture.inPhase(PowerFixture.asAdviser(staged(Vector.empty),
      heirloom, Orientation.FaceDown), Phase.Act)
    val top = ready.game.current.commonCards.relicDeck.head
    val done = (for {
      started <- rules.startWalker(OathState.Ready(ready),
        ActionRef.PlayFacedownAdviser, PowerFixture.actor, Vector.empty,
        Vector(DecisionOptionRef.Denizen(heirloom)))
      placed <- place(started, heirloom, "adviser-faceup")
      answered <- answer(placed, take)
    } yield answered).fold(error => fail(error.toString), value => value)
    assert(relics(after(done)).exists(_.id == top))
  }

  test("a replacement is answered before the card is played, and the " +
      "question after the play still resolves") {
    val fillers = denizensOf(Suit.Arcane).take(6)
    val full = fillers.foldLeft(staged(Vector(heirloom)))((ready, id) =>
      if (PowerFixture.player(ready).advisers.size >= 3) ready
      else PowerFixture.asAdviser(ready, id))
    val current = PowerFixture.player(full).advisers.map(_.id)
    val started = start(full).toOption.get
    val chosen = keep(started, heirloom).toOption.get
    val placed = place(chosen, heirloom, "adviser-faceup").toOption.get
    // With every adviser slot taken the play first asks what to replace.
    val replaced = rules.resolveWalker(placed.state, PowerFixture.actor,
      s"cardplay.replace.denizen.${heirloom.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(
        current.head.asInstanceOf[DenizenId]))).fold(
      error => fail(error.toString), value => value)
    val done = answer(replaced, take).fold(error => fail(error.toString),
      value => value)
    assert(relics(after(done)).nonEmpty)
    assert(!PowerFixture.player(after(done)).advisers.exists(
      _.id == current.head))
  }
}

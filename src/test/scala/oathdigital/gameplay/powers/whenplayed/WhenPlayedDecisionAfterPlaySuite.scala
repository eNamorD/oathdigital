package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.SearchFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** A When Played power that asks the player something asks it after the card
  * has left the temporary hand (or the facedown slot). The walker rebuilds the
  * tree from the state at every command, so the tree must not depend on the
  * card still being where the play started. Family Heirloom is the power with
  * such a question; these run it through the whole rules path.
  */
class WhenPlayedDecisionAfterPlaySuite extends munit.FunSuite:
  private val heirloom = FamilyHeirloom.cardId
  private val take = DecisionAnswer.ChooseOneAnswer(FamilyHeirloom.keep)
  private val bottom = DecisionAnswer.ChooseOneAnswer(FamilyHeirloom.bottom)
  /** The relic Family Heirloom draws. */
  private val top = CatalogNames.relic("Dowsing Sticks")

  /** A Search that draws only `cards`, with 5 Supply and Dowsing Sticks on
    * top of the relic deck. */
  private def searching(cards: Vector[WorldCardId]): Table =
    Table.from(staged(cards, supply = 5)).relicDeckTop(top)

  private def answer(from: OathTransition, choice: DecisionAnswer) =
    rules.resolveWalker(from.state, p1, FamilyHeirloom.decisionId, choice)

  private def relics(ready: ReadyGame) = Look(ready).player(p1).relics

  test("a searched Family Heirloom played faceup asks its question and keeps " +
      "the relic"):
    val ready = searching(Vector(heirloom)).ready
    val done = accepted(for
      started <- start(ready)
      chosen <- keep(started, heirloom)
      placed <- place(chosen, heirloom, "adviser-faceup")
      answered <- answer(placed, take)
    yield answered)
    assert(relics(after(done)).exists(_.id == top))

  test("a searched Family Heirloom played faceup can put the relic on the " +
      "bottom of the deck"):
    val ready = searching(Vector(heirloom)).ready
    val done = accepted(for
      started <- start(ready)
      chosen <- keep(started, heirloom)
      placed <- place(chosen, heirloom, "adviser-faceup")
      answered <- answer(placed, bottom)
    yield answered)
    assert(!relics(after(done)).exists(_.id == top))
    assertEquals(after(done).game.current.commonCards.relicDeck.last, top)

  test("a facedown Family Heirloom played faceup asks its question and keeps " +
      "the relic"):
    val drawing = searching(Vector.empty).ready
    val asAdviser = PowerFixture.asAdviser(drawing, heirloom,
      Orientation.FaceDown)
    val ready = PowerFixture.inPhase(asAdviser, Phase.Act)
    val done = accepted(for
      started <- rules.startWalker(OathState.Ready(ready),
        ActionRef.PlayFacedownAdviser, p1, Vector.empty,
        Vector(DecisionOptionRef.Denizen(heirloom)))
      placed <- place(started, heirloom, "adviser-faceup")
      answered <- answer(placed, take)
    yield answered)
    assert(relics(after(done)).exists(_.id == top))

  test("a replacement is answered before the card is played, and the " +
      "question after the play still resolves"):
    // The adviser area holds 3, so these fill it.
    val wardOfSilence = CatalogNames.denizen("Ward of Silence")
    val full = searching(Vector(heirloom)).adviser(p1, wardOfSilence)
      .adviser(p1, "Arcane Brokers").adviser(p1, "Arcane Armor").ready
    val placed = accepted(for
      started <- start(full)
      chosen <- keep(started, heirloom)
      placed <- place(chosen, heirloom, "adviser-faceup")
    yield placed)
    // With every adviser slot taken the play first asks what to replace.
    val replaced = accepted(SearchFixture.replace(placed, heirloom,
      wardOfSilence))
    val done = accepted(answer(replaced, take))
    assert(relics(after(done)).nonEmpty)
    assert(!Look(after(done)).player(p1).advisers.exists(
      _.id == wardOfSilence))

package oathdigital.gameplay.powers.search

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Wine of Welcome (relic R33): a Search of the world deck draws from its
  * bottom, stopping after a Vision as usual (catalog batch 6, slice 3). */
class WineOfWelcomeSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val modifiers = Vector(WineOfWelcome.id)
  /** The world deck's bottom, last named at the very bottom. */
  private val bottom = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))

  /** p1 at Ancient City holding Wine of Welcome faceup, with 5 Supply and
    * one card in the Cradle discard pile. */
  private def holdingWine: Table = Table.start.pawn(p1, "Ancient City")
    .relic(p1, "Wine of Welcome").supply(p1, 5)
    .discarded(Region.Cradle, "Wrestlers")

  private def searchedWorld(table: Table): ReadyGame =
    SearchFixture.after(accepted(SearchFixture.start(table.ready,
      modifiers)))

  test("picked, a world Search draws the bottom three, bottom first"):
    val after = searchedWorld(holdingWine.worldDeckBottom(bottom*))
    assertEquals(after.game.current.temporaryHands(p1), bottom.reverse)
    assertEquals(Look(after).supply(p1), 5 - 2)

  test("the draw from the bottom stops after a Vision"):
    val table = holdingWine.worldDeckBottom(bottom(0), VisionRules.Faith,
      bottom(1))
    val before = table.ready.game.current.tracks.visionsDrawn
    val after = searchedWorld(table)
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(bottom(1), VisionRules.Faith))
    assertEquals(after.game.current.tracks.visionsDrawn, before + 1)

  test("while it is picked, a discard pile warns and stays choosable"):
    val shown = ShownOptions.sourcesAfterPicking(holdingWine.ready,
      modifiers)
    assertEquals(ShownOptions.optionFor(shown, SearchSource.ref(
      SearchSource.RegionalDiscard(Region.Cradle))).map(option =>
      (option.state, option.reason)),
      Some(("warning", Some(WineOfWelcome.discardWarning))))
    assertEquals(ShownOptions.optionFor(shown,
      SearchSource.ref(SearchSource.WorldDeck)).map(_.state),
      Some("available"))

  test("Wine of Welcome and Mushrooms warn each other"):
    val ready = holdingWine.denizen("Mushrooms", at = "Ancient City").ready
    val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1))
    val decisionId =
      ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)
    def shownAfterPicking(power: PowerId) = ShownOptions.atParkedDecision(
      accepted(rules.resolveWalker(begun.state, p1, decisionId,
        ModifierPlay.pick(power))).state)
    val warned = Some(("warning", Some(WineOfWelcome.withMushrooms)))
    assertEquals(ShownOptions.optionFor(shownAfterPicking(Mushrooms.id),
      DecisionOptionRef.Relic(WineOfWelcomeCard.id)).map(option =>
      (option.state, option.reason)), warned)
    assertEquals(ShownOptions.optionFor(shownAfterPicking(WineOfWelcome.id),
      DecisionOptionRef.Denizen(MushroomsCard.id)).map(option =>
      (option.state, option.reason)), warned)

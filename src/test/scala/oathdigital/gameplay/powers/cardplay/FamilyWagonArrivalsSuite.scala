package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.action.{Enchantress, PiedPiper}
import oathdigital.gameplay.powers.wake.HornedMask
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.LogNoted
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PowerRules, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Family Wagon's bury after the arrival routes other than card play:
  * Horned Mask, a swap (Enchantress) and Pied Piper's move. They run under
  * the production walker and phase powers, so the arrival pass hears them.
  */
class FamilyWagonArrivalsSuite extends munit.FunSuite:
  private val rules = PowerRules.withWalkerAndPhasePowers
  private val power = FamilyWagon.forCatalog(catalog)
  private val wagon = CatalogNames.denizen("Family Wagon")
  // Nomads.
  private val elders = CatalogNames.denizen("Elders")
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  private val lancers = CatalogNames.denizen("Lancers")
  /** Site-only: Horned Mask takes it as a facedown adviser. */
  private val inn = CatalogNames.denizen("Wayside Inn")
  private val mask = CatalogNames.relic("Horned Mask")
  private val enchantress = CatalogNames.denizen("Enchantress")
  private val piper = CatalogNames.denizen("Pied Piper")

  /** p1 starts the power `id` from `source`. */
  private def started(ready: ReadyGame, id: PowerId,
      source: DecisionOptionRef): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.UsePower(id), p1,
      Vector(source)))

  /** p1 answers `decision` with `ref`. */
  private def answered(from: OathTransition, decision: String,
      ref: DecisionOptionRef): OathTransition =
    accepted(rules.resolveWalker(from.state, p1, decision,
      ChooseOneAnswer(ref)))

  private def wagonLines(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(FamilyWagon.id, power.noteKeys, events)

  private def worldDeck(state: OathState): Vector[WorldCardId] =
    Outcomes.ready(state).game.current.commonCards.worldDeck

  test("Horned Mask: the holder has no limit, so the Mask takes the " +
      "denizen without a discard, and Family Wagon buries it, since it " +
      "arrives facedown"):
    val ready = Table.start.turn(p1, Phase.Wake).pawn(p1, "Ancient City")
      .relic(p1, mask).adviser(p1, wagon).adviser(p1, elders)
      .adviser(p1, rainBoots).adviser(p1, lancers)
      .denizen(inn, at = "Ancient City").ready
    val asked = started(ready, HornedMask.id, DecisionOptionRef.Relic(mask))
    val done = answered(asked, HornedMask.denizenDecisionId,
      DecisionOptionRef.Denizen(inn))
    assertEquals(Look(Outcomes.ready(done.state)).advisers(p1),
      Vector[CardId](wagon, elders, rainBoots, lancers))
    assertEquals(worldDeck(done.state).lastOption, Some(inn))
    assertEquals(wagonLines(done.events), Vector(NoteText.Said("buried",
      s"Buried ${inn.value}.", covers = true)))

  test("a swap: Enchantress given into a Family Wagon holder's advisers is " +
      "buried, its secret going to the active player, and the line names " +
      "the holder"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, enchantress)
      .secrets(p1, 1).adviser(p2, wagon).adviser(p2, elders).ready
    val asked = started(ready, Enchantress.id,
      DecisionOptionRef.Denizen(enchantress))
    val done = answered(asked, Enchantress.decisionId,
      DecisionOptionRef.Denizen(elders))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.advisers(p1), Vector[CardId](elders))
    assertEquals(after.advisers(p2), Vector[CardId](wagon))
    assertEquals(worldDeck(done.state).lastOption, Some(enchantress))
    assertEquals(after.faceDownSecrets(p1), Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(wagonLines(done.events), Vector(NoteText.Said(
      "buried.holder", s"${p2.value} buried ${enchantress.value}.",
      covers = true)))

  test("Pied Piper moved to a Family Wagon holder is buried; the favor is " +
      "still taken, its secret goes to the active player, and Family " +
      "Wagon's line comes first"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, "Ancient City")
      .adviser(p1, piper).secrets(p1, 1)
      .pawn(p2, "Buried Giant").adviser(p2, wagon).favor(p2, 3).ready
    val asked = started(ready, PiedPiper.id, DecisionOptionRef.Denizen(piper))
    val done = answered(asked, PiedPiper.decisionId,
      DecisionOptionRef.Player(p2))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.advisers(p2), Vector[CardId](wagon))
    assertEquals(after.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(worldDeck(done.state).lastOption, Some(piper))
    assertEquals(after.faceDownSecrets(p1), Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(wagonLines(done.events), Vector(NoteText.Said(
      "buried.holder", s"${p2.value} buried ${piper.value}.", covers = true)))
    assertEquals(NoteText.said(PiedPiper, done.events).map(_.text), Vector(
      s"Moved to ${p2.value}'s advisers and took 2 favor from them."))
    assertEquals(done.events.collect { case LogNoted(id, _, _) => id },
      Vector(FamilyWagon.id, PiedPiper.id))

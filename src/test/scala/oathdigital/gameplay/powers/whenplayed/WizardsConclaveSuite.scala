package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.targeting.LostTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class WizardsConclaveSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[WizardsConclave]
  private val conclave = power.cardId
  private val played = hook(conclave)

  private def heldBy(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.banners.darkestSecret.holder

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def took(player: PlayerId): NoteText.Said = NoteText.Said("took",
    s"${player.value} took the Darkest Secret.", covers = false)

  private def playerOptions(players: PlayerId*): Vector[DecisionOption] =
    players.toVector.map(player =>
      DecisionOption.Player(DecisionOptionRef.Player(player)))

  private def options(asked: Decide)(using munit.Location)
      : Vector[DecisionOption] = asked.query match
    case query: DecisionQuery.ChooseOne => query.options
    case other => fail(s"expected a ChooseOne, got $other")

  private def chose(player: PlayerId): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(player))

  test("the player ruling the most Arcane cards takes the Darkest Secret, " +
      "its secrets with it"):
    // p1 rules Wizard's Conclave; p2 rules Disciples and Glamor.
    val ready = Table.start.adviser(p1, conclave).adviser(p2, "Disciples")
      .adviser(p2, "Glamor").darkestSecret(Some(p3), 2).ready
    val done = Outcomes.finished(play(ready, power, conclave))
    assertEquals(heldBy(done.treeless), Some(p2))
    assertEquals(done.treeless.game.current.banners.darkestSecret.secrets, 2)
    assertEquals(said(done.events), Vector(took(p2)))
    assertEquals(replayed(ready, done.events), done.treeless)

  test("p1 decides a tie among the tied players, themselves included"):
    // The quiet table's Darkest Secret is unheld.
    val ready = Table.start.adviser(p1, conclave).adviser(p2, "Glamor").ready
    val first = Outcomes.parked(play(ready, power, conclave))
    val asked = question(ready, power, played, first.tree)
    assertEquals(asked.owner, p1)
    assertEquals(options(asked), playerOptions(p1, p2))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      WizardsConclave.decisionId, chose(p1)))
    assertEquals(heldBy(done.treeless), Some(p1))
    assertEquals(said(done.events), Vector(took(p1)))

  test("when nobody rules an Arcane card, every player ties"):
    // Wizard's Conclave stands at Buried Giant, which nobody rules.
    val giant = CatalogNames.site("Buried Giant")
    val atGiant = hookAt(conclave, giant)
    val ready = Table.start.denizen(conclave, at = giant).ready
    val first = Outcomes.parked(fire(ready, power, atGiant))
    assertEquals(options(question(ready, power, atGiant, first.tree)),
      playerOptions(p1, p2, p3))
    val done = Outcomes.finished(resume(ready, power, atGiant, first.tree,
      WizardsConclave.decisionId, chose(p3)))
    assertEquals(heldBy(done.treeless), Some(p3))

  test("a winner who already holds the Darkest Secret keeps it"):
    val ready = Table.start.adviser(p1, conclave).adviser(p2, "Disciples")
      .adviser(p2, "Glamor").darkestSecret(Some(p2), 1).ready
    val done = Outcomes.finished(play(ready, power, conclave))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("kept",
      s"${p2.value} kept the Darkest Secret.", covers = false)))

  test("when Lost Tongue refuses the take, the Darkest Secret does not move"):
    // p2 holds the Darkest Secret and Lost Tongue faceup; p1, the winner,
    // rules no Nomad card.
    val ready = Table.start.adviser(p1, conclave).adviser(p2, "Lost Tongue")
      .darkestSecret(Some(p2), 1).ready
    val done = Outcomes.finished(playBeside(ready, power, conclave,
      LostTongue.forCatalog(catalog)))
    assertEquals(heldBy(done.treeless), Some(p2))
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "The Darkest Secret did not move.", covers = false)))

package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.walker.{LogNoted, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class ARoundOfAleSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[ARoundOfAle]
  private val ale = power.cardId
  private val atHome = hookAt(ale, homeSite)
  private val peaks = CatalogNames.site("Broken Peaks")
  private val cleanupLine = NoteText.Said("cleanup",
    "Triggering resource cleanup.", covers = false)

  test("favor on every card returns to its bank, card secrets go to p1, and " +
      "p1's facedown secrets turn faceup"):
    // Homesteaders (Hearth), p2's adviser, holds 2 favor. Defame (Discord)
    // at Broken Peaks holds 1 favor and 1 secret. p3's Painted Trumpet holds
    // 1 secret. p1 holds 1 faceup and 2 facedown secrets, p2 1 facedown.
    val ready = Table.start.denizen(ale, at = homeSite)
      .adviser(p2, "Homesteaders").tokens("Homesteaders", favor = 2)
      .denizen("Defame", at = peaks)
      .tokens("Defame", favor = 1, secrets = 1)
      .relic(p3, "Painted Trumpet").tokens("Painted Trumpet", secrets = 1)
      .secrets(p1, faceUp = 1, faceDown = 2)
      .secrets(p2, faceUp = 0, faceDown = 1).ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    val (before, end) = (Look(ready), Look(done.treeless))
    assertEquals(end.bankFavor(Suit.Hearth), before.bankFavor(Suit.Hearth) + 2)
    assertEquals(end.bankFavor(Suit.Discord),
      before.bankFavor(Suit.Discord) + 1)
    Vector("Homesteaders", "Defame", "Painted Trumpet").foreach(card =>
      assertEquals(end.tokensOn(card), Tokens.empty, card))
    // 1 faceup, 2 turned faceup, and 1 each from Defame and the Trumpet.
    assertEquals((end.faceUpSecrets(p1), end.faceDownSecrets(p1)), (5, 0))
    assertEquals((end.faceUpSecrets(p2), end.faceDownSecrets(p2)), (0, 1))

  test("its line comes before the cleanup step"):
    // One card with favor on it, so the cleanup has a step to record.
    val ready = Table.start.denizen(ale, at = homeSite)
      .adviser(p2, "Homesteaders").tokens("Homesteaders", favor = 2).ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    assertEquals(said(power, done.events), Vector(cleanupLine))
    // The cleanup is the walk's last step, and the line comes before it.
    val notedAt = done.events.indexWhere(_.isInstanceOf[LogNoted])
    val cleanedAt =
      done.events.lastIndexWhere(_.isInstanceOf[WalkerStepRecorded])
    assert(notedAt >= 0 && notedAt < cleanedAt, done.events.toString)

  test("the recorded operations rebuild the end state"):
    val ready = Table.start.denizen(ale, at = homeSite)
      .adviser(p2, "Homesteaders").tokens("Homesteaders", favor = 2)
      .denizen("Defame", at = peaks).tokens("Defame", secrets = 1)
      .secrets(p1, faceUp = 1, faceDown = 2).ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    assertJournalRebuilds(ready, done.events, done.treeless)

  test("League Treaty does not redirect the favor, since no Rest favor " +
      "window opens"):
    // League Treaty at Ancient City, ruled by one of p2's warbands, would ask
    // p2 where the Cradle's card favor goes at Rest.
    val ready = Table.start.warbandsAt("Ancient City", p2, 1)
      .denizen("League Treaty", at = "Ancient City")
      .denizen("Defame", at = "Broken Peaks").tokens("Defame", favor = 2)
      .denizen("Homesteaders", at = "Ancient City")
      .tokens("Homesteaders", favor = 2)
      .denizen(ale, at = peaks).ready
    val done = Outcomes.finished(fireWithEveryPower(ready,
      hookAt(ale, peaks)))
    Vector(Suit.Discord, Suit.Hearth).foreach(suit => assertEquals(
      Look(done.treeless).bankFavor(suit), Look(ready).bankFavor(suit) + 2,
      suit.toString))

  test("with nothing to clean up it writes its line and changes nothing"):
    val ready = Table.start.denizen(ale, at = homeSite).ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(cleanupLine))

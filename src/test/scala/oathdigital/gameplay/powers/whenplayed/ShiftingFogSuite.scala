package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.p1

class ShiftingFogSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = ShiftingFog
  private val card = power.cardId
  /** p1 holds Shifting Fog faceup. */
  private val holding = Table.start.adviser(p1, card)

  test("Shifting Fog is in the default walker catalog"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))

  test("every bank's favor moves at once to the next bank"):
    val ready = holding.banksHoldingOnly(Suit.Discord -> 1, Suit.Arcane -> 2,
      Suit.Order -> 3, Suit.Hearth -> 4, Suit.Beast -> 5, Suit.Nomad -> 6).ready
    val done = Outcomes.finished(play(ready, power, card))
    assertEquals(Suit.all.map(done.treeless.banks.favor(_)),
      Vector(6, 1, 2, 3, 4, 5))
    assertJournalRebuilds(ready, done.events, done.treeless)
    assertEquals(said(power, done.events),
      Vector(NoteText.Said("moved",
        "Every bank's favor moved to the next bank.", covers = false)))

  test("equal banks still move, and the line is written"):
    val ready = holding.banksHoldingOnly(Suit.all.map(_ -> 2)*).ready
    val done = Outcomes.finished(play(ready, power, card))
    // One move per bank, and there are 6 banks.
    assertEquals(PhasePowerHarness.ops(done.events).size, 6)
    assertEquals(said(power, done.events).size, 1)

  test("with every bank empty nothing moves and nothing is written"):
    val ready = holding.banksHoldingOnly().ready
    val done = Outcomes.finished(play(ready, power, card))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events),
      Vector.empty)

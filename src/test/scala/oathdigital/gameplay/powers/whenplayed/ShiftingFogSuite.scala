package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class ShiftingFogSuite extends munit.FunSuite:
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = ShiftingFog.forCatalog(catalog).get
  private val card = power.cardId
  private def banked(ready: ReadyGame, favor: Map[Suit, Int]) =
    ready.copy(banks = ready.banks.copy(favor = favor))
  private val staged = asAdviser(base, card)

  test("Shifting Fog is in the default walker catalog"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))

  test("the next bank to the right, Nomad's going to Discord"):
    assertEquals(Suit.all.map(ShiftingFog.next), Vector(Suit.Arcane,
      Suit.Order, Suit.Hearth, Suit.Beast, Suit.Nomad, Suit.Discord))

  test("every bank's favor moves at once to the next bank"):
    val ready = banked(staged, Suit.all.zipWithIndex.map((suit, i) =>
      suit -> (i + 1)).toMap)
    val done = finished(play(ready, power, card))
    assertEquals(Suit.all.map(done.treeless.banks.favor(_)),
      Vector(6, 1, 2, 3, 4, 5))
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("moved",
        "Every bank's favor moved to the next bank.", covers = false)))

  test("equal banks still move, and the line is written"):
    val ready = banked(staged, Suit.all.map(_ -> 2).toMap)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events).size, 6)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events).size, 1)

  test("with every bank empty nothing moves and nothing is written"):
    val ready = banked(staged, Suit.all.map(_ -> 0).toMap)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector.empty)

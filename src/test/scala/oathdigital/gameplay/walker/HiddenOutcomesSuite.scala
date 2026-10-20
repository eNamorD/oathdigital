package oathdigital.gameplay.walker

import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.p1

/** Which operations show a player something the chooser cannot see, and how
  * far each of the two questions looks (hidden outcome depth decision).
  */
class HiddenOutcomesSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Horse Archers")
  private val peek = Peek(p1, card, Location.PlayArea(p1))

  test("peeks, reveals, rolls, rerolls and cards turned faceup hide the " +
      "outcome; other operations do not"):
    assert(HiddenOutcomes.hides(peek))
    assert(HiddenOutcomes.hides(Reveal(card, Location.PlayArea(p1))))
    assert(HiddenOutcomes.hides(Flip(card, Location.PlayArea(p1),
      Orientation.FaceUp)))
    assert(HiddenOutcomes.hides(Roll(PoolKey("test.roll"),
      DiceSpec(DiceKind.Attack), RollMode.Automatic)))
    assert(HiddenOutcomes.hides(Reroll(PoolKey("test.roll"), Vector(0))))
    assert(!HiddenOutcomes.hides(Flip(card, Location.PlayArea(p1),
      Orientation.FaceDown)))
    assert(!HiddenOutcomes.hides(GainSupply(p1, 1)))

  test("a composite carrying a hidden operation hides at no node of its " +
      "own, and hides anywhere beneath it"):
    val take = Take(Piece.Favor(1), p1, Location.FavorBank(Suit.Discord),
      Location.PlayArea(p1), leaving = Vector(peek))
    assert(!HiddenOutcomes.hides(take))
    assert(HiddenOutcomes.hidesAnywhere(take))

  test("a Defer holds its operations out of sight of both questions, " +
      "because nothing under it runs"):
    val held = Defer(DeferralKey("test.held"), Vector(peek))
    assert(!HiddenOutcomes.hides(held))
    assert(!HiddenOutcomes.hidesAnywhere(held))

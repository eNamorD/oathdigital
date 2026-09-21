package oathdigital.gameplay

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powers.PowerFixture.base
import oathdigital.model._

/** Revealing the top card of a regional discard is a public no-op: a discarded
  * card has no orientation state, so nothing changes, and a facedown flip of
  * it is still not accepted.
  */
class RevealDiscardSuite extends munit.FunSuite {
  private val region = Region.Cradle
  private val at = Location.RegionalDiscard(region)
  private val top = base.game.current.commonCards.discard(region).last
  private def run(ops: CoreOperation*) = OperationPipeline.run(base,
    ops.toVector, OperationPolicy.Permissive)(Right(_))

  test("revealing a discarded card is accepted and changes no state") {
    val result = run(Reveal(top, at)).toOption.get
    assertEquals(result.state, base)
    assertEquals(result.executed, Vector(Reveal(top, at)))
  }

  test("a card that is not in that discard cannot be revealed there") {
    val other = base.game.current.commonCards.discard(Region.Provinces).last
    assert(run(Reveal(other, at)).isLeft)
  }

  test("a facedown flip of a discarded card stays unsupported") {
    assert(run(Flip(top, at, Orientation.FaceDown)).isLeft)
  }
}

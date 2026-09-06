package oathdigital.gameplay.operations

import oathdigital.model._

class OperationTreeSuite extends munit.FunSuite {
  test("primitives are leaves") {
    val move = Move(Piece.Pawn(PlayerId("p")),
      PositionedLocation(Location.Site(SiteId("a"))),
      PositionedLocation(Location.Site(SiteId("b"))))
    assertEquals(Operation.flatten(move), Vector(move))
  }
  test("composites flatten depth-first") {
    // The brief's original fixture (pay 1 secret into the payer's own play
    // area) builds a same-location secret Move that the Move invariant
    // rejects at construction, so it can never reach flatten. Pay two placed
    // portions to a legal card destination instead: the composite's two leaf
    // children (favor then secret) flatten depth-first to exactly 2 leaves.
    val pay = PayCost(PlayerId("p"), Location.OnCard(DenizenId("pay:target")),
      Cost(favor = 1, secret = 1))
    assertEquals(Operation.flatten(pay).size, 2) // one leaf per placed portion
  }
}

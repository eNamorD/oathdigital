package oathdigital.gameplay

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

/** Revealing the top card of a regional discard is a public no-op: a discarded
  * card has no orientation state, so nothing changes, and a facedown flip of
  * it is still not accepted.
  */
class RevealDiscardSuite extends munit.FunSuite:
  private val region = Region.Cradle
  private val at = Location.RegionalDiscard(region)
  /** Errand Boy tops the Cradle's discard; Wrestlers lies in the Provinces'. */
  private val base = Table.start
    .discarded(Region.Cradle, "Rain Boots", "Errand Boy")
    .discarded(Region.Provinces, "Wrestlers")
    .ready
  private val top = CatalogNames.denizen("Errand Boy")
  private def run(ops: CoreOperation*) = OperationPipeline.run(base,
    ops.toVector, OperationPolicy.Permissive)(Right(_))

  test("revealing a discarded card is accepted and changes no state"):
    val result = run(Reveal(top, at)).toOption.get
    assertEquals(result.state, base)
    assertEquals(result.executed, Vector(Reveal(top, at)))

  test("a card that is not in that discard cannot be revealed there"):
    val other = CatalogNames.denizen("Wrestlers")
    assertEquals(run(Reveal(other, at)).left.toOption, Some(OathViolation.CoreOperationRejected("missing-piece",
      "denizen card is not present at regional discard")))

  test("a facedown flip of a discarded card stays unsupported"):
    assertEquals(run(Flip(top, at, Orientation.FaceDown)).left.toOption, Some(OathViolation.CoreOperationRejected(
      "unsupported-orientation",
      "card kind denizen cannot use orientation at RegionalDiscard(Cradle)")))

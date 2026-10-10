package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Unwrap.as

/** `SupplyStrip` finds the actor's Supply payments wherever the cost window
  * holds them (operation rewrite design, slice 2). Pure: no walker. */
class SupplyStripSuite extends munit.FunSuite:
  private val actor = Table.p1
  private val ready = Table.start.ready
  private val pending = PendingTree(Vector("0"), Vector.empty)
  private val window = PowerWindow.TravelCost
  private val pay: SpendSupply = SpendSupply(actor, 2)
  private val othersPay: SpendSupply = SpendSupply(Table.p2, 2)

  /** The context of folding the cost window, a windowed `Sequence`. */
  private val folding: PowerCtx = PowerCtx(ready, actor,
    RuleSourceRef.GameRule("test"), window, Vector("0"),
    Sequence(Vector.empty, Some(window)))

  test("the actor's payment is removed in the tree, in what a Branch " +
      "selects and in what a BuildOps builds"):
    val stripped = SupplyStrip(folding, actor, Vector(pay,
      Branch((_, _) => Vector(pay)),
      BuildOps((_, _) => Right(Vector(pay, othersPay)))))
    assertEquals(stripped.head, Sequence(Vector.empty))
    assertEquals(as[Branch](stripped(1)).select(ready, pending),
      Vector[Operation](Sequence(Vector.empty)))
    assertEquals(as[BuildOps](stripped(2)).build(ready, pending),
      Right(Vector[CoreOperation](othersPay)))

  test("another player's payment and a node with its own window are left " +
      "alone"):
    val nested = Sequence(Vector(pay),
      Some(PowerWindow.TravelActionEligibility))
    val stripped = SupplyStrip(folding, actor, Vector(othersPay, nested))
    assertEquals(stripped.head, othersPay)
    assert(stripped(1) eq nested)

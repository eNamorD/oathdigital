package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributionCollector, ContributingPower, PowerCtx, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.model._

/** The named ignore is decided with the context the powers are gathered in, so
  * a power can ignore another only for the node at hand. `shouldIgnore` (with no
  * context) still works: `ignores` defaults to it.
  */
class ContributionIgnoresSuite extends munit.FunSuite {
  private val window = PowerWindow.TravelCost
  private val actor = initialReady.game.current.turn.activePlayer

  private def power(name: String,
      ignoring: (PowerCtx, ContributingPower) => Boolean = (_, _) => false)
      : ContributingPower = new ContributingPower {
    def id: PowerId = PowerId(name)
    def source: RuleSourceRef = RuleSourceRef.GameRule(name)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(window -> Vector(Transform((_, ops) => ops)))
    override def ignores(ctx: PowerCtx, candidate: ContributingPower): Boolean =
      ignoring(ctx, candidate)
  }

  private def ctxAt(operation: Operation)(candidate: ContributingPower)
      : PowerCtx = PowerCtx(initialReady, actor, candidate.source, window,
    Vector.empty, operation)

  private def survivors(powers: Vector[ContributingPower], operation: Operation)
      : Vector[PowerId] = ContributionCollector.gather(window, powers,
    ctxAt(operation)).order

  private val plain = power("test.plain")
  private val marker = SpendSupply(actor, 1)
  private val other = SpendSupply(actor, 2)

  test("a context-free shouldIgnore still ignores, through the default") {
    val quiet = new ContributingPower {
      def id: PowerId = PowerId("test.quiet")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        Map(window -> Vector.empty)
      override def shouldIgnore(candidate: ContributingPower): Boolean =
        candidate.id == plain.id
    }
    assertEquals(survivors(Vector(plain, quiet), marker), Vector(quiet.id))
  }

  test("a power can ignore another for one node and not for another") {
    val picky = power("test.picky", ignoring = (ctx, candidate) =>
      candidate.id == plain.id && ctx.operation == marker)
    assertEquals(survivors(Vector(plain, picky), marker), Vector(picky.id))
    assertEquals(survivors(Vector(plain, picky), other).toSet,
      Set(plain.id, picky.id))
  }

  test("one pass: a power that is itself ignored still has its ignore counted") {
    val first = power("test.first", ignoring = (_, candidate) =>
      candidate.id.value == "test.second")
    val second = power("test.second", ignoring = (_, candidate) =>
      candidate.id.value == "test.third")
    val third = power("test.third")
    assertEquals(survivors(Vector(first, second, third), marker),
      Vector(first.id))
  }
}

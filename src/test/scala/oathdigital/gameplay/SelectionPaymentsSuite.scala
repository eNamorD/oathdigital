package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Selecting modifiers refuses a combination the player cannot pay, at
  * selection, because every selected modifier pays at the start of its action.
  */
class SelectionPaymentsSuite extends munit.FunSuite {
  private val actor = EconomyFixture.act().game.current.turn.activePlayer

  /** A selectable Muster modifier that burns `secrets` secrets when selected. */
  private def burning(name: String, secrets: Int): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
      override def resolution: PowerResolution = PowerResolution.PlayerSelected
      override def selectionPayments(ready: ReadyGame, player: PlayerId) =
        Vector(PayCost(player, Location.SharedBank,
          Cost(secretBurnt = secrets)))
    }
  private val a = burning("test.burns-a", 1)
  private val b = burning("test.burns-b", 1)
  private val free: ContributingPower = new ContributingPower {
    def id: PowerId = PowerId("test.free")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
  }

  private def muster(secrets: Int, selected: ContributingPower*)
      : Either[OathViolation, OathTransition] = {
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(a, b, free)))
    rules.startWalker(Ready(EconomyFixture.act(secrets = secrets)),
      ActionRef.Muster, actor, selected.map(_.id).toVector)
  }

  test("a selection whose payments can all be made is accepted") {
    assert(muster(2, a, b).isRight)
    assert(muster(1, a).isRight)
    assert(muster(1, free).isRight)
  }

  test("two payments that need the only secret are refused at selection") {
    val refused = muster(1, a, b)
    assert(refused.left.toOption.exists(_ match {
      case OathViolation.InvalidEventOrder(detail) =>
        detail.contains("cannot all be paid together")
      case _ => false
    }), refused.toString)
  }

  test("each payment alone is affordable, so the refusal is about the pair") {
    assert(muster(1, a).isRight)
    assert(muster(1, b).isRight)
  }

  test("a free power adds nothing to the payments") {
    assert(muster(1, a, free).isRight)
  }

  test("Catacombs states its secret as a selection payment") {
    val fixture = CatacombsContributionSuite.reliclessSite(secrets = 1)
    val power = oathdigital.gameplay.powers.recover.CatacombsContribution
      .forCatalog(catalog).get
    assertEquals(power.selectionPayments(fixture.ready, fixture.actor).size, 1)
    val rules = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(power)))
    assert(rules.startWalker(Ready(fixture.ready), ActionRef.Recover,
      fixture.actor, Vector(power.id)).isRight)
  }

  test("Catacombs with no faceup secret is refused at selection, not mid-action") {
    val fixture = CatacombsContributionSuite.reliclessSite(secrets = 0)
    val power = oathdigital.gameplay.powers.recover.CatacombsContribution
      .forCatalog(catalog).get
    val rules = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(power)))
    val refused = rules.startWalker(Ready(fixture.ready), ActionRef.Recover,
      fixture.actor, Vector(power.id))
    assert(refused.left.toOption.exists(_.toString.contains(
      "cannot all be paid together")), refused.toString)
  }
}

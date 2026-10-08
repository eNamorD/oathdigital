package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatacombsGame, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Selecting modifiers refuses a combination the player cannot pay, at
  * selection, because every selected modifier pays at the start of its action.
  */
class SelectionPaymentsSuite extends munit.FunSuite:
  /** Stub of ContributingPower: a PlayerSelected modifier whose selection
    * burns `secrets` secrets. */
  private def burning(name: String, secrets: Int): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
      override def resolution: PowerResolution = PowerResolution.PlayerSelected
      override def selectionPayments(ready: ReadyGame, player: PlayerId): Vector[CoreOperation] =
        Vector(PayCost(player, Location.SharedBank,
          Cost(secretBurnt = secrets)))
  private val a = burning("test.burns-a", 1)
  private val b = burning("test.burns-b", 1)
  /** Stub of ContributingPower: a PlayerSelected modifier whose selection
    * pays nothing. */
  private val free: ContributingPower = new ContributingPower:
    def id: PowerId = PowerId("test.free")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def resolution: PowerResolution = PowerResolution.PlayerSelected

  private def muster(secrets: Int, selected: ContributingPower*)
      : Either[OathViolation, OathTransition] =
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(a, b, free)))
    // The start's 1 favor and 7 Supply pay for a Muster from the Alchemist
    // at Ancient City; only the selected powers' secrets are in question.
    val ready = Table.start.pawn(p1, at = "Ancient City")
      .denizen("Alchemist", at = "Ancient City")
      .secrets(p1, faceUp = secrets).ready
    rules.startWalker(Ready(ready), ActionRef.Muster, p1,
      selected.map(_.id).toVector)

  test("a selection whose payments can all be made is accepted"):
    accepted(muster(2, a, b))
    accepted(muster(1, a))
    accepted(muster(1, free))

  test("two payments that need the only secret are refused at selection"):
    val refused = muster(1, a, b)
    assertEquals(refused, Left(OathViolation.InvalidEventOrder(
      "the selected modifiers cannot all be paid together: " +
        "CoreOperationRejected(insufficient-pieces,player play area " +
        "contains 0 of requested secrets)")))
    // Either payment alone is affordable (b is a's twin), so the refusal is
    // about the pair.
    accepted(muster(1, a))

  test("a free power adds nothing to the payments"):
    accepted(muster(1, a, free))

  test("Catacombs states its secret as a selection payment"):
    val fixture = CatacombsGame.reliclessSite(secrets = 1)
    val power = oathdigital.gameplay.powers.recover.CatacombsContribution
      .forCatalog(catalog)
    assertEquals(power.selectionPayments(fixture.ready, fixture.actor),
      Vector(oathdigital.gameplay.operations.Costs.onCard(fixture.actor, power.cardId, Cost(secret = 1), catalog)))
    val rules = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(power)), walkerDice = WalkerDiceFixture.blanks)
    accepted(rules.startWalker(Ready(fixture.ready), ActionRef.Recover,
      fixture.actor, Vector(power.id)))

  test("Catacombs with no faceup secret is refused at selection, not mid-action"):
    val fixture = CatacombsGame.reliclessSite(secrets = 0)
    val power = oathdigital.gameplay.powers.recover.CatacombsContribution
      .forCatalog(catalog)
    val rules = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(power)))
    val refused = rules.startWalker(Ready(fixture.ready), ActionRef.Recover,
      fixture.actor, Vector(power.id))
    // It is not offered at all, so the start refuses it as inapplicable.
    assertEquals(refused, Left(OathViolation.InvalidEventOrder(
      s"power ${power.id.value} is not applicable to this recover")))

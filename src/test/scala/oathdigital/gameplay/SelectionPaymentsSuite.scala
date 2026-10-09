package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatacombsGame, Table}
import oathdigital.testkit.Unwrap.accepted

/** Selecting modifiers for an action that still takes them at its start
  * refuses a combination the player cannot pay, at selection, because every
  * selected modifier pays at the start of its action. Search, Travel, Muster
  * and Trade pay at each pick instead (modifier walker design). */
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

  /** p1 starts a Recover at a site holding a facedown relic, with `secrets`
    * faceup secrets, selecting `selected`. Only the selected powers' secrets
    * are in question. */
  private def recoverSelecting(secrets: Int, selected: ContributingPower*)
      : Either[OathViolation, OathTransition] =
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(a, b, free)),
      walkerDice = WalkerDiceFixture.blanks)
    val fixture = CatacombsGame.relicSite()
    val ready = Table.from(fixture.ready)
      .secrets(fixture.actor, faceUp = secrets).ready
    rules.startWalker(Ready(ready), ActionRef.Recover, fixture.actor,
      selected.map(_.id).toVector)

  test("a selection whose payments can all be made is accepted"):
    accepted(recoverSelecting(2, a, b))
    accepted(recoverSelecting(1, a))
    accepted(recoverSelecting(1, free))

  test("two payments that need the only secret are refused at selection"):
    val refused = recoverSelecting(1, a, b)
    assertEquals(refused, Left(OathViolation.InvalidEventOrder(
      "the selected modifiers cannot all be paid together: " +
        "CoreOperationRejected(insufficient-pieces,player play area " +
        "contains 0 of requested secrets)")))
    // Either payment alone is affordable (b is a's twin), so the refusal is
    // about the pair.
    accepted(recoverSelecting(1, a))

  test("a free power adds nothing to the payments"):
    accepted(recoverSelecting(1, a, free))

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

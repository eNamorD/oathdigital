package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BanditPaymasterSuite extends munit.FunSuite:
  import TargetsFixture._

  private val paymaster = CatalogNames.denizen("Bandit Paymaster")
  private val source = DecisionOptionRef.Denizen(paymaster)
  private val home = Table.homeOf(p1)

  /** p1's Act holding Bandit Paymaster, with `bandits` bandit warbands at
    * p1's site. */
  private def staged(bandits: Int, favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, paymaster).favor(p1, favor)
      .bandits(home, bandits)

  test("with two bandits at the site, one is removed and the player gains " +
      "3 warbands"):
    val ready = staged(2).ready
    val t = use(ready, BanditPaymaster, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(paymaster), Tokens(1, 0))
    assertEquals(Look(end).forces(home),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(Look(end).warbands(p1), Look(ready).warbands(p1) + 3)
    assertEquals(NoteText.said(BanditPaymaster, t.events), Vector(
      NoteText.Said(NoteKey.Used, s"Removed 1 bandit warband from " +
        s"${home.value}, and ${p1.value} gained 3 warbands.", covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("the last bandit is spared, and the line says so"):
    val ready = staged(1).ready
    val t = use(ready, BanditPaymaster, source).toOption.get
    val end = after(t)
    assertEquals(Look(end).forces(home),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(Look(end).warbands(p1), Look(ready).warbands(p1))
    assertEquals(NoteText.said(BanditPaymaster, t.events), Vector(
      NoteText.Said("used.none", s"${home.value} had no bandit to spare.",
        covers = false)))

  test("a short supply gains what it holds"):
    val base = staged(3).ready
    val kind = PlayerForceKind.of(base, Look(base).player(p1)).get
    val ready = staged(3).warbands(p1, base.banks.warbandSupply(kind) - 1)
      .ready
    val t = use(ready, BanditPaymaster, source).toOption.get
    assertEquals(Look(after(t)).warbands(p1), Look(ready).warbands(p1) + 1)
    assertEquals(NoteText.said(BanditPaymaster, t.events), Vector(
      NoteText.Said(NoteKey.Used, s"Removed 1 bandit warband from " +
        s"${home.value}, and ${p1.value} gained 1 warband.", covers = false)))

  test("it is unusable without a favor"):
    val broke = staged(2, favor = 0).ready
    assert(!usableNow(broke).exists(_.power.id == BanditPaymaster.id))
    assert(use(broke, BanditPaymaster, source).isLeft)

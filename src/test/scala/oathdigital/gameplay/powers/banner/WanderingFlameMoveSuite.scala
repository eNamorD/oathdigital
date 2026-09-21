package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WanderingFlameMoveSuite extends munit.FunSuite {
  import PowerFixture._
  import BannerFixture._
  import TargetsFixture.{after, answer, awaits, pick, replayed, use}

  private val power = WanderingFlameMove

  /** The actor holds the banner in the Act phase, and exactly one secret lies
    * on each site in `marked`.
    */
  private def staged(marked: SiteId*): ReadyGame = inPhase(
    marked.foldLeft(withoutSiteSecrets(holdingFlame(base)))(
      withSiteSecrets(_, _, 1)), Phase.Act)

  test("it is a registered phase power with its own id") {
    assert(PhasePowerCatalog.default(catalog).find(power.id).isDefined)
    assertEquals(power.id.value, "banner.darkest-secret.wandering-flame.move")
  }

  test("one secret-bearing site: the pawn goes there with no question, by a " +
      "plain Move that leaves the secret where it is") {
    val start = staged(brokenPeaks)
    val done = use(start, power, darkestSecret).toOption.get
    assert(backToActing(done))
    assertEquals(pawnOf(after(done)), brokenPeaks)
    assertEquals(siteSecrets(after(done), brokenPeaks), 1)
    assertEquals(ops(done.events), Vector[CoreOperation](
      Move(Piece.Pawn(actor), PositionedLocation(Location.Site(ancientCity)),
        PositionedLocation(Location.Site(brokenPeaks)))))
    assertEquals(replayed(start, done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("several candidates: the player chooses among exactly the other " +
      "sites that hold a secret") {
    val start = staged(brokenPeaks, buriedGiant, ancientCity)
    val parked = use(start, power, darkestSecret).toOption.get
    assert(awaits(parked, power.decisionId))
    assert(answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(ancientCity))).isLeft, "the pawn's own site")
    assert(answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(deepWoods))).isLeft, "a site with no secret")
    val done = answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(buriedGiant))).toOption.get
    assert(backToActing(done))
    assertEquals(pawnOf(after(done)), buriedGiant)
    assertEquals(replayed(start, parked.events ++ done.events),
      Right(done.state))
  }

  test("a secret on a card at a site does not count, only one on the site") {
    val card = DenizenId("7")
    val onCard = withCardTokens(atSite(staged(), card, brokenPeaks),
      brokenPeaks, card, Tokens(0, 1))
    assert(!usable(onCard, power.id))
    assert(usable(withSiteSecrets(onCard, brokenPeaks, 1), power.id))
  }

  test("it is unlimited: it can be used again in the same turn") {
    val start = staged(brokenPeaks, buriedGiant)
    val first = use(start, power, darkestSecret).toOption.get
    val parked = answer(first, actor, power.decisionId,
      pick(DecisionOptionRef.Site(brokenPeaks))).toOption.get
    val second = use(after(parked), power, darkestSecret)
    assert(second.isRight, second.toString)
    assertEquals(after(parked).game.current.turn.usedPowers, Set.empty[PowerUseRef])
  }

  test("it is unusable with no other site holding a secret, without the " +
      "banner, or on the Festival face") {
    assert(!usable(staged(), power.id))
    assert(use(staged(), power, darkestSecret).isLeft)
    assert(!usable(staged(ancientCity), power.id), "only the pawn's own site")
    assert(usable(staged(brokenPeaks), power.id))
    val notHeld = inPhase(withSiteSecrets(holdingFlame(base, holder = None),
      brokenPeaks, 1), Phase.Act)
    assert(!usable(notHeld, power.id))
    assert(use(notHeld, power, darkestSecret).isLeft)
    val theirs = inPhase(withSiteSecrets(holdingFlame(base, holder = Some(p1)),
      brokenPeaks, 1), Phase.Act)
    assert(!usable(theirs, power.id))
    val festival = inPhase(withSiteSecrets(
      holdingFlame(base, DarkestSecretFace.Festival), brokenPeaks, 1), Phase.Act)
    assert(!usable(festival, power.id))
    assert(use(festival, power, darkestSecret).isLeft)
  }

  test("it is usable in the Act phase only") {
    val wake = inPhase(withSiteSecrets(holdingFlame(base), brokenPeaks, 1),
      Phase.Wake)
    assert(!usable(wake, power.id))
  }
}

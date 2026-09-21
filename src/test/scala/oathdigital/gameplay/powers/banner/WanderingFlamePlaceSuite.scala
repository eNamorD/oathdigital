package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WanderingFlamePlaceSuite extends munit.FunSuite {
  import PowerFixture._
  import BannerFixture._
  import TargetsFixture.{after, replayed, use, withSecrets}

  private val power = WanderingFlamePlace

  /** The actor holds the banner in the Act phase with the given secrets. */
  private def staged(faceUp: Int, faceDown: Int = 0): ReadyGame = inPhase(
    withSecrets(holdingFlame(base), actor, faceUp, faceDown), Phase.Act)

  test("it is a registered phase power with its own id") {
    assert(PhasePowerCatalog.default(catalog).find(power.id).isDefined)
    assertEquals(power.id.value, "banner.darkest-secret.wandering-flame.place")
    assertNotEquals(power.id, WanderingFlameMove.id)
  }

  test("it moves one secret from the actor's board onto the pawn's site, " +
      "and nothing else changes") {
    val start = staged(faceUp = 2, faceDown = 1)
    val done = use(start, power, darkestSecret).toOption.get
    val end = after(done)
    assert(backToActing(done))
    assertEquals(player(end).board.faceUpSecrets, 1)
    assertEquals(player(end).board.faceDownSecrets, 1)
    assertEquals(siteSecrets(end, ancientCity), siteSecrets(start, ancientCity) + 1)
    assertEquals(ops(done.events), Vector[CoreOperation](
      Move(Piece.Secrets(1), PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(ancientCity)))))
    assertEquals(replayed(start, done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("it places on the site the pawn is at, whichever site that is") {
    val start = TargetsFixture.withPawn(staged(faceUp = 1), actor, brokenPeaks)
    val end = after(use(start, power, darkestSecret).toOption.get)
    assertEquals(siteSecrets(end, brokenPeaks), siteSecrets(start, brokenPeaks) + 1)
    assertEquals(siteSecrets(end, ancientCity), siteSecrets(start, ancientCity))
  }

  test("it is unlimited: each use places another secret") {
    val first = use(staged(faceUp = 3), power, darkestSecret).toOption.get
    val second = use(after(first), power, darkestSecret).toOption.get
    val end = after(second)
    assertEquals(player(end).board.faceUpSecrets, 1)
    assertEquals(siteSecrets(end, ancientCity),
      siteSecrets(staged(faceUp = 3), ancientCity) + 2)
    assertEquals(end.game.current.turn.usedPowers, Set.empty[PowerUseRef])
  }

  test("with no faceup secret it is not usable, and a facedown secret is " +
      "never flipped or moved") {
    Vector(staged(faceUp = 0), staged(faceUp = 0, faceDown = 2)).foreach { start =>
      assert(!usable(start, power.id))
      assert(use(start, power, darkestSecret).isLeft)
    }
    assert(usable(staged(faceUp = 1), power.id))
    val end = after(use(staged(faceUp = 1, faceDown = 2), power, darkestSecret)
      .toOption.get)
    assertEquals(player(end).board.faceUpSecrets, 0)
    assertEquals(player(end).board.faceDownSecrets, 2)
    assert(!usable(end, power.id), "only facedown secrets are left")
  }

  test("it is unusable without the banner, on the Festival face or outside " +
      "the Act phase") {
    val notHeld = inPhase(withSecrets(holdingFlame(base, holder = None), actor,
      2, 0), Phase.Act)
    assert(!usable(notHeld, power.id))
    assert(use(notHeld, power, darkestSecret).isLeft)
    val theirs = inPhase(withSecrets(holdingFlame(base, holder = Some(p1)),
      actor, 2, 0), Phase.Act)
    assert(!usable(theirs, power.id))
    val festival = inPhase(withSecrets(
      holdingFlame(base, DarkestSecretFace.Festival), actor, 2, 0), Phase.Act)
    assert(!usable(festival, power.id))
    assert(use(festival, power, darkestSecret).isLeft)
    assert(!usable(inPhase(staged(2), Phase.Wake), power.id))
  }

  test("the Move power and the place power are separate uses of one banner") {
    val start = withSiteSecrets(staged(faceUp = 1), brokenPeaks, 1)
    assertEquals(PaidActionHarness.usableIds(start).toSet,
      Set(WanderingFlameMove.id, WanderingFlamePlace.id))
  }
}

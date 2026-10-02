package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p3}
import oathdigital.testkit.Unwrap.accepted

class WanderingFlamePlaceSuite extends munit.FunSuite:
  import PowerFixture._
  import BannerFixture._
  import PhasePowerHarness.{after, backToActing, ops, replayed, use}
  import ActPowerHarness.usableIds

  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val power = WanderingFlamePlace

  /** p1's Act at Ancient City holding the Darkest Secret, with its one
    * secret, on Wandering Flame, and `faceUp` and `faceDown` secrets. */
  private def staged(faceUp: Int, faceDown: Int = 0): ReadyGame =
    Table.start.pawn(p1, ancientCity).darkestSecret(Some(p1), 1)
      .secrets(p1, faceUp, faceDown).ready

  test("it moves one secret from the actor's board onto the pawn's site, " +
      "and nothing else changes"):
    val start = staged(faceUp = 2, faceDown = 1)
    val done = accepted(use(start, power, darkestSecretOption))
    val end = after(done)
    assert(backToActing(done))
    assertEquals(Look(end).player(actor).board.faceUpSecrets, 1)
    assertEquals(Look(end).player(actor).board.faceDownSecrets, 1)
    assertEquals(Look(end).siteTokens(ancientCity).secrets,
      Look(start).siteTokens(ancientCity).secrets + 1)
    assertEquals(ops(done.events), Vector[CoreOperation](
      Move(Piece.Secrets(1), PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(ancientCity)))))
    assertEquals(replayed(start, done.events), Right(done.state))
    assertWireRoundTrips(done.events)

  test("it places on the site the pawn is at, whichever site that is"):
    val start = Table.from(staged(faceUp = 1)).pawn(actor, brokenPeaks)
      .unchecked
    val end = after(accepted(use(start, power, darkestSecretOption)))
    assertEquals(Look(end).siteTokens(brokenPeaks).secrets,
      Look(start).siteTokens(brokenPeaks).secrets + 1)
    assertEquals(Look(end).siteTokens(ancientCity).secrets,
      Look(start).siteTokens(ancientCity).secrets)

  test("it is unlimited: each use places another secret"):
    val first = accepted(use(staged(faceUp = 3), power, darkestSecretOption))
    val second = accepted(use(after(first), power, darkestSecretOption))
    val end = after(second)
    assertEquals(Look(end).player(actor).board.faceUpSecrets, 1)
    assertEquals(Look(end).siteTokens(ancientCity).secrets,
      Look(staged(faceUp = 3)).siteTokens(ancientCity).secrets + 2)
    assertEquals(end.game.current.turn.usedPowers, Set.empty[PowerUseRef])

  test("with no faceup secret it is not usable, and a facedown secret is " +
      "never flipped or moved"):
    Vector(staged(faceUp = 0), staged(faceUp = 0, faceDown = 2)).foreach { start =>
      assert(!usableIds(start).contains(power.id))
      assert(use(start, power, darkestSecretOption).isLeft)
    }
    assert(usableIds(staged(faceUp = 1)).contains(power.id))
    val end = after(
      accepted(
      use(staged(faceUp = 1, faceDown = 2), power, darkestSecretOption)))
    assertEquals(Look(end).player(actor).board.faceUpSecrets, 0)
    assertEquals(Look(end).player(actor).board.faceDownSecrets, 2)
    assert(!usableIds(end).contains(power.id), "only facedown secrets are left")

  test("it is unusable without the banner, with the banner held by another " +
      "player, on the Festival face or outside the Act phase"):
    val notHeld = Table.start.turn(p1, Phase.Act).darkestSecret(None, 1)
      .secrets(p1, 2, 0).ready
    assert(!usableIds(notHeld).contains(power.id))
    assert(use(notHeld, power, darkestSecretOption).isLeft)
    val theirs = Table.start.turn(p1, Phase.Act).darkestSecret(Some(p3), 1)
      .secrets(p1, 2, 0).ready
    assert(!usableIds(theirs).contains(power.id))
    val festival = Table.start.turn(p1, Phase.Act)
      .darkestSecret(Some(p1), 1, DarkestSecretFace.Festival).secrets(p1, 2, 0)
      .ready
    assert(!usableIds(festival).contains(power.id))
    assert(use(festival, power, darkestSecretOption).isLeft)
    assert(!usableIds(inPhase(staged(2), Phase.Wake)).contains(power.id))

  test("the Move power and the place power are separate uses of one banner"):
    val start = Table.from(staged(faceUp = 1))
      .siteTokens(brokenPeaks, secrets = 1).unchecked
    assertEquals(ActPowerHarness.usableIds(start).toSet,
      Set(WanderingFlameMove.id, WanderingFlamePlace.id))

  test("it writes the site it placed a secret at"):
    val done = accepted(use(staged(faceUp = 2), power, darkestSecretOption))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} placed a secret at ${ancientCity.value}.",
      covers = false)))

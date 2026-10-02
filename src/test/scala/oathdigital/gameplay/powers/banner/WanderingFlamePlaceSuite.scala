package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p3

class WanderingFlamePlaceSuite extends munit.FunSuite:
  import PowerFixture._
  import BannerFixture._
  import PhasePowerHarness.{after, backToActing, ops, replayed, use}
  import ActPowerHarness.usableIds

  private val power = WanderingFlamePlace

  /** The actor holds the banner in the Act phase with the given secrets. */
  private def staged(faceUp: Int, faceDown: Int = 0): ReadyGame = inPhase(
    Table.from(quietTable).darkestSecret(Some(actor), 1).secrets(actor, faceUp,
      faceDown)
      .unchecked, Phase.Act)

  test("it moves one secret from the actor's board onto the pawn's site, " +
      "and nothing else changes"):
    val start = staged(faceUp = 2, faceDown = 1)
    val done = use(start, power, darkestSecretOption).toOption.get
    val end = after(done)
    assert(backToActing(done))
    assertEquals(Look(end).player(actor).board.faceUpSecrets, 1)
    assertEquals(Look(end).player(actor).board.faceDownSecrets, 1)
    assertEquals(Look(end).siteTokens(CatalogNames.site("Ancient City"))
      .secrets, Look(start).siteTokens(CatalogNames.site("Ancient City"))
      .secrets + 1)
    assertEquals(ops(done.events), Vector[CoreOperation](
      Move(Piece.Secrets(1), PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(CatalogNames.site("Ancient City"))))))
    assertEquals(replayed(start, done.events), Right(done.state))
    assertWireRoundTrips(done.events)

  test("it places on the site the pawn is at, whichever site that is"):
    val start = Table.from(staged(faceUp = 1)).pawn(actor,
      CatalogNames.site("Broken Peaks"))
      .unchecked
    val end = after(use(start, power, darkestSecretOption).toOption.get)
    assertEquals(Look(end).siteTokens(CatalogNames.site("Broken Peaks"))
      .secrets, Look(start).siteTokens(CatalogNames.site("Broken Peaks"))
      .secrets + 1)
    assertEquals(Look(end).siteTokens(CatalogNames.site("Ancient City"))
      .secrets, Look(start).siteTokens(CatalogNames.site("Ancient City"))
      .secrets)

  test("it is unlimited: each use places another secret"):
    val first = use(staged(faceUp = 3), power, darkestSecretOption).toOption.get
    val second = use(after(first), power, darkestSecretOption).toOption.get
    val end = after(second)
    assertEquals(Look(end).player(actor).board.faceUpSecrets, 1)
    assertEquals(Look(end).siteTokens(CatalogNames.site("Ancient City"))
      .secrets,
      Look(staged(faceUp = 3)).siteTokens(CatalogNames.site("Ancient City"))
        .secrets + 2)
    assertEquals(end.game.current.turn.usedPowers, Set.empty[PowerUseRef])

  test("with no faceup secret it is not usable, and a facedown secret is " +
      "never flipped or moved"):
    Vector(staged(faceUp = 0), staged(faceUp = 0, faceDown = 2)).foreach { start =>
      assert(!usableIds(start).contains(power.id))
      assert(use(start, power, darkestSecretOption).isLeft)
    }
    assert(usableIds(staged(faceUp = 1)).contains(power.id))
    val end = after(use(staged(faceUp = 1, faceDown = 2), power,
      darkestSecretOption)
      .toOption.get)
    assertEquals(Look(end).player(actor).board.faceUpSecrets, 0)
    assertEquals(Look(end).player(actor).board.faceDownSecrets, 2)
    assert(!usableIds(end).contains(power.id), "only facedown secrets are left")

  test("it is unusable without the banner, on the Festival face or outside " +
      "the Act phase"):
    val notHeld = inPhase(Table.from(quietTable).darkestSecret(None, 1)
      .secrets(actor, 2, 0).unchecked, Phase.Act)
    assert(!usableIds(notHeld).contains(power.id))
    assert(use(notHeld, power, darkestSecretOption).isLeft)
    val theirs = inPhase(Table.from(quietTable).darkestSecret(Some(p3), 1)
      .secrets(actor, 2, 0).unchecked, Phase.Act)
    assert(!usableIds(theirs).contains(power.id))
    val festival = inPhase(Table.from(quietTable).darkestSecret(Some(actor), 1,
      DarkestSecretFace.Festival).secrets(actor, 2, 0).unchecked, Phase.Act)
    assert(!usableIds(festival).contains(power.id))
    assert(use(festival, power, darkestSecretOption).isLeft)
    assert(!usableIds(inPhase(staged(2), Phase.Wake)).contains(power.id))

  test("the Move power and the place power are separate uses of one banner"):
    val start = Table.from(staged(faceUp = 1))
      .siteTokens(CatalogNames.site("Broken Peaks"), secrets = 1).unchecked
    assertEquals(ActPowerHarness.usableIds(start).toSet,
      Set(WanderingFlameMove.id, WanderingFlamePlace.id))

  test("it writes the site it placed a secret at"):
    val done = use(staged(faceUp = 2), power, darkestSecretOption).toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} placed a secret at ${CatalogNames.site("Ancient City").value}.",
      covers = false)))

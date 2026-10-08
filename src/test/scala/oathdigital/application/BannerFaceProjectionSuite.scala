package oathdigital.application

import oathdigital.gameplay.powers.banner.{WanderingFlameMove,
  WanderingFlamePlace}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.OathState.Ready
import oathdigital.model._
import oathdigital.protocol.{GameIntent, WalkerStartArgWire}
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}

/** The banner faces' phase powers reach the client: they are projected with a
  * name and a text, their `usePower` control is legal for the holder only,
  * and the client's source (a banner) binds back to the engine's source.
  */
class BannerFaceProjectionSuite extends munit.FunSuite:
  private val projector = new GameProjector(catalog)
  private val move = WanderingFlameMove.id.value
  private val place = WanderingFlamePlace.id.value

  test("the holder is shown both Wandering Flame powers, named, with a text"):
    // p1 stands at Ancient City and holds the Wandering Flame and a faceup
    // secret; Broken Peaks holds a secret.
    val holding = Table.start.pawn(p1, "Ancient City")
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .siteTokens("Broken Peaks", secrets = 1).secrets(p1, faceUp = 1).ready
    val projected = projector.project("flame", LoadedGame(Ready(holding), 30L),
      p1)
    assertEquals(projected.phasePowers.map(p =>
      (p.powerId, p.source.kind, p.source.id, p.name)), Vector(
      (move, "banner", "darkest-secret", "Wandering Flame: move"),
      (place, "banner", "darkest-secret", "Wandering Flame: place a secret on your site")))
    assertEquals(projected.phasePowers.map(_.rulesText), Vector(
      "ACTION: Place your pawn at a site with a secret on it. You may do " +
        "this multiple times per turn.",
      "ACTION: Place a secret on the banner or on your site."))
    assert(projected.legalControls.contains(s"usePower:$move:darkest-secret"))
    assert(projected.legalControls.contains(s"usePower:$place:darkest-secret"))

  test("a power with nothing to do is not projected: Move needs a secret-" +
      "bearing site, and the place power needs a faceup secret"):
    def shown(ready: ReadyGame) = projector.project("flame",
      LoadedGame(Ready(ready), 30L), p1)
    // p1 stands at Ancient City throughout. The quiet table has no site
    // secrets, so Move has no target.
    val noSite = Table.start.pawn(p1, "Ancient City")
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .secrets(p1, faceUp = 1).ready
    assertEquals(shown(noSite).phasePowers.map(_.powerId), Vector(place))
    val noSecret = Table.start.pawn(p1, "Ancient City")
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .siteTokens("Broken Peaks", secrets = 1)
      .secrets(p1, faceUp = 0, faceDown = 2).ready
    assertEquals(shown(noSecret).phasePowers.map(_.powerId), Vector(move))
    val neither = Table.start.pawn(p1, "Ancient City")
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .secrets(p1, faceUp = 0, faceDown = 2).ready
    assertEquals(shown(neither).phasePowers, Vector.empty)
    assert(!shown(neither).legalControls.exists(_.startsWith("usePower:")))

  test("another player, and the Festival face, are shown neither"):
    val holding = Table.start
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .siteTokens("Broken Peaks", secrets = 1).secrets(p1, faceUp = 1).ready
    val theirs = projector.project("flame", LoadedGame(Ready(holding), 30L),
      p2)
    assertEquals(theirs.phasePowers, Vector.empty)
    assert(!theirs.legalControls.exists(_.startsWith("usePower:")))
    val festival = Table.start
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.Festival)
      .siteTokens("Broken Peaks", secrets = 1).ready
    assertEquals(projector.project("flame", LoadedGame(Ready(festival), 30L),
      p1).phasePowers, Vector.empty)

  test("the client's banner source binds to the engine's banner source"):
    assertEquals(GameIntentMapper.bind(p1, GameIntent.UsePower(move,
      WalkerStartArgWire("banner", "darkest-secret"))),
      Right(GameCommand.UsePower(p1, WanderingFlameMove.id,
        DecisionOptionRef.Banner(Banner.DarkestSecret))))
    assert(GameIntentMapper.bind(p1, GameIntent.UsePower(move,
      WalkerStartArgWire("banner", "no-such-banner"))).isLeft)

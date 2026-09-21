package oathdigital.application

import oathdigital.gameplay.powers.{PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.banner.{BannerFixture, WanderingFlameMove, WanderingFlamePlace}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.OathState.Ready
import oathdigital.model._
import oathdigital.protocol.{GameIntent, WalkerStartArgWire}

/** The banner faces' phase powers reach the client: they are projected with a
  * name and a text, their `usePower` control is legal for the holder only,
  * and the client's source (a banner) binds back to the engine's source.
  */
class BannerFaceProjectionSuite extends munit.FunSuite {
  import PowerFixture.{actor, base, inPhase}
  import BannerFixture._

  private val projector = new GameProjector(catalog)
  private val move = WanderingFlameMove.id.value
  private val place = WanderingFlamePlace.id.value

  /** The holder has a faceup secret, and another site holds one. */
  private def holding: ReadyGame = inPhase(TargetsFixture.withSecrets(
    withSiteSecrets(holdingFlame(base), brokenPeaks, 1), actor, 1, 0), Phase.Act)

  test("the holder is shown both Wandering Flame powers, named, with a text") {
    val projected = projector.project("flame", LoadedGame(Ready(holding), 30L),
      actor)
    assertEquals(projected.phasePowers.map(p =>
      (p.powerId, p.source.kind, p.source.id, p.name)), Vector(
      (move, "banner", "darkest-secret", "Wandering Flame: move"),
      (place, "banner", "darkest-secret", "Wandering Flame: place a secret on your site")))
    assert(projected.phasePowers.forall(_.rulesText.trim.nonEmpty))
    assert(projected.legalControls.contains(s"usePower:$move:darkest-secret"))
    assert(projected.legalControls.contains(s"usePower:$place:darkest-secret"))
  }

  test("a power with nothing to do is not projected: Move needs a secret-" +
      "bearing site, and the place power needs a faceup secret") {
    def shown(ready: ReadyGame) = projector.project("flame",
      LoadedGame(Ready(ready), 30L), actor)
    val noSite = inPhase(TargetsFixture.withSecrets(
      withoutSiteSecrets(holdingFlame(base)), actor, 1, 0), Phase.Act)
    assertEquals(shown(noSite).phasePowers.map(_.powerId), Vector(place))
    val noSecret = TargetsFixture.withSecrets(holding, actor, 0, 2)
    assertEquals(shown(noSecret).phasePowers.map(_.powerId), Vector(move))
    val neither = inPhase(TargetsFixture.withSecrets(
      withoutSiteSecrets(holdingFlame(base)), actor, 0, 2), Phase.Act)
    assertEquals(shown(neither).phasePowers, Vector.empty)
    assert(!shown(neither).legalControls.exists(_.startsWith("usePower:")))
  }

  test("another player, and the Festival face, are shown neither") {
    val other = base.game.current.players.map(_.player).find(_ != actor).get
    val theirs = projector.project("flame", LoadedGame(Ready(holding), 30L),
      other)
    assertEquals(theirs.phasePowers, Vector.empty)
    assert(!theirs.legalControls.exists(_.startsWith("usePower:")))
    val festival = inPhase(withSiteSecrets(
      holdingFlame(base, DarkestSecretFace.Festival), brokenPeaks, 1), Phase.Act)
    assertEquals(projector.project("flame", LoadedGame(Ready(festival), 30L),
      actor).phasePowers, Vector.empty)
  }

  test("the client's banner source binds to the engine's banner source") {
    assertEquals(GameIntentMapper.bind(actor, GameIntent.UsePower(move,
      WalkerStartArgWire("banner", "darkest-secret"))),
      Right(GameCommand.UsePower(actor, WanderingFlameMove.id,
        DecisionOptionRef.Banner(Banner.DarkestSecret))))
    assert(GameIntentMapper.bind(actor, GameIntent.UsePower(move,
      WalkerStartArgWire("banner", "no-such-banner"))).isLeft)
  }
}

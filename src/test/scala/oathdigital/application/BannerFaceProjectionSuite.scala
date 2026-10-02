package oathdigital.application

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.powers.banner.{WanderingFlameMove,
  WanderingFlamePlace}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.OathState.Ready
import oathdigital.model._
import oathdigital.protocol.{GameIntent, WalkerStartArgWire}
import oathdigital.testkit.{CatalogNames, Table}

/** The banner faces' phase powers reach the client: they are projected with a
  * name and a text, their `usePower` control is legal for the holder only,
  * and the client's source (a banner) binds back to the engine's source.
  */
class BannerFaceProjectionSuite extends munit.FunSuite:
  import PowerFixture.{actor, quietTable, inPhase}

  private val projector = new GameProjector(catalog)
  private val move = WanderingFlameMove.id.value
  private val place = WanderingFlamePlace.id.value

  /** The holder has a faceup secret, and another site holds one. */
  private def holding: ReadyGame = inPhase(Table.from(quietTable)
    .darkestSecret(Some(actor),
    1).siteTokens(CatalogNames.site("Broken Peaks"), secrets = 1)
    .secrets(actor, 1, 0)
      .unchecked, Phase.Act)

  test("the holder is shown both Wandering Flame powers, named, with a text"):
    val projected = projector.project("flame", LoadedGame(Ready(holding), 30L),
      actor)
    assertEquals(projected.phasePowers.map(p =>
      (p.powerId, p.source.kind, p.source.id, p.name)), Vector(
      (move, "banner", "darkest-secret", "Wandering Flame: move"),
      (place, "banner", "darkest-secret", "Wandering Flame: place a secret on your site")))
    assert(projected.phasePowers.forall(_.rulesText.trim.nonEmpty))
    assert(projected.legalControls.contains(s"usePower:$move:darkest-secret"))
    assert(projected.legalControls.contains(s"usePower:$place:darkest-secret"))

  test("a power with nothing to do is not projected: Move needs a secret-" +
      "bearing site, and the place power needs a faceup secret"):
    def shown(ready: ReadyGame) = projector.project("flame",
      LoadedGame(Ready(ready), 30L), actor)
    val noSite = inPhase(Table.from(quietTable).darkestSecret(Some(actor),
      1).secrets(actor, 1, 0).unchecked, Phase.Act)
    assertEquals(shown(noSite).phasePowers.map(_.powerId), Vector(place))
    val noSecret = Table.from(holding).secrets(actor, 0, 2).unchecked
    assertEquals(shown(noSecret).phasePowers.map(_.powerId), Vector(move))
    val neither = inPhase(Table.from(quietTable).darkestSecret(Some(actor),
      1).secrets(actor, 0, 2).unchecked, Phase.Act)
    assertEquals(shown(neither).phasePowers, Vector.empty)
    assert(!shown(neither).legalControls.exists(_.startsWith("usePower:")))

  test("another player, and the Festival face, are shown neither"):
    val other = quietTable.game.current.players.map(_.player).find(_ != actor)
      .get
    val theirs = projector.project("flame", LoadedGame(Ready(holding), 30L),
      other)
    assertEquals(theirs.phasePowers, Vector.empty)
    assert(!theirs.legalControls.exists(_.startsWith("usePower:")))
    val festival = inPhase(Table.from(quietTable).darkestSecret(Some(actor), 1,
      DarkestSecretFace.Festival)
      .siteTokens(CatalogNames.site("Broken Peaks"),
      secrets = 1).unchecked, Phase.Act)
    assertEquals(projector.project("flame", LoadedGame(Ready(festival), 30L),
      actor).phasePowers, Vector.empty)

  test("the client's banner source binds to the engine's banner source"):
    assertEquals(GameIntentMapper.bind(actor, GameIntent.UsePower(move,
      WalkerStartArgWire("banner", "darkest-secret"))),
      Right(GameCommand.UsePower(actor, WanderingFlameMove.id,
        DecisionOptionRef.Banner(Banner.DarkestSecret))))
    assert(GameIntentMapper.bind(actor, GameIntent.UsePower(move,
      WalkerStartArgWire("banner", "no-such-banner"))).isLeft)

package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{CardStaging, CatalogCards, NoteText,
  PowerFixture,
  PowerImplementationStatus, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** E08 never appears in a generated game (the Nomad Homeland takes E06), so
  * each test places it by hand.
  */
class SacredGroundSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val e08: EdificeId = CatalogCards.edifice(catalog, SacredGround.id).get
  private val elsewhere: SiteId =
    base.game.current.map.inPlay.find(_ != home(base)).get

  private def withE08(ready: ReadyGame, site: SiteId,
      side: EdificeSide = EdificeSide.Intact): ReadyGame =
    CardStaging.without(ready, e08).updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(site, c.map.sites(site).copy(denizens =
        c.map.sites(site).denizens :+ EdificeState(e08, side, Tokens.empty))))))

  private def search(site: SiteId, vision: VisionId = VisionRules.Faith,
      side: EdificeSide = EdificeSide.Intact): OathTransition =
    searched(withE08(SearchFixture.staged(Vector(vision)), site, side), vision)

  test("Sacred Ground is a registered, implemented rule"):
    assert(WalkerPowerCatalog.default(catalog).powers.exists(
      _.id == SacredGround.id))
    assert(PowerImplementationStatus.implemented(catalog)(SacredGround.id))

  test("a player whose pawn is elsewhere cannot play a Vision faceup"):
    val parkedAt = search(elsewhere)
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))
    assert(offered(parkedAt).contains("adviser-facedown"))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      "adviser-faceup").isLeft)

  test("a player whose pawn is at Sacred Ground plays freely"):
    assert(offered(search(home(base))).contains("adviser-faceup"))

  test("the site's ruler is bound like everyone else"):
    val ruled = withE08(SearchFixture.staged(Vector(VisionRules.Faith)),
      elsewhere).updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(elsewhere, c.map.sites(elsewhere).copy(forces =
        SiteForces.Occupied(ForceKind.Exile(player(base).lineage), 2))))))
    assert(!offered(searched(ruled, VisionRules.Faith))
      .contains("adviser-faceup"))

  test("the Conspiracy is excepted"):
    assert(offered(search(elsewhere, VisionRules.Conspiracy))
      .contains("adviser-faceup"))

  test("the ruined face, Desecrated Ground, forbids nothing"):
    assert(offered(search(elsewhere, side = EdificeSide.Ruined))
      .contains("adviser-faceup"))

  test("a facedown Vision played from the advisers is bound too"):
    val ready = withE08(inPhase(base, Phase.Act), elsewhere)
    assert(!offered(fromAdvisers(ready, VisionRules.Faith))
      .contains("adviser-faceup"))

  private def hidden(from: OathTransition): Vector[NoteText.Said] =
    val power = SacredGround.forCatalog(catalog).get
    NoteText.said(power.id, power.noteKeys, from.events)

  test("the hidden faceup placement is written as Sacred Ground's line"):
    assertEquals(hidden(search(elsewhere)).distinct, Vector(NoteText.Said(
      "no-faceup", s"${actor.value} cannot play a Vision faceup.",
      covers = false)))

  test("the Conspiracy, which Sacred Ground excepts, reads no line"):
    assertEquals(hidden(search(elsewhere, VisionRules.Conspiracy)), Vector.empty)

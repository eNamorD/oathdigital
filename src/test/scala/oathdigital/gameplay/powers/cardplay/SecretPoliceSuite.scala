package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{CardStaging, PowerFixture,
  PowerImplementationStatus, SearchFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SecretPoliceSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val police = DenizenId("113")
  private val other = TargetsFixture.others(base).head
  private val elsewhere: SiteId =
    base.game.current.map.inPlay.find(_ != home(base)).get

  private def exile(id: PlayerId): SiteForces =
    SiteForces.Occupied(ForceKind.Exile(player(base, id).lineage), 2)
  private val bandits: SiteForces = SiteForces.Occupied(ForceKind.Bandit, 2)

  private def withForces(ready: ReadyGame, site: SiteId,
      forces: SiteForces): ReadyGame = ready.updateCurrent(c => c.copy(
    map = c.map.copy(sites = c.map.sites.updated(site,
      c.map.sites(site).copy(forces = forces)))))

  /** Secret Police faceup at `at`, ruled by `policeRuler`, and the actor's
    * pawn site ruled by `homeRuler`.
    */
  private def policed(ready: ReadyGame, at: SiteId, policeRuler: SiteForces,
      homeRuler: SiteForces): ReadyGame =
    withForces(withForces(atSite(CardStaging.without(ready, police), police, at),
      at, policeRuler), home(base), homeRuler)

  private def search(at: SiteId, policeRuler: SiteForces, homeRuler: SiteForces,
      vision: VisionId = VisionRules.Faith): OathTransition =
    searched(policed(SearchFixture.staged(Vector(vision)), at, policeRuler,
      homeRuler), vision)

  test("Secret Police is a registered, implemented rule"):
    assert(WalkerPowerCatalog.default(catalog).powers.exists(
      _.id == SecretPolice.id))
    assert(PowerImplementationStatus.implemented(catalog)(SecretPolice.id))

  test("an enemy at a site its ruler rules cannot play a Vision faceup"):
    val parkedAt = search(home(base), exile(other), exile(other))
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))
    assert(offered(parkedAt).contains("adviser-facedown"))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      "adviser-faceup").isLeft)

  test("any site the ruler rules binds, not only the Police site"):
    assert(!offered(search(elsewhere, exile(other), exile(other)))
      .contains("adviser-faceup"))

  test("the Police site's own ruler plays freely"):
    assert(offered(search(home(base), exile(actor), exile(actor)))
      .contains("adviser-faceup"))

  test("a pawn at a site with a different ruler is not bound"):
    assert(offered(search(elsewhere, exile(other), exile(actor)))
      .contains("adviser-faceup"))

  test("under Bandit rule every player at a Bandit site is bound"):
    assert(!offered(search(elsewhere, bandits, bandits))
      .contains("adviser-faceup"))

  test("the Conspiracy is forbidden too"):
    assert(!offered(search(home(base), exile(other), exile(other),
      VisionRules.Conspiracy)).contains("adviser-faceup"))

  test("a facedown Vision played from the advisers is bound too"):
    val ready = policed(inPhase(base, Phase.Act), home(base), exile(other),
      exile(other))
    assert(!offered(fromAdvisers(ready, VisionRules.Faith))
      .contains("adviser-faceup"))

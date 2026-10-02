package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{CardStaging, NoteText, PowerFixture,
  SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, PhasePowerHarness}

class SecretPoliceSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val police = DenizenId("113")
  private val other = PhasePowerHarness.others(quietTable).head
  private val elsewhere: SiteId =
    quietTable.game.current.map.inPlay.find(_ != actorsSite(quietTable)).get

  private def exile(id: PlayerId): SiteForces =
    SiteForces.Occupied(ForceKind.Exile(Look(quietTable).player(id).lineage), 2)
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
      at, policeRuler), actorsSite(quietTable), homeRuler)

  private def search(at: SiteId, policeRuler: SiteForces, homeRuler: SiteForces,
      vision: VisionId = VisionRules.Faith): OathTransition =
    searched(policed(SearchFixture.staged(Vector(vision)), at, policeRuler,
      homeRuler), vision)

  test("an enemy at a site its ruler rules cannot play a Vision faceup"):
    val parkedAt = search(actorsSite(quietTable), exile(other), exile(other))
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))
    assert(offered(parkedAt).contains("adviser-facedown"))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      "adviser-faceup").isLeft)

  test("any site the ruler rules binds, not only the Police site"):
    assert(!offered(search(elsewhere, exile(other), exile(other)))
      .contains("adviser-faceup"))

  test("the Police site's own ruler plays freely"):
    assert(offered(search(actorsSite(quietTable), exile(actor), exile(actor)))
      .contains("adviser-faceup"))

  test("a pawn at a site with a different ruler is not bound"):
    assert(offered(search(elsewhere, exile(other), exile(actor)))
      .contains("adviser-faceup"))

  test("under Bandit rule every player at a Bandit site is bound"):
    assert(!offered(search(elsewhere, bandits, bandits))
      .contains("adviser-faceup"))

  test("the Conspiracy is forbidden too"):
    assert(!offered(search(actorsSite(quietTable), exile(other), exile(other),
      VisionRules.Conspiracy)).contains("adviser-faceup"))

  test("a facedown Vision played from the advisers is bound too"):
    val ready = policed(inPhase(quietTable, Phase.Act), actorsSite(quietTable),
      exile(other),
      exile(other))
    assert(!offered(fromAdvisers(ready, VisionRules.Faith))
      .contains("adviser-faceup"))

  private def hidden(from: OathTransition): Vector[NoteText.Said] =
    val power = SecretPolice
    NoteText.said(power.id, power.noteKeys, from.events)

  test("the hidden faceup placement is written as the Police's line"):
    assertEquals(hidden(search(actorsSite(quietTable), exile(other),
      exile(other))).distinct,
      Vector(NoteText.Said("no-faceup",
        s"${actor.value} cannot play a Vision faceup.", covers = false)))

  test("a player the Police do not bind reads no line"):
    assertEquals(hidden(search(actorsSite(quietTable), exile(actor),
      exile(actor))),
      Vector.empty)

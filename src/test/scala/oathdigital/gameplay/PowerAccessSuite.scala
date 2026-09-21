package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PowerAccessSuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val actor = current.turn.activePlayer
  private val me = current.players.find(_.player == actor).get
  private val other = current.players.map(_.player).find(_ != actor).get
  private val home = me.pawnSite.get
  private val far = current.map.inPlay.find(_ != home).get
  private val exile = SiteForces.Occupied(ForceKind.Exile(me.lineage), 1)
  private val bandit = SiteForces.Occupied(ForceKind.Bandit, 1)
  private val card = DenizenId(catalog.denizens.head.id.value)

  private def farRuledBy(forces: SiteForces): ReadyGame =
    base.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(far, c.map.sites(far).copy(forces = forces)))))
  private def viaBoard(update: PlayerState => PlayerState): ReadyGame =
    base.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == actor) update(p) else p)))

  private def siteCard(site: SiteId, state: ReadyGame) = PowerAccess.accessible(
    RuleSourceRef.SiteCard(site, card), RuleSourceFace.FaceUp, state, actor)

  test("a site card is usable at the pawn's site and at a ruled site only") {
    assert(siteCard(home, base))
    assert(!siteCard(far, farRuledBy(bandit)))
    assert(siteCard(far, farRuledBy(exile)))
  }

  test("a facedown site card is never usable") {
    assert(!PowerAccess.accessible(RuleSourceRef.SiteCard(home, card),
      RuleSourceFace.FaceDown, base, actor))
  }

  test("an edifice is usable on either face, at the pawn's site or a ruled site") {
    Vector(RuleSourceFace.Intact, RuleSourceFace.Ruined).foreach { face =>
      def edifice(site: SiteId, state: ReadyGame) = PowerAccess.accessible(
        RuleSourceRef.Edifice(site, EdificeId("e")), face, state, actor)
      assert(edifice(home, base), face.toString)
      assert(edifice(far, farRuledBy(exile)), face.toString)
      assert(!edifice(far, farRuledBy(bandit)), face.toString)
    }
  }

  test("a relic at a site never grants access") {
    Vector(RuleSourceFace.FaceUp, RuleSourceFace.FaceDown).foreach { face =>
      assert(!PowerAccess.accessible(RuleSourceRef.SiteRelic(home,
        RelicId("r")), face, base, actor))
    }
  }

  test("advisers: your own faceup ones, and facedown ones only when asked") {
    def adviser(owner: PlayerId, face: RuleSourceFace, facedown: Boolean) =
      PowerAccess.accessible(RuleSourceRef.Adviser(owner, card), face, base,
        actor, facedown)
    assert(adviser(actor, RuleSourceFace.FaceUp, facedown = false))
    assert(!adviser(actor, RuleSourceFace.FaceDown, facedown = false))
    assert(adviser(actor, RuleSourceFace.FaceDown, facedown = true))
    assert(!adviser(other, RuleSourceFace.FaceUp, facedown = false))
  }

  test("a relic in your play area must be faceup") {
    def relic(owner: PlayerId, face: RuleSourceFace) = PowerAccess.accessible(
      RuleSourceRef.Relic(owner, RelicId("r")), face, base, actor)
    assert(relic(actor, RuleSourceFace.FaceUp))
    assert(!relic(actor, RuleSourceFace.FaceDown))
    assert(!relic(other, RuleSourceFace.FaceUp))
  }

  test("a banner is usable by its holder only") {
    def banner(state: ReadyGame) = PowerAccess.accessible(
      RuleSourceRef.Banner(Banner.PeoplesFavor.key), RuleSourceFace.GrandCouncil,
      state, actor)
    def heldBy(holder: Option[PlayerId]) = base.updateCurrent(c => c.copy(
      banners = c.banners.copy(peoplesFavor =
        c.banners.peoplesFavor.copy(holder = holder))))
    assert(banner(heldBy(Some(actor))))
    assert(!banner(heldBy(Some(other))))
    assert(!banner(heldBy(None)))
  }

  test("locate finds a card the actor may use, and says where it is") {
    val atHome = base.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(home, c.map.sites(home).copy(denizens =
        Vector(DenizenState(card, Orientation.FaceUp, Tokens.empty)))))))
    assertEquals(PowerAccess.locate(atHome, actor, card),
      Some(PowerAccess.Held.AtSite(home)))
    assertEquals(PowerAccess.siteOf(atHome, actor, card), Some(home))

    val ruledFar = farRuledBy(exile).updateCurrent(c => c.copy(map =
      c.map.copy(sites = c.map.sites.updated(far, c.map.sites(far).copy(
        denizens = Vector(DenizenState(card, Orientation.FaceUp, Tokens.empty)))))))
    assertEquals(PowerAccess.locate(ruledFar, actor, card),
      Some(PowerAccess.Held.AtSite(far)))
    val unruledFar = ruledFar.updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(far, c.map.sites(far).copy(forces = bandit)))))
    assertEquals(PowerAccess.locate(unruledFar, actor, card), None)

    val adviser = viaBoard(_.copy(advisers = Vector(DenizenState(card,
      Orientation.FaceUp, Tokens.empty))))
    assertEquals(PowerAccess.locate(adviser, actor, card),
      Some(PowerAccess.Held.InPlayArea))
    assertEquals(PowerAccess.siteOf(adviser, actor, card), Some(home))
    val facedown = viaBoard(_.copy(advisers = Vector(DenizenState(card,
      Orientation.FaceDown, Tokens.empty))))
    assertEquals(PowerAccess.locate(facedown, actor, card), None)
  }

  test("locate finds an edifice at a ruled site, intact or ruined") {
    val edifice = EdificeId(catalog.edifices.head.id.value)
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val state = farRuledBy(exile).updateCurrent(c => c.copy(map = c.map.copy(
        sites = c.map.sites.updated(far, c.map.sites(far).copy(denizens =
          Vector(EdificeState(edifice, side, Tokens.empty)))))))
      assertEquals(PowerAccess.locate(state, actor, edifice),
        Some(PowerAccess.Held.AtSite(far)), side.toString)
    }
  }
}

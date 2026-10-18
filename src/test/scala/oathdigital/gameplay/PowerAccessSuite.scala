package oathdigital.gameplay

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

class PowerAccessSuite extends munit.FunSuite:
  private val actor = p1
  private val other = p2
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** A site p1 does not stand at. */
  private val far = CatalogNames.site("Broken Peaks")
  private val card = CatalogNames.denizen("Errand Boy")
  /** p1 standing at Ancient City. */
  private val actorAtHome = Table.start.pawn(actor, at = home).ready
  private val farRuledByActor = Table.start.pawn(actor, at = home)
    .warbandsAt(far, actor, 1)
  private val farRuledByBandit = Table.start.pawn(actor, at = home)
    .bandits(far, 1)

  private def siteCard(site: SiteId, state: ReadyGame) = PowerAccess.accessible(
    RuleSourceRef.SiteCard(site, card), RuleSourceFace.FaceUp, state, actor)

  test("a site card is usable at the pawn's site and at a ruled site only"):
    assert(siteCard(home, actorAtHome))
    assert(!siteCard(far, farRuledByBandit.ready))
    assert(siteCard(far, farRuledByActor.ready))

  test("a facedown site card is never usable"):
    assert(!PowerAccess.accessible(RuleSourceRef.SiteCard(home, card),
      RuleSourceFace.FaceDown, actorAtHome, actor))

  test("an edifice is usable on either face, at the pawn's site or a ruled site"):
    Vector(RuleSourceFace.Intact, RuleSourceFace.Ruined).foreach { face =>
      def edifice(site: SiteId, state: ReadyGame) = PowerAccess.accessible(
        RuleSourceRef.Edifice(site, EdificeId("e")), face, state, actor)
      assert(edifice(home, actorAtHome), face.toString)
      assert(edifice(far, farRuledByActor.ready), face.toString)
      assert(!edifice(far, farRuledByBandit.ready), face.toString)
    }

  test("a relic at a site never grants access"):
    Vector(RuleSourceFace.FaceUp, RuleSourceFace.FaceDown).foreach { face =>
      assert(!PowerAccess.accessible(RuleSourceRef.SiteRelic(home,
        RelicId("r")), face, actorAtHome, actor))
    }

  test("advisers: your own faceup ones, and facedown ones only when asked"):
    def adviser(owner: PlayerId, face: RuleSourceFace, facedown: Boolean) =
      PowerAccess.accessible(RuleSourceRef.Adviser(owner, card), face,
        actorAtHome, actor, facedown)
    assert(adviser(actor, RuleSourceFace.FaceUp, facedown = false))
    assert(!adviser(actor, RuleSourceFace.FaceDown, facedown = false))
    assert(adviser(actor, RuleSourceFace.FaceDown, facedown = true))
    assert(!adviser(other, RuleSourceFace.FaceUp, facedown = false))

  test("a relic in your play area must be faceup"):
    def relic(owner: PlayerId, face: RuleSourceFace) = PowerAccess.accessible(
      RuleSourceRef.Relic(owner, RelicId("r")), face, actorAtHome, actor)
    assert(relic(actor, RuleSourceFace.FaceUp))
    assert(!relic(actor, RuleSourceFace.FaceDown))
    assert(!relic(other, RuleSourceFace.FaceUp))

  test("a banner is usable by its holder only"):
    def banner(state: ReadyGame) = PowerAccess.accessible(
      RuleSourceRef.Banner(Banner.PeoplesFavor.key), RuleSourceFace.GrandCouncil,
      state, actor)
    def heldBy(holder: Option[PlayerId]) =
      Table.start.peoplesFavor(holder, favor = 1).ready
    assert(banner(heldBy(Some(actor))))
    assert(!banner(heldBy(Some(other))))
    assert(!banner(heldBy(None)))

  test("locate finds a card the actor may use, and says where it is"):
    val atHome = Table.start.pawn(actor, at = home).denizen(card, at = home)
      .ready
    assertEquals(PowerAccess.locate(atHome, actor, card),
      Some(PowerAccess.Held.AtSite(home)))
    assertEquals(PowerAccess.siteOf(atHome, actor, card), Some(home))

    val ruledFar = farRuledByActor.denizen(card, at = far).ready
    assertEquals(PowerAccess.locate(ruledFar, actor, card),
      Some(PowerAccess.Held.AtSite(far)))
    val unruledFar = farRuledByBandit.denizen(card, at = far).ready
    assertEquals(PowerAccess.locate(unruledFar, actor, card), None)

    val adviser = Table.start.pawn(actor, at = home).adviser(actor, card).ready
    assertEquals(PowerAccess.locate(adviser, actor, card),
      Some(PowerAccess.Held.InPlayArea))
    assertEquals(PowerAccess.siteOf(adviser, actor, card), Some(home))
    val facedown = Table.start.pawn(actor, at = home)
      .adviser(actor, card, facedown = true).ready
    assertEquals(PowerAccess.locate(facedown, actor, card), None)

  test("locate finds an edifice at a ruled site, intact or ruined"):
    val edifice = CatalogNames.edifice("Hall of Ministers")
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val state = farRuledByActor.edifice(edifice, side, at = far).ready
      assertEquals(PowerAccess.locate(state, actor, edifice),
        Some(PowerAccess.Held.AtSite(far)), side.toString)
    }

  /** A plain Order denizen, at the pawn site. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")

  /** `state` with `actor`, the walk's actor on their own turn, acting as if
    * at `site`, as `ActAsIf` leaves it. */
  private def actingAt(state: ReadyGame, site: SiteId): ReadyGame =
    state.updateCurrent(_.copy(walkerAsIfSite = Some(site)))

  test("an as-if site takes the pawn site's place: its cards are usable " +
      "and the pawn site's are not"):
    val state = Table.start.turn(actor, Phase.Act).pawn(actor, at = home)
      .denizen(wrestlers, at = home).denizen(card, at = far).ready
    val acting = actingAt(state, far)
    assertEquals(PowerAccess.locate(acting, actor, card),
      Some(PowerAccess.Held.AtSite(far)))
    assertEquals(PowerAccess.locate(acting, actor, wrestlers), None)
    // Without an as-if site, the pawn site's card is usable as before.
    assertEquals(PowerAccess.locate(state, actor, wrestlers),
      Some(PowerAccess.Held.AtSite(home)))

  test("a pawn site the actor rules stays usable beside an as-if site"):
    val state = actingAt(Table.start.turn(actor, Phase.Act)
      .pawn(actor, at = home).warbandsAt(home, actor, 1)
      .denizen(wrestlers, at = home).denizen(card, at = far).ready, far)
    assertEquals(PowerAccess.locate(state, actor, wrestlers),
      Some(PowerAccess.Held.AtSite(home)))

  test("an as-if site is the walk's actor's only"):
    val state = actingAt(Table.start.turn(actor, Phase.Act)
      .pawn(actor, at = home).pawn(other, at = home)
      .denizen(card, at = far).ready, far)
    assertEquals(PowerAccess.presentSite(state, actor), Some(far))
    assertEquals(PowerAccess.presentSite(state, other), Some(home))
    assertEquals(PowerAccess.locate(state, other, card), None)

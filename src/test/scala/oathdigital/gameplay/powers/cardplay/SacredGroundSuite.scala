package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

/** E08 never appears in a generated game (the Nomad Homeland takes E06), so
  * each test places it by hand.
  */
class SacredGroundSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val e08: EdificeId = SacredGroundCard.id
  // p1 stands at Ancient City on the quiet table; Broken Peaks is elsewhere.
  private val home = CatalogNames.site("Ancient City")
  private val elsewhere = CatalogNames.site("Broken Peaks")

  private def withE08(ready: ReadyGame, site: SiteId,
      side: EdificeSide = EdificeSide.Intact): ReadyGame =
    Table.from(ready).edifice(e08, side, at = site).unchecked

  private def search(site: SiteId, vision: VisionId = VisionRules.Faith,
      side: EdificeSide = EdificeSide.Intact): OathTransition =
    val drawing = SearchFixture.staged(Vector(vision), supply = 5)
    searched(withE08(drawing, site, side), vision)

  test("a player whose pawn is elsewhere cannot play a Vision faceup"):
    val parkedAt = search(elsewhere)
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))
    assert(offered(parkedAt).contains("adviser-facedown"))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      "adviser-faceup").isLeft)

  test("a player whose pawn is at Sacred Ground plays freely"):
    assert(offered(search(home)).contains("adviser-faceup"))

  test("the site's ruler is bound like everyone else"):
    // p1 rules Broken Peaks, where Sacred Ground stands, with 2 warbands.
    val drawingFaith = SearchFixture.staged(Vector(VisionRules.Faith),
      supply = 5)
    val ruled = Table.from(withE08(drawingFaith, elsewhere))
      .warbandsAt(elsewhere, actor, 2).unchecked
    assert(!offered(searched(ruled, VisionRules.Faith))
      .contains("adviser-faceup"))

  test("the Conspiracy is excepted"):
    assert(offered(search(elsewhere, VisionRules.Conspiracy))
      .contains("adviser-faceup"))

  test("the ruined face, Desecrated Ground, forbids nothing"):
    assert(offered(search(elsewhere, side = EdificeSide.Ruined))
      .contains("adviser-faceup"))

  test("a facedown Vision played from the advisers is bound too"):
    val ready = withE08(inPhase(quietTable, Phase.Act), elsewhere)
    assert(!offered(fromAdvisers(ready, VisionRules.Faith))
      .contains("adviser-faceup"))

  private def hidden(from: OathTransition): Vector[NoteText.Said] =
    val power = SacredGround
    NoteText.said(power.id, power.noteKeys, from.events)

  test("the hidden faceup placement is written as Sacred Ground's line"):
    assertEquals(hidden(search(elsewhere)).distinct, Vector(NoteText.Said(
      "no-faceup", s"${actor.value} cannot play a Vision faceup.",
      covers = false)))

  test("the Conspiracy, which Sacred Ground excepts, reads no line"):
    assertEquals(hidden(search(elsewhere, VisionRules.Conspiracy)),
      Vector.empty)

package oathdigital.application.gamelog

import oathdigital.application.GamePresentationProjector
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table

/** A power's note as the reason an option is not on offer: worded by the
  * Game Log's own template, after the power's source.
  */
class NoteReasonsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)
  private val ready = Table.start.ready
  private val site = ready.game.current.map.inPlay.head
  private val power = PowerId("test.hider")
  private val hid = NoteKey("hid", Vector(NotePart.Text("This is hidden.")))
  private val reasons = new NoteReasons(catalog, presentation,
    NoteWordings.of(power, Vector(hid)))

  test("a note reads as its source, then its sentence"):
    assertEquals(reasons.text(power, hid(PowerSourceRef.Site(site)), ready,
      None), Some(s"${presentation.siteLabel(site)}: This is hidden."))

  test("a note whose power declares no template has no text"):
    assertEquals(reasons.text(PowerId("test.silent"),
      hid(PowerSourceRef.Site(site)), ready, None), None)

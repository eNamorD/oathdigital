package oathdigital.frontend

import oathdigital.protocol.GameIntent

/** The Challenge and Place Banner Resource start controls at the DOM. */
class BannerControlsSuite extends munit.FunSuite:
  private def renderAndClickEach(offered: Vector[String],
      canControl: Boolean = true): RenderedControls =
    RenderedControls.renderAndClickEach(offered, canControl)(
      BannerControls.render)

  test("only the offered controls render and each starts its action"):
    val rendered = renderAndClickEach(
      Vector("placeBannerResource", "beginChallenge"))
    assertEquals(rendered.buttons.map(_.textContent),
      Vector("Challenge (1 Supply)", "Place favor or secrets on a banner"))
    assertEquals(rendered.submitted, Vector[GameIntent](
      GameIntent.StartWalker("challenge"),
      GameIntent.StartWalker("place-banner-resource")))

  test("nothing renders when nothing is offered, and controls disable without control"):
    assertEquals(renderAndClickEach(Vector.empty).buttons, Vector.empty)
    assert(renderAndClickEach(Vector("beginChallenge"), canControl = false)
      .buttons.forall(_.disabled))

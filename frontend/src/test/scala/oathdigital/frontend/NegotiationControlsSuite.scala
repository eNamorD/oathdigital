package oathdigital.frontend

import oathdigital.protocol.GameIntent

/** The Negotiation start control at the DOM. */
class NegotiationControlsSuite extends munit.FunSuite:
  private def renderAndClickEach(offered: Vector[String],
      canControl: Boolean = true): RenderedControls =
    RenderedControls.renderAndClickEach(offered, canControl)(
      NegotiationControls.render)

  test("the control renders only when offered and starts Negotiation"):
    val rendered = renderAndClickEach(Vector("beginNegotiation"))
    assertEquals(rendered.buttons.map(_.textContent), Vector("Negotiate"))
    assertEquals(rendered.submitted, Vector[GameIntent](
      GameIntent.StartWalker("negotiation")))
    assertEquals(renderAndClickEach(Vector.empty).buttons, Vector.empty)

  test("the control is disabled for a viewer who cannot control the game"):
    val buttons = renderAndClickEach(Vector("beginNegotiation"),
      canControl = false).buttons
    assertEquals(buttons.size, 1)
    assert(buttons.forall(_.disabled))

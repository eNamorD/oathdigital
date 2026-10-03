package oathdigital.frontend

import oathdigital.protocol.{GameIntent, WalkerStartArgWire}

/** The Muster and Trade start controls at the DOM, under jsdom. */
class EconomyControlsSuite extends munit.FunSuite:
  private def renderAndClickEach(offered: Vector[String],
      canControl: Boolean = true): RenderedControls =
    RenderedControls.renderAndClickEach(offered, canControl)(
      EconomyControls.render)

  test("only the offered controls render, in Muster then Trade order"):
    val rendered = renderAndClickEach(Vector("beginTradeSecret", "beginMuster"))
    assertEquals(rendered.buttons.map(_.textContent),
      Vector("Muster (1 Supply)", "Trade for secrets (1 Supply)"))

  test("each control starts its action, with Trade's resource as the start selection"):
    val rendered = renderAndClickEach(
      Vector("beginMuster", "beginTradeFavor", "beginTradeSecret"))
    assertEquals(rendered.submitted, Vector[GameIntent](
      GameIntent.StartWalker("muster", Vector.empty),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "favor"))),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "secret")))))

  test("no control renders when none is offered, and all are disabled without control"):
    assertEquals(renderAndClickEach(Vector.empty).buttons, Vector.empty)
    val rendered = renderAndClickEach(Vector("beginMuster"), canControl = false)
    assert(rendered.buttons.forall(_.disabled))

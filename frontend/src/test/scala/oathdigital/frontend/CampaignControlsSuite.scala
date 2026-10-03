package oathdigital.frontend

import oathdigital.protocol.GameIntent

/** The Campaign start control at the DOM. */
class CampaignControlsSuite extends munit.FunSuite:
  private def renderAndClickEach(offered: Vector[String],
      canControl: Boolean = true): RenderedControls =
    RenderedControls.renderAndClickEach(offered, canControl)(
      CampaignControls.render)

  test("the control renders only when offered, as a major action, and starts Campaign"):
    val rendered = renderAndClickEach(Vector("beginCampaign"))
    assertEquals(rendered.buttons.map(_.textContent),
      Vector("Campaign (2 Supply)"))
    assertEquals(rendered.submitted, Vector[GameIntent](
      GameIntent.StartWalker("campaign", Vector.empty)))
    assertEquals(renderAndClickEach(Vector.empty).buttons, Vector.empty)
    assert(renderAndClickEach(Vector("beginCampaign"), canControl = false)
      .buttons.forall(_.disabled))
    assertEquals(ServerUiSupport.actionCategory("campaign"), "major")

  test("Campaign does not start through the modifier flow"):
    assertEquals(ModifierFlowDraft.action(
      GameIntent.StartWalker("campaign", Vector.empty)), None)

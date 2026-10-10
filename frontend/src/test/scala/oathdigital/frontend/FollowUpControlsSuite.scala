package oathdigital.frontend

import oathdigital.protocol.GameIntent
import oathdigital.protocol.projection.FollowUpProjection
import org.scalajs.dom

/** The panel a queued action draws in place of the action buttons
  * (follow-up actions design, "Projection and UI"). */
class FollowUpControlsSuite extends munit.FunSuite:
  private val owed = FollowUpProjection("red-exile", "campaign", "Campaign",
    "Knights Errant", startable = true)

  private def rendered(followUp: FollowUpProjection,
      controls: Vector[String] = Vector("beginFollowUp", "skipFollowUp"),
      canControl: Boolean = true)
      : (dom.Element, scala.collection.mutable.Buffer[GameIntent]) =
    val sent = scala.collection.mutable.Buffer.empty[GameIntent]
    val panel = dom.document.createElement("section")
    FollowUpControls.render(ServerModeFixtures.twoSeatProjection(
      controls.toSet, phase = "follow-up-pending")
      .copy(followUp = Some(followUp)), canControl, panel, sent += _)
    (panel, sent)

  private def buttons(panel: dom.Element): Vector[dom.html.Button] =
    panel.querySelectorAll("button").toVector
      .map(_.asInstanceOf[dom.html.Button])

  test("the start button names the action and its source, and Skip skips"):
    val (panel, sent) = rendered(owed)
    assertEquals(buttons(panel).map(_.textContent),
      Vector("Campaign for no Supply · Knights Errant", "Skip"))
    buttons(panel).foreach(_.click())
    assertEquals(sent.toVector, Vector[GameIntent](
      GameIntent.StartWalker("campaign", Vector.empty),
      GameIntent.SkipFollowUp))

  test("an unstartable entry is disabled and says why on hover, and Skip " +
      "still works"):
    val reason = "No site to conquer and no enemy pawn to raid"
    val (panel, _) = rendered(owed.copy(startable = false,
      reason = Some(reason)))
    val start = buttons(panel).head
    assert(start.disabled)
    assertEquals(start.getAttribute("title"), reason)
    assert(panel.textContent.contains(reason))
    assert(!buttons(panel).last.disabled)

  test("the entries behind the head are named"):
    val (panel, _) = rendered(owed.copy(queuedAfter = Vector("Campaign")))
    assert(panel.textContent.contains("Then: Campaign"))

  test("another player sees no buttons"):
    val (panel, _) = rendered(owed, controls = Vector.empty)
    assertEquals(buttons(panel), Vector.empty)

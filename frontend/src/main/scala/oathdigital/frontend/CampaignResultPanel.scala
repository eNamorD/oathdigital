package oathdigital.frontend

import org.scalajs.dom

/** The last Campaign's result, drawn for every viewer: it is public, and it is
  * the only place the dice of a Campaign that ended in one command are shown.
  */
private[frontend] object CampaignResultPanel {
  import ServerUiSupport.{element, playerDisplayName, siteLabel, text}

  /** The faces as the symbols printed on them, drawn by the same renderer
    * the Recover panel uses, with the words kept as the accessible name.
    */
  private def dice(faces: Vector[String]): dom.Element =
    if (faces.isEmpty) {
      val none = element("span", "die-faces")
      none.appendChild(dom.document.createTextNode("no dice"))
      none
    } else DieFace.roll(faces)

  private def line(className: String, before: String, faces: Vector[String],
      after: String): dom.Element = {
    val node = element("p", className)
    node.appendChild(dom.document.createTextNode(before))
    node.appendChild(dice(faces))
    node.appendChild(dom.document.createTextNode(after))
    node
  }

  def render(value: GameProjection, panel: dom.Element): Unit =
    value.lastCampaign.foreach { result =>
      val box = element("section", "campaign-result")
      val kind = if (result.kind == "raid") "Raid" else "Conquest"
      val against = result.defenderPlayerId.fold("Bandits")(id =>
        playerDisplayName(value, id))
      val targets = (result.targetSiteIds.map(siteLabel(value, _)) ++
        result.raidTargets).mkString(", ")
      box.appendChild(text("h2", "", "Last Campaign"))
      box.appendChild(text("p", "campaign-result-summary",
        s"$kind by ${playerDisplayName(value, result.attackerPlayerId)} against " +
          s"$against${if (targets.isEmpty) "" else s" ($targets)"} with ${result.force} " +
          s"committed warband${if (result.force == 1) "" else "s"}"))
      box.appendChild(line("campaign-result-attack", "Attack dice: ",
        result.attackDice,
        s". Attack ${result.attackScore} + ${result.sacrificed} sacrificed = " +
          s"${result.attackScore + result.sacrificed}, ${result.skullLosses} " +
          s"skull loss${if (result.skullLosses == 1) "" else "es"}."))
      box.appendChild(line("campaign-result-defense", "Defense dice: ",
        result.defenseDice, s". Defense ${result.defenseScore}."))
      box.appendChild(text("p", "campaign-result-outcome",
        if (result.attackerWins) "Victory" else "Defeat"))
      panel.appendChild(box)
    }
}

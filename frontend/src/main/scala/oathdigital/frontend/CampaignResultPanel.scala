package oathdigital.frontend

import org.scalajs.dom

/** The last Campaign's result, drawn for every viewer: it is public, and it is
  * the only place the dice of a Campaign that ended in one command are shown.
  */
private[frontend] object CampaignResultPanel {
  import ServerUiSupport.{element, playerDisplayName, siteLabel, text}

  private val names = Map("hollow-sword" -> "hollow sword",
    "one-sword" -> "one sword", "two-swords-skull" -> "two swords and a skull",
    "blank" -> "blank", "one-shield" -> "one shield",
    "two-shields" -> "two shields", "doubler" -> "doubler")

  private def dice(faces: Vector[String]): String =
    if (faces.isEmpty) "no dice" else faces.map(f => names.getOrElse(f, f)).mkString(", ")

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
      box.appendChild(text("p", "campaign-result-attack",
        s"Attack dice: ${dice(result.attackDice)}. Attack ${result.attackScore} + " +
          s"${result.sacrificed} sacrificed = ${result.attackScore + result.sacrificed}, " +
          s"${result.skullLosses} skull loss${if (result.skullLosses == 1) "" else "es"}."))
      box.appendChild(text("p", "campaign-result-defense",
        s"Defense dice: ${dice(result.defenseDice)}. Defense ${result.defenseScore}."))
      box.appendChild(text("p", "campaign-result-outcome",
        if (result.victorious) "Victory" else "Defeat"))
      panel.appendChild(box)
    }
}

package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

private[frontend] object WorldBoardRenderer {
 def players(value: GameProjection, ui: ServerUiView): dom.Element = {
   val panel = element("section", "panel")
   panel.appendChild(text("h2", "", "Exile players"))
   val list = element("ul", "participants")
   value.players.foreach { player =>
     val item = dom.document.createElement("li")
     val reference = playerReference(value, player.playerId)
     item.appendChild(reference)
     item.appendChild(dom.document.createTextNode(s" · role: ${player.role}"))
     value.pawnLocations.find(_.playerId == player.playerId).foreach(pawn =>
       item.appendChild(dom.document.createTextNode(
         s" · pawn at ${siteLabel(value, pawn.siteId)}"
       )))
     list.appendChild(item)
   }
   panel.appendChild(list)
   panel
 }

 def playerBoards(value: GameProjection, ui: ServerUiView): dom.Element = {
   val panel = element("section", "panel player-boards")
   panel.appendChild(text("h2", "", "Player boards"))
   if (value.playerBoards.isEmpty)
     panel.appendChild(text("p", "empty-state", "Player boards are not available yet."))
   value.playerBoards.foreach { board =>
     val section = element("section", "player-board")
     val heading = element("h3", "")
     heading.appendChild(playerReference(value, board.playerId))
     section.appendChild(heading)
     section.appendChild(text("p", "resources",
       s"Warbands ${board.warbands} · Favor ${board.favor} · Secrets " +
         s"${board.faceUpSecrets} face up / ${board.faceDownSecrets} face down · " +
         s"Supply ${board.supply}"))
     val advisers = element("div", "board-cards advisers")
     advisers.appendChild(text("strong", "", "Advisers"))
     board.advisers.foreach { card =>
       advisers.appendChild(boardCardTarget(card,
         BoardTargetRef.PlayerAdviser(board.playerId, card.cardId), ui))
     }
     section.appendChild(advisers)
     val relics = element("div", "board-cards relics")
     relics.appendChild(text("strong", "", "Relics"))
     board.relics.foreach { card =>
       relics.appendChild(boardCardTarget(card,
         BoardTargetRef.PlayerRelic(board.playerId, card.cardId), ui))
     }
     section.appendChild(relics)
     board.revealedVision.foreach(card => {
       section.appendChild(text("strong", "", "Revealed Vision"))
       section.appendChild(cardDetailsPopover(card))
     })
     panel.appendChild(section)
   }
   panel
 }

 def boardCardTarget(card: CardDetails, target: BoardTargetRef,
     ui: ServerUiView): dom.Element = {
   import ui._
   val candidate = currentBoardSelection.flatMap(_.activeAction.flatMap(
     _.candidates.find(_.target == target)))
   val shell = element("span", cardTargetClasses(candidate.nonEmpty,
     currentBoardSelection.exists(_.selected(target))))
   shell.setAttribute("data-target-ref", target.stableKey)
   shell.appendChild(cardDetailsPopover(card))
   candidate.foreach { value =>
     val choose = button(candidateButtonLabel(value), "board-target-control")
     choose.setAttribute("data-target-ref", target.stableKey)
     choose.setAttribute("aria-pressed",
       currentBoardSelection.exists(_.selected(target)).toString)
     choose.onclick = _ => currentBoardSelection.foreach(state =>
       handleSelection(state.choose(target)))
     shell.appendChild(choose)
   }
   shell
 }

 def world(
     value: GameProjection,
     presentation: ViewerPresentation,
     ui: ServerUiView
 ): dom.Element = {
   import ui._
   val panel = element("section", "panel world")
   panel.setAttribute("aria-label", "The World")
   panel.appendChild(text("h2", "", "The World"))
   panel.appendChild(pileDisplay("World deck", value.worldDeckCount,
     value.worldDeckTopCardKind))
   val regions = element("div", "regions")
   value.world.foreach { region =>
     val section = element("section", "region")
     val name = region.regionId match {
       case "cradle" => "Cradle"
       case "provinces" => "Provinces"
       case "hinterland" => "Hinterland"
       case other => other
     }
     section.setAttribute("aria-label", name)
     section.appendChild(text("h3", "region-label", name))
     section.appendChild(pileDisplay("Discard", region.discardCount,
       region.discardTopCardKind))
     val sites = element("div", "sites")
     region.sites.foreach { site =>
       val siteTarget = BoardTargetRef.Site(site.siteId)
       val candidate = currentBoardSelection.flatMap(
         _.activeAction.flatMap(_.candidates.find(_.target == siteTarget)))
       val isSelected = currentBoardSelection.exists(_.selected(siteTarget))
       val control = element("article", siteTargetClasses(candidate.nonEmpty,
         isSelected))
       control.setAttribute("aria-label", site.label)
       control.setAttribute("data-target-ref", siteTarget.stableKey)
       val heading = element("div", "site-heading")
       heading.appendChild(VisualDomRenderer.render(
         SiteCardPresentation.from(site).siteVisual,
         "site-visual"
       ))
       heading.appendChild(text("span", "site-name", site.label))
       control.appendChild(heading)
       candidate.foreach { value =>
         val choose = button(candidateButtonLabel(value), "board-target-control")
         choose.setAttribute("data-target-ref", siteTarget.stableKey)
         choose.setAttribute("aria-pressed", isSelected.toString)
         choose.disabled = !canControl || !presentation.showGameplayControls
         choose.onclick = _ => currentBoardSelection.foreach(state =>
           handleSelection(state.choose(siteTarget)))
         control.appendChild(choose)
       }
       val pawns = element("div", "site-pawns")
       value.pawnLocations.filter(_.siteId == site.siteId).foreach { pawn =>
         val marker = element("span", "pawn")
         marker.appendChild(dom.document.createTextNode("● "))
         marker.appendChild(playerReference(value, pawn.playerId))
         pawns.appendChild(marker)
       }
       if (pawns.childNodes.length > 0) control.appendChild(pawns)
       control.appendChild(siteDetails(site, currentBoardSelection,
         target => currentBoardSelection.foreach(state =>
           handleSelection(state.choose(target)))))
       sites.appendChild(control)
     }
     section.appendChild(sites)
     regions.appendChild(section)
   }
   panel.appendChild(regions)
   panel
 }
}

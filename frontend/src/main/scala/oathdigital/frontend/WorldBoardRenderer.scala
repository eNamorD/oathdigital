package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

private[frontend] object WorldBoardRenderer {
 def players(value: GameProjection, ui: ServerUiView): dom.Element = {
   playerBoards(value, ui)
 }

 /** A resource as its glyph and its count, with the sentence the words used
   * to carry kept as the accessible name.
   */
 private def counted(token: String, value: String,
     accessible: String): dom.Element = {
   val node = element("span", "resource")
   node.setAttribute("aria-label", accessible)
   node.appendChild(RulesTextRenderer.glyph(token))
   node.appendChild(dom.document.createTextNode(value))
   node
 }

 def playerBoards(value: GameProjection, ui: ServerUiView): dom.Element = {
   val panel = element("section", "panel player-boards")
   value.players.foreach { player =>
     val section = element("section", "player-board")
     section.setAttribute("data-player-id", player.playerId)
     if (value.activeParticipantId.contains(player.playerId)) {
       section.classList.add("player-board-active")
       section.setAttribute("aria-label", s"${player.displayName}, active player")
     }
     // One line for who and what they hold: the pane is a strip across the
     // top of the table, so every line it spends is a line of card.
     val identity = element("h3", "player-identity")
     identity.appendChild(playerReference(value, player.playerId))
     identity.appendChild(text("span", "player-role", player.role))
     section.appendChild(identity)
     value.playerBoards.find(_.playerId == player.playerId).foreach { board =>
     val resources = element("span", "resources")
     resources.appendChild(text("span", "resource", s"Warbands ${board.warbands}"))
     resources.appendChild(counted("favor", board.favor.toString, s"Favor ${board.favor}"))
     val secrets = counted("secret", s"${board.faceUpSecrets}/${board.totalSecrets}",
       s"Secrets ${board.faceUpSecrets}/${board.totalSecrets}. " +
         secretSummaryLabel(board.faceUpSecrets, board.totalSecrets,
           board.faceDownSecrets, board.committedSecrets))
     secrets.setAttribute("title", secretSummaryLabel(board.faceUpSecrets,
       board.totalSecrets, board.faceDownSecrets, board.committedSecrets))
     resources.appendChild(secrets)
     resources.appendChild(text("span", "resource", s"Supply ${board.supply}"))
     identity.appendChild(resources)
     // One row, no headings: two labelled rows cost more height than the pane
     // has, and a relic's square box already says which card is which.
     val cards = element("div", "board-cards")
     cards.setAttribute("aria-label", "Cards in play")
     board.advisers.foreach(card => cards.appendChild(CardFace.render(card)))
     board.relics.foreach(card => cards.appendChild(CardFace.render(card)))
     board.revealedVision.foreach(card =>
       cards.appendChild(CardFace.render(card)))
     section.appendChild(cards)
     board.banners.foreach { banner =>
       section.appendChild(text("p", s"player-banner banner-${banner.key}",
         s"${actionLabel(banner.key)} · ${banner.face.replace('-', ' ')} · " +
           s"resources ${banner.resources}"))
     }
     }
     panel.appendChild(section)
   }
   panel
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
       candidate.foreach { _ =>
         control.setAttribute("role", "button")
         control.setAttribute("tabindex", "0")
         control.setAttribute("aria-pressed", isSelected.toString)
         control.addEventListener("click", (_: dom.Event) =>
           currentBoardSelection.foreach(state =>
             handleSelection(state.choose(siteTarget))))
         control.addEventListener("keydown", (event: dom.Event) => {
           val key = event.asInstanceOf[dom.KeyboardEvent].key
           if (key == "Enter" || key == " ") {
             event.preventDefault()
             currentBoardSelection.foreach(state =>
               handleSelection(state.choose(siteTarget)))
           }
         })
       }
       control.appendChild(siteHeading(site))
       candidate.flatMap(candidateDetailBadge).foreach(control.appendChild)
       val pawns = element("div", "site-pawns")
       value.pawnLocations.filter(_.siteId == site.siteId).foreach { pawn =>
         val marker = element("span", "pawn")
         marker.appendChild(dom.document.createTextNode("● "))
         marker.appendChild(playerReference(value, pawn.playerId))
         pawns.appendChild(marker)
       }
       if (pawns.childNodes.length > 0) control.appendChild(pawns)
       control.appendChild(siteDetails(site))
       sites.appendChild(control)
     }
     section.appendChild(sites)
     if (region.regionId == "cradle")
       value.tracks.foreach(track => section.appendChild(roundTracker(value, track)))
     regions.appendChild(section)
   }
   panel.appendChild(regions)
   panel.appendChild(favorBanks(value))
   panel.appendChild(sharedBank(value, ui))
   panel
 }

 private[frontend] final case class RoundSegment(path: String, labelX: Double,
     labelY: Double, markerX: Double, markerY: Double)

 private[frontend] def roundSegment(round: Int): RoundSegment = {
   val center = 100.0; val outer = 88.0; val inner = 49.0
   val start = Math.toRadians(-90.0 + (round - 1) * 45.0 + 2.0)
   val end = Math.toRadians(-90.0 + round * 45.0 - 2.0)
   def point(radius: Double, angle: Double) =
     (center + radius * Math.cos(angle), center + radius * Math.sin(angle))
   val (outerStartX, outerStartY) = point(outer, start)
   val (outerEndX, outerEndY) = point(outer, end)
   val (innerEndX, innerEndY) = point(inner, end)
   val (innerStartX, innerStartY) = point(inner, start)
   val middle = (start + end) / 2.0
   val (labelX, labelY) = point(68.0, middle)
   val (markerX, markerY) = point(94.0, middle)
   def n(value: Double) = f"$value%.2f"
   RoundSegment(s"M ${n(outerStartX)} ${n(outerStartY)} " +
     s"A ${n(outer)} ${n(outer)} 0 0 1 ${n(outerEndX)} ${n(outerEndY)} " +
     s"L ${n(innerEndX)} ${n(innerEndY)} " +
     s"A ${n(inner)} ${n(inner)} 0 0 0 ${n(innerStartX)} ${n(innerStartY)} Z",
     labelX, labelY, markerX, markerY)
 }

 private def roundTracker(value: GameProjection,
     track: oathdigital.protocol.projection.GameTracksProjection): dom.Element = {
   val section = element("section", "round-tracker")
   section.setAttribute("aria-label", s"Round ${track.round} of 8; " +
     s"Visions Drawn ${track.visionsDrawn}; first player ${track.firstPlayerId}")
   val svg = dom.document.createElementNS("http://www.w3.org/2000/svg", "svg")
     .asInstanceOf[dom.svg.SVG]
   svg.setAttribute("viewBox", "0 0 200 200")
   svg.setAttribute("role", "img")
   svg.setAttribute("aria-label", s"Eight-segment round tracker, current round ${track.round}")
   (1 to 8).foreach { round =>
     val segment = roundSegment(round)
     val group = dom.document.createElementNS("http://www.w3.org/2000/svg", "g")
     group.setAttribute("data-round", round.toString)
     val path = dom.document.createElementNS("http://www.w3.org/2000/svg", "path")
     path.setAttribute("d", segment.path)
     path.setAttribute("class", if (round == track.round) "round-current" else "round-segment")
     val label = dom.document.createElementNS("http://www.w3.org/2000/svg", "text")
     label.setAttribute("x", segment.labelX.toString)
     label.setAttribute("y", segment.labelY.toString)
     label.setAttribute("text-anchor", "middle"); label.setAttribute("dominant-baseline", "middle")
     label.textContent = round.toString
     group.appendChild(path); group.appendChild(label)
     if (track.usurperLimited && round == track.limiterRound) {
       val marker = dom.document.createElementNS("http://www.w3.org/2000/svg", "text")
       marker.setAttribute("x", segment.markerX.toString)
       marker.setAttribute("y", segment.markerY.toString)
       marker.setAttribute("text-anchor", "middle"); marker.setAttribute("class", "limiter-marker")
       marker.textContent = "◆"; group.appendChild(marker)
     }
     svg.appendChild(group)
   }
   section.appendChild(svg)
   section.appendChild(text("p", "track-summary",
     s"Visions Drawn ${track.visionsDrawn} · First player ${track.firstPlayerId}"))
   section
 }

 private def favorBanks(value: GameProjection): dom.Element = {
   val banks = element("div", "favor-banks")
   banks.setAttribute("aria-label", "Favor banks")
   value.favorBanks.foreach(bank => banks.appendChild(text("span",
     s"favor-bank suit-${bank.suit}", s"${bank.suit.capitalize}: ${bank.count}")))
   banks
 }

 private def sharedBank(value: GameProjection, ui: ServerUiView): dom.Element = {
   val section = element("section", "shared-bank")
   section.appendChild(text("h3", "", "Shared Bank"))
   section.appendChild(pileDisplay("Relic deck", value.relicDeckCount, None))
   value.banners.foreach { banner =>
     section.appendChild(text("p", s"shared-banner banner-${banner.key}",
       s"${actionLabel(banner.key)} · ${banner.face.replace('-', ' ')} · " +
         s"resources ${banner.resources}"))
   }
   section
 }
}

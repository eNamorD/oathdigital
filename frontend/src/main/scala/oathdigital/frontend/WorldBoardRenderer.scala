package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport._

private[frontend] object WorldBoardRenderer:
 def players(value: GameProjection, playerId: String,
     options: BoardOptions = BoardOptions.none): dom.Element =
   playerBoards(value, playerId, options)

 /** A resource as its glyph and its count, with the sentence the words used
   * to carry kept as the accessible name.
   */
 private def counted(token: String, value: String,
     accessible: String): dom.Element =
   val node = element("span", "resource")
   node.setAttribute("aria-label", accessible)
   node.appendChild(RulesTextRenderer.glyph(token))
   node.appendChild(dom.document.createTextNode(value))
   node

 /** Turn order read from the viewer's own seat. The seating vector is
   * already the cyclic turn order, so anchoring it on the viewer gives who
   * plays after them without tracking the first player -- and unlike an
   * active-player anchor, the strip does not rotate under them as turns pass.
   */
 private[frontend] def seatOrder(players: Vector[GamePlayer],
     viewer: Option[String]): Vector[GamePlayer] =
   viewer.map(id => players.indexWhere(_.playerId == id)).filter(_ > 0)
     .fold(players)(at => players.drop(at) ++ players.take(at))

 def playerBoards(value: GameProjection, playerId: String,
     options: BoardOptions = BoardOptions.none): dom.Element =
   val panel = element("section", "panel player-boards")
   // The seat the client holds, not the projection's viewer: a trusted
   // session adopts its seat from that viewer before it renders and refuses
   // to display a projection that disagrees with it (`ServerModeUi.store`),
   // so this is the one answer that is right in both modes.
   seatOrder(value.players, Some(playerId).filter(_.nonEmpty))
     .foreach { player =>
     val section = element("section", "player-board")
     section.setAttribute("data-player-id", player.playerId)
     if value.activeParticipantId.contains(player.playerId) then
       section.classList.add("player-board-active")
       section.setAttribute("aria-label", s"${player.displayName}, active player")
     // One line for who and what they hold: the pane is a strip across the
     // top of the table, so every line it spends is a line of card.
     val identity = element("h3", "player-identity")
     val name = playerReference(value, player.playerId)
     options.decorate(name,
       Vector(BoardOptionViews.key("player", player.playerId)))
     identity.appendChild(name)
     identity.appendChild(text("span", "player-role", player.role))
     section.appendChild(identity)
     value.playerBoards.find(_.playerId == player.playerId).foreach { board =>
     val resources = element("span", "resources")
     resources.appendChild(text("span", "resource", s"Warbands ${board.warbands}"))
     val favor = counted("favor", board.favor.toString, s"Favor ${board.favor}")
     HoverTip.set(favor, "Favor")
     resources.appendChild(favor)
     val summary = secretSummaryLines(board.faceUpSecrets, board.totalSecrets,
       board.faceDownSecrets, board.committedSecrets)
     val secrets = counted("secret", s"${board.faceUpSecrets}/${board.totalSecrets}",
       summary.mkString(". "))
     HoverTip.set(secrets, summary.mkString("\n"))
     resources.appendChild(secrets)
     // Supply is the one figure with a rule behind it: the bag sets the
     // band, the band sets the Rest. The figure opens that rule.
     val supply = button(s"Supply ${board.supply}/${value.supplyMaximum}",
       "resource supply-open")
     supply.onclick = _ => CardInspection.openText("Supply at Rest",
       SupplyAtRest.lines(player.role, board, value.supplyBands,
         value.supplyMaximum), supply)
     resources.appendChild(supply)
     identity.appendChild(resources)

     // A title and a Vision are states of the player, not cards in a hand:
     // in the physical game a Vision is played sideways, which is what this
     // row stands in for.
     val slots = element("div", "player-slots")
     value.oathkeeper.filter(_.holderPlayerId.contains(player.playerId))
       .foreach { oath =>
         val presented = oathdigital.protocol.projection
           .OathkeeperPresentation.byGoal.get(oath.goal)
         val pill = button(oath.side.capitalize, s"player-title title-${oath.side}")
         val title = presented.fold(oath.goal)(_.title)
         HoverTip.set(pill, title)
         pill.onclick = _ => CardInspection.openText(title,
           presented.fold(Vector.empty[String])(_.lines), pill)
         slots.appendChild(pill)
       }
     // The spot is drawn empty only while the decision lights it: the strip
     // never scrolls, so an empty spot may not spend a row on every board.
     val visionKey = Vector(BoardOptionViews.key("revealed-vision",
       player.playerId))
     board.revealedVision.map { card =>
       val pill = button(card.name, "player-slot-vision")
       pill.onclick = _ => CardInspection.open(card, pill)
       options.control(pill, visionKey,
         Some(card.name -> (CardInspection.open(card, _))))
     }.orElse(options.view(visionKey).map { _ =>
       val spot = button("Vision", "player-slot-vision player-slot-vision-empty")
       spot.setAttribute("aria-label", "Empty revealed Vision spot")
       options.control(spot, visionKey, None)
     }).foreach(slots.appendChild)
     if slots.childNodes.length > 0 then section.appendChild(slots)

     // One row, no headings: two labelled rows cost more height than the pane
     // has, and a relic's square box already says which card is which.
     val cards = element("div", "board-cards")
     cards.setAttribute("aria-label", "Cards in play")
     board.advisers.zipWithIndex.foreach((card, at) => cards.appendChild(
       options.card(CardFace.render(card), card,
         boardCardKeys("adviser-slot", player.playerId, at, card))))
     (board.advisers.size until board.adviserLimit).foreach(at =>
       cards.appendChild(openSlot(options, "denizen", "Empty adviser slot",
         at, BoardOptionViews.key("adviser-slot", s"${player.playerId}:$at"))))
     board.relics.zipWithIndex.foreach((card, at) => cards.appendChild(
       options.card(CardFace.render(card), card,
         boardCardKeys("relic-slot", player.playerId, at, card))))
     section.appendChild(cards)
     board.banners.foreach { banner =>
       val mark = text("p", s"player-banner banner-${banner.key}",
         s"${actionLabel(banner.key)} · ${banner.face.replace('-', ' ')} · " +
           holds(banner))
       options.decorate(mark,
         Vector(BoardOptionViews.key("banner", banner.key)))
       section.appendChild(mark)
     }
     }
     panel.appendChild(section)
   }
   panel

 /** The keys a card on a player board answers to: its slot, and the card
   * itself when the viewer may know which card it is.
   */
 private def boardCardKeys(slot: String, playerId: String, at: Int,
     card: CardDetails): Vector[String] =
   BoardOptionViews.key(slot, s"$playerId:$at") +:
     Option.unless(card.hidden)(BoardOptions.cardKey(card)).toVector

 /** The parked decision's badge for the option on `keys`, drawn inside
   * `target`: what choosing it costs (travel destination and search source
   * designs, ruling 3). */
 private def badged(target: dom.Element, keys: Vector[String],
     options: BoardOptions): Unit =
   options.view(keys).flatMap(_.option.badge).foreach { badge =>
     val chip = text("span", "target-detail-badge", "")
     RulesTextRenderer.inline(badge, chip)
     target.appendChild(chip)
   }

 /** The World pane. `options` draws the parked board decision onto its
   * sites, cards, pawns, banks and banners.
   */
 def world(
     value: GameProjection,
     options: BoardOptions,
     submit: Option[GameCommand => Unit] = None
 ): dom.Element =
   val panel = element("section", "panel world")
   panel.setAttribute("aria-label", "The World")
   panel.appendChild(text("h2", "", "The World"))
   // The world deck is a Search's source and Clay Rattle's pile.
   val deckKeys = Vector(BoardOptionViews.key("deck", "world"))
   val deck = pileDisplay("World deck", value.worldDeckCount,
     value.worldDeckTopCardKind)
   options.decorate(deck, deckKeys)
   badged(deck, deckKeys, options)
   panel.appendChild(deck)
   val regions = element("div", "regions")
   value.world.foreach { region =>
     val section = element("section", "region")
     val name = region.regionId match
       case "cradle" => "Cradle"
       case "provinces" => "Provinces"
       case "hinterland" => "Hinterland"
       case other => other
     section.setAttribute("aria-label", name)
     section.appendChild(text("h3", "region-label", name))
     val discardTitle = s"$name discard pile"
     val discardKeys = Vector(BoardOptionViews.key("discard-pile",
       region.regionId))
     val discard = openablePile("Discard", region.discardCount,
       region.discardTopCardKind, discardTitle, region.discardCards)
     badged(discard, discardKeys, options)
     section.appendChild(options.control(discard, discardKeys,
       Option.when(region.discardCount > 0)(discardTitle -> (anchor =>
         CardInspection.openCards(s"$discardTitle (${region.discardCount})",
           region.discardCards, anchor)))))
     val sites = element("div", "sites")
     region.sites.foreach { site =>
       val control = element("article", "site site-readonly")
       control.setAttribute("aria-label", site.label)
       val siteKeys = Vector(BoardOptionViews.key("site", site.siteId))
       options.decorate(control, siteKeys)
       control.appendChild(siteHeading(site))
       // What reaching this site costs, or why it cannot be reached, comes
       // from the decision that offers it.
       badged(control, siteKeys, options)
       val pawns = element("div", "site-pawns")
       value.pawnLocations.filter(_.siteId == site.siteId).foreach { pawn =>
         val marker = element("span", "pawn")
         marker.appendChild(dom.document.createTextNode("● "))
         marker.appendChild(playerReference(value, pawn.playerId))
         options.decorate(marker,
           Vector(BoardOptionViews.key("player", pawn.playerId)))
         pawns.appendChild(marker)
       }
       // Appended whether or not anyone stands here: the row holds its line
       // of height either way, so the cards below it sit at the same place in
       // every site on the board.
       control.appendChild(pawns)
       control.appendChild(siteDetails(site, options,
         SitePowerLinks.of(value, submit)))
       sites.appendChild(control)
     }
     section.appendChild(sites)
     if region.regionId == "cradle" then
       value.tracks.foreach(track => section.appendChild(roundTracker(value, track)))
     regions.appendChild(section)
   }
   panel.appendChild(regions)
   panel.appendChild(favorBanks(value, options))
   panel.appendChild(sharedBank(value, options))
   panel

 private[frontend] final case class RoundSegment(path: String, labelX: Double,
     labelY: Double, markerX: Double, markerY: Double)

 private[frontend] def roundSegment(round: Int): RoundSegment =
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

 private def roundTracker(value: GameProjection,
     track: oathdigital.protocol.projection.GameTracksProjection): dom.Element =
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
     path.setAttribute("class", if round == track.round then "round-current" else "round-segment")
     val label = dom.document.createElementNS("http://www.w3.org/2000/svg", "text")
     label.setAttribute("x", segment.labelX.toString)
     label.setAttribute("y", segment.labelY.toString)
     label.setAttribute("text-anchor", "middle"); label.setAttribute("dominant-baseline", "middle")
     label.textContent = round.toString
     group.appendChild(path); group.appendChild(label)
     if track.usurperLimited && round == track.limiterRound then
       val marker = dom.document.createElementNS("http://www.w3.org/2000/svg", "text")
       marker.setAttribute("x", segment.markerX.toString)
       marker.setAttribute("y", segment.markerY.toString)
       marker.setAttribute("text-anchor", "middle"); marker.setAttribute("class", "limiter-marker")
       marker.textContent = "◆"; group.appendChild(marker)
     svg.appendChild(group)
   }
   section.appendChild(svg)
   section.appendChild(text("p", "track-summary",
     s"Visions Drawn ${track.visionsDrawn} · First player ${track.firstPlayerId}"))
   // Beside the marker the tracker already draws on that round, rather than
   // in the action panel, which is about this turn rather than the game.
   if track.usurperLimited then
     section.appendChild(text("p", "usurper-notice",
       s"Usurper locked until round ${track.limiterRound}"))
   section

 private def favorBanks(value: GameProjection,
     options: BoardOptions): dom.Element =
   val banks = element("div", "favor-banks")
   banks.setAttribute("aria-label", "Favor banks")
   value.favorBanks.foreach { bank =>
     val box = text("span", s"favor-bank suit-${bank.suit}", "")
     // The symbol first, as on a card: a player matching a bank against a
     // card's suit reads the shape, not the word.
     box.appendChild(RulesTextRenderer.glyph(s"suit-${bank.suit}"))
     box.appendChild(dom.document.createTextNode(
       s"${bank.suit.capitalize}: ${bank.count}"))
     options.decorate(box,
       Vector(BoardOptionViews.key("favor-bank", bank.suit)))
     banks.appendChild(box)
   }
   banks

 /** What a banner holds: The People's Favor holds favor, and Darkest Secret
   * holds secrets. */
 private def holds(banner: BannerState): String = banner.key match
   case "darkest-secret" =>
     if banner.resources == 1 then "1 secret" else s"${banner.resources} secrets"
   case _ => s"${banner.resources} favor"

 private def sharedBank(value: GameProjection,
     options: BoardOptions): dom.Element =
   val section = element("section", "shared-bank")
   section.appendChild(text("h3", "", "Shared Bank"))
   value.oathkeeper.foreach(oath =>
     section.appendChild(text("p", "oathkeeper-status",
       s"${oathdigital.protocol.projection.OathkeeperPresentation.title(oath.goal)} · " +
         s"${oath.side.capitalize}: ${oath.holderPlayerId.getOrElse("unheld")}")))
   section.appendChild(pileDisplay("Relic deck", value.relicDeckCount, None))
   value.banners.foreach { banner =>
     val mark = text("p", s"shared-banner banner-${banner.key}",
       s"${actionLabel(banner.key)} · ${banner.face.replace('-', ' ')} · " +
         holds(banner))
     options.decorate(mark,
       Vector(BoardOptionViews.key("banner", banner.key)))
     section.appendChild(mark)
   }
   section

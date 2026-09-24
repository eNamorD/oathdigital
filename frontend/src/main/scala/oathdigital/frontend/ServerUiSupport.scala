package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}
import org.scalajs.dom
import scala.scalajs.js

private[frontend] trait ServerUiView {
  def currentGameId: String
  def currentPlayerId: String
  def displayedProjection: Option[GameProjection]
  def sessionCoordinator: ServerSessionCoordinator
  def currentBoardSelection: Option[BoardTargetSelectionState]
  def currentBoardSelection_=(value: Option[BoardTargetSelectionState]): Unit
  def currentWalkerPartition: Option[WalkerPartitionDraft]
  def currentWalkerPartition_=(value: Option[WalkerPartitionDraft]): Unit
  def currentWalkerDistribution: Option[WalkerDistributeDraft]
  def currentWalkerDistribution_=(value: Option[WalkerDistributeDraft]): Unit
  def currentWalkerSelection: Option[WalkerSelectionDraft]
  def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]): Unit
  def currentModifierWorkflow: Option[ModifierWorkflow]
  def currentFacedownAdviserDraft: Option[FacedownAdviserDraft]
  def chooseFacedownAdviser(cardId: String): Unit
  def toggleModifier(value: PreviewModifier): Unit
  def moveModifier(value: PreviewModifier, delta: Int): Unit
  def confirmModifiers(): Unit
  def backFromModifiers(): Unit
  def cancelModifiers(): Unit
  def beginTargetedMajorAction(actionKind: String): Unit
  def backFromTargets(): Unit
  def cancelTargetAction(): Unit
  def submitTargetCommand(command: GameCommand): Unit
  def canControl: Boolean
  def rerender(): Unit
  def submitCommand(command: GameCommand): Unit
  def handleSelection(result: BoardSelectionResult): Unit
  def loadSession(gameId: String, playerId: String): Unit
  def reconnectSession(): Unit
  def createGame(): Unit
}
private[frontend] object ServerUiSupport {
  private[frontend] def secretSummaryLabel(available: Int, total: Int,
      facedown: Int, committed: Int): String =
    s"$available available of $total owned; $facedown facedown and $committed committed"

  private[frontend] def siteLabel(value: GameProjection, siteId: String): String =
    value.world.flatMap(_.sites).find(_.siteId == siteId)
      .fold(siteId)(_.label)

  /** Name row and the two upper corners: what a site holds on the left, what
    * it costs to walk into on the right.
    */
  private[frontend] def siteHeading(site: GameSite): dom.Element = {
    val presentation = SiteCardPresentation.from(site)
    val heading = element("div", "site-heading")
    val tokens = element("span", "site-tokens")
    if (presentation.looseFavor > 0)
      siteToken("favor", presentation.looseFavor).foreach(tokens.appendChild)
    if (presentation.looseSecrets > 0)
      siteToken("secret", presentation.looseSecrets).foreach(tokens.appendChild)
    // Always appended, empty or not: the name is the middle cell of three,
    // and a missing cell would slide it off centre.
    heading.appendChild(tokens)
    heading.appendChild(text("span", "site-name", site.label))
    val defense = element("span", "site-defense")
    defense.setAttribute("aria-label", s"Defense ${presentation.defense}")
    // Drawn as dice rather than a number because defense is rolled, and no
    // site in the catalog exceeds two. An undefended site says so in words,
    // since no die at all would read as missing information.
    if (presentation.defense == 0)
      defense.appendChild(text("span", "site-defense-none", "0"))
    else (0 until presentation.defense)
      .foreach(_ => defense.appendChild(RulesTextRenderer.glyph("defense-die")))
    heading.appendChild(defense)
    heading
  }

  private def siteToken(token: String, count: Int): Vector[dom.Element] =
    Vector(RulesTextRenderer.glyph(token),
      text("span", "site-token-count", count.toString))

  private[frontend] def siteDetails(site: GameSite): dom.Element = {
    val presentation = SiteCardPresentation.from(site)
    val details = element("div", "site-details")
    // Denizens and relics share one row: capacity and relic slots always sum
    // to three, so one row holds every card a site can ever have.
    val cards = element("div", "site-cards")
    site.denizens.foreach { denizen =>
      val card = denizen.details.fold[dom.Element](
        facedownCard("denizen"))(CardFace.render)
      card.setAttribute("data-denizen-id", denizen.denizenId)
      cards.appendChild(card)
    }
    (site.denizens.size until site.denizenCapacity)
      .foreach(_ => cards.appendChild(emptySlot("denizen")))
    // A peeked relic is a real card sitting face-down: CardFace gives it the
    // knowable pip and the hover reveal, in the same box as an unknown one.
    presentation.peekedRelics.foreach(value =>
      cards.appendChild(CardFace.render(value.card)))
    (0 until presentation.unknownRelicCount)
      .foreach(_ => cards.appendChild(facedownCard("relic")))
    (site.relics.facedownCount until site.relicCapacity)
      .foreach(_ => cards.appendChild(emptySlot("relic")))
    details.appendChild(cards)
    site.forces.foreach { forces =>
      val row = text("p", s"site-forces ${forceCssClass(forces)}",
        forceText(forces))
      forces match {
        case exile: SiteForces.Exile =>
          row.setAttribute("data-ruler-kind", "player")
          row.setAttribute("data-ruler-player-id", exile.rulerPlayerId)
        case _: SiteForces.Imperial =>
          row.setAttribute("data-ruler-kind", "empire")
        case _: SiteForces.Bandit =>
          row.setAttribute("data-ruler-kind", "bandit")
      }
      details.appendChild(row)
    }
    val footer = element("div", "site-footer")
    // One line of labels: the box has no room for a second line, so each
    // power's rules text rides on its hover title instead.
    val powers = element("p", "site-powers")
    site.powers.zipWithIndex.foreach { case (power, index) =>
      if (index > 0) powers.appendChild(dom.document.createTextNode(" · "))
      val item = text("span", "site-power", power.label)
      item.setAttribute("title", power.description.fold(power.label)(
        description => s"${power.label}: $description"))
      powers.appendChild(item)
    }
    footer.appendChild(powers)
    presentation.requirement.foreach(value =>
      footer.appendChild(requirementCorner(value)))
    details.appendChild(footer)
    details
  }

  /** Forge keeps its word because the catalog has no forge glyph; its price is
    * glyphs because favor and secrets do. Recover is a die-roll target rather
    * than a price, so it stays a number.
    */
  private def requirementCorner(value: SiteRequirement): dom.Element = {
    val node = element("span", "site-requirement")
    value match {
      case SiteRequirement.Forge(favor, secrets) =>
        node.appendChild(text("span", "site-requirement-label", "Forge"))
        (0 until favor).foreach(_ =>
          node.appendChild(RulesTextRenderer.glyph("favor")))
        (0 until secrets).foreach(_ =>
          node.appendChild(RulesTextRenderer.glyph("secret")))
      case SiteRequirement.Recover(difficulty) =>
        node.appendChild(text("span", "site-requirement-label",
          s"Recover $difficulty"))
    }
    node
  }

  private[frontend] def forceText(forces: SiteForces): String =
    s"${forces.label} x${forces.count}"

  private[frontend] def forceCssClass(forces: SiteForces): String = forces match {
    case exile: SiteForces.Exile => s"force-${exile.color.key}"
    case _: SiteForces.Imperial => "force-empire"
    case _: SiteForces.Bandit => "force-bandit"
  }

  /** A site relic the viewer cannot identify. Same box as a real relic, so
    * learning what it is -- which a negotiation can do mid-game -- swaps the
    * contents of a box that does not move.
    */
  private[frontend] def facedownCard(cardKind: String): dom.Element = {
    val node = element("span", s"card-face ${CardFace.boxClass(cardKind)} card-face-down")
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", s"Facedown $cardKind")
    node.appendChild(text("span", "card-back-letter", CardFace.backLetter(cardKind)))
    node
  }

  private[frontend] def emptySlot(cardKind: String): dom.Element = {
    val node = element("span",
      s"card-face ${CardFace.boxClass(cardKind)} card-slot-empty")
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", s"Empty $cardKind slot")
    node
  }

  private[frontend] def pileDisplay(
      label: String,
      count: Int,
      topCardKind: Option[String]
  ): dom.Element = {
    val pile = element("div", "pile-display")
    pile.appendChild(text("span", "pile-label", s"$label:"))
    val css = pileCardClasses(count)
    val symbol = pileSymbol(count, topCardKind)
    val back = text("span", css, if (symbol.isEmpty) "\u00a0" else symbol)
    back.setAttribute("role", "img")
    back.setAttribute("aria-label", if (count == 0) "Empty pile"
      else topCardKind match {
        case Some("denizen") => "Denizen card on top"
        case Some("vision") => "Vision card on top"
        case _ => "Facedown card; type hidden"
      })
    pile.appendChild(back)
    pile.appendChild(text("span", "pile-count", s"x$count"))
    pile
  }

  private[frontend] def pileSymbol(count: Int, topCardKind: Option[String]): String =
    if (count == 0) "" else topCardKind match {
      case Some("denizen") => "D"
      case Some("vision") => "V"
      case _ => ""
    }

  private[frontend] def pileCardClasses(count: Int): String =
    if (count == 0) "pile-card pile-empty" else "pile-card pile-back"

  private[frontend] final case class TakeWealthAction(
      label: String,
      command: GameCommand
  )

  private[frontend] final case class ViewerPresentation(
      showGameplayControls: Boolean,
      waitingForPlayerId: Option[String],
      waitingForDisplayName: Option[String],
      procedureStatus: Option[String] = None
  )

  private[frontend] def viewerPresentation(
      value: GameProjection,
      playerId: String
  ): ViewerPresentation = {
    if (value.oathkeeper.exists(_.winnerPlayerId.nonEmpty))
      return ViewerPresentation(showGameplayControls = false,
        waitingForPlayerId = None, waitingForDisplayName = None)
    // Task 5 fix: a parked walker `Decide`'s owner is projected `walkerDecision`
    // regardless of whose turn it is, and everyone else is projected
    // `walkerWaiting` naming that owner (see WalkerDecisionProjector.project/
    // waiting). Reading `activeParticipantId` below instead of these two
    // fields would leave an off-turn owner with no panel -- and the active
    // player waiting on them right back -- since neither side is the other's
    // active participant.
    if (value.walkerDecision.nonEmpty)
      return ViewerPresentation(showGameplayControls = true,
        waitingForPlayerId = None, waitingForDisplayName = None)
    if (value.walkerWaiting.nonEmpty)
      return ViewerPresentation(showGameplayControls = false,
        waitingForPlayerId = value.walkerWaiting.map(_.playerId),
        waitingForDisplayName = value.walkerWaiting.map(w =>
          playerDisplayName(value, w.playerId)))
    val controllingPlayer = value.activeParticipantId
    controllingPlayer match {
      case Some(activePlayerId) if activePlayerId != playerId =>
        ViewerPresentation(
          showGameplayControls = false,
          waitingForPlayerId = Some(activePlayerId),
          waitingForDisplayName = Some(playerDisplayName(value, activePlayerId))
        )
      case _ => ViewerPresentation(
        showGameplayControls = true,
        waitingForPlayerId = None,
        waitingForDisplayName = None
      )
    }
  }

  private[frontend] def showActActionControls(
      value: GameProjection,
      presentation: ViewerPresentation
  ): Boolean = value.actionSelectionOpen && presentation.showGameplayControls

  private[frontend] def actionLabel(kind: String): String = kind match {
    case "travel" => "Travel"
    case "challenge" => "Challenge"
    case "peoples-favor" => "People's Favor"
    case "darkest-secret" => "Darkest Secret"
    case other => other
  }

  private[frontend] def actionCategory(kind: String): String = kind match {
    case "search" | "travel" |
        "muster" | "trade-favor" | "trade-secret" | "recover" | "forge" |
        "challenge" | "campaign" => "major"
    // Using a power's "Action:" is a minor action like any other, so an
    // unrecognised kind lands there rather than in a section of its own.
    case _ => "minor"
  }

  private[frontend] val majorFamilyOrder: Vector[String] = Vector(
    "search", "travel", "campaign", "muster", "trade", "forge", "recover", "challenge")

  private[frontend] def actionFamily(kind: String): String = kind match {
    case "trade-favor" | "trade-secret" => "trade"
    case other => other
  }

  private[frontend] val actionCategoryOrder: Vector[(String, String)] =
    Vector("major" -> "Major actions", "minor" -> "Minor actions")

  private[frontend] final class ActionSections {
    private val contents = scala.collection.mutable.Map.empty[String,
      scala.collection.mutable.ArrayBuffer[(String, dom.Node)]]
    def appendKind(kind: String, node: dom.Node): Unit = contents
      .getOrElseUpdate(actionCategory(kind), scala.collection.mutable.ArrayBuffer.empty)
      .append(actionFamily(kind) -> node)
    def appendTo(panel: dom.Element): Unit = actionCategoryOrder.foreach { case (key, heading) =>
      contents.get(key).filter(_.nonEmpty).foreach { nodes =>
        val section = element("section", s"available-action-group action-group-$key")
        section.appendChild(text("h3", "action-group-heading", heading))
        val order = if (key == "major") majorFamilyOrder else nodes.map(_._1).distinct.toVector
        // One option per row: a run of buttons side by side reads as one blur.
        order.foreach(family => nodes.filter(_._1 == family).foreach { entry =>
          val row = element("div", "action-option")
          row.appendChild(entry._2); section.appendChild(row)
        })
        panel.appendChild(section)
      }
    }
  }

  private[frontend] def cardinalityInstruction(action: BoardTargetAction): String =
    if (action.maximum == 0) "No target is available; confirm to play this action."
    else if (action.explicitConfirm) "Choose one target, then confirm."
    else "Choose one target. Selection submits immediately."

  private[frontend] def candidateDetailText(
      candidate: BoardTargetCandidate): Option[String] =
    Option.when(candidate.details.nonEmpty)(candidate.details.mkString(" · "))

  private[frontend] def candidateDetailBadge(
      candidate: BoardTargetCandidate): Option[dom.Element] =
    candidateDetailText(candidate).map(value => text("span", "target-detail-badge", value))

  private[frontend] def winnerColorClass(value: GameProjection,
      playerId: String): String =
    PlayerColorCss.of(value.players.find(_.playerId == playerId).map(_.color))

  private[frontend] def winnerBanner(value: GameProjection,
      winner: String, victory: String): dom.Element = {
    val banner = element("div", s"victory-banner ${winnerColorClass(value, winner)}")
    banner.setAttribute("role", "status")
    banner.appendChild(playerReference(value, winner))
    banner.appendChild(dom.document.createTextNode(s" wins — $victory victory"))
    banner
  }

  private[frontend] def siteTargetClasses(candidate: Boolean,
      selected: Boolean): String =
    Vector("site", if (candidate) "board-target" else "site-readonly",
      if (selected) "board-target-selected" else "").filter(_.nonEmpty).mkString(" ")

  private[frontend] def commandForSelection(action: BoardTargetAction,
      targets: Vector[BoardTargetRef], playerId: String): Option[GameCommand] =
    (action.actionKind, targets) match {
      // Travel moved onto the generic walker (batch-1 Task 5), so the
      // destination the player just picked rides `StartWalker`'s start
      // selection instead of a `Travel` intent of its own -- as a plain site
      // reference, which is all the wire says about it. Modifiers are folded
      // into this same intent by `ModifierWorkflow.submission`, which is why
      // they are empty here.
      case ("travel", Vector(BoardTargetRef.Site(site))) =>
        Some(GameCommand.StartWalker("travel", Vector.empty,
          Vector(oathdigital.protocol.WalkerStartArgWire("site", site))))
      case _ => None
    }

  private def takeWealth(resource: String): GameCommand =
    GameCommand.StartWalker("take-wealth", Vector.empty,
      Vector(WalkerStartArgWire("button", resource)))

  private[frontend] def takeWealthActions(
      value: GameProjection,
      playerId: String
  ): Vector[TakeWealthAction] =
    if (value.phase != "wake" ||
        !viewerPresentation(value, playerId).showGameplayControls) Vector.empty
    // Take Wealth moved onto the generic walker (batch-1 Task 7), so the
    // resource the player picks rides `StartWalker`'s start selection as the
    // button it is -- a choice with no game object behind it -- instead of a
    // `TakeWealth` intent of its own. The legal-control keys are unchanged:
    // the server still decides which of the two it offers.
    else Vector(
      "takeFavor" -> TakeWealthAction(
        "Take Wealth: 1 favor", takeWealth("favor")),
      "takeSecret" -> TakeWealthAction(
        "Take Wealth: 1 secret", takeWealth("secret"))
    ).collect {
      case (legalControl, action)
          if value.legalControls.contains(legalControl) => action
    }

  private[frontend] def playerReference(
      value: GameProjection,
      playerId: String
  ): dom.Element = {
    val player = value.players.find(_.playerId == playerId)
    val node = text(
      "span",
      s"player-ref ${PlayerColorCss.of(player.map(_.color))}",
      player.fold(playerId)(_.displayName)
    )
    node.setAttribute("data-player-id", playerId)
    node
  }

  private[frontend] def facedownAdviserLaunchCount(minor: MinorActionsState): Int =
    if (minor.advisers.nonEmpty) 1 else 0

  private[frontend] def protocolNegotiationTerms(value: NegotiationTermsInput): NegotiationTerms =
    NegotiationTerms(
      value.transfers.map(v => NegotiationTransfer(
        v.recipientPlayerId, v.favor, v.relicIds)),
      value.disclosures.map { disclosure =>
        val information: NegotiationInformation = disclosure.kind match {
          case "adviser" => NegotiationInformation.Adviser(
            disclosure.ownerPlayerId.get,
            WorldCard(disclosure.cardKind.get, disclosure.cardId))
          case "held-relic" => NegotiationInformation.HeldRelic(
            disclosure.ownerPlayerId.get, disclosure.cardId)
          case "site-relic" => NegotiationInformation.SiteRelic(
            disclosure.siteId.get, disclosure.cardId)
        }
        NegotiationDisclosure(disclosure.recipientPlayerId, information)
      })

  private[frontend] def playerDisplayName(
      value: GameProjection,
      playerId: String
  ): String =
    value.players.find(_.playerId == playerId)
      .fold(playerId)(_.displayName)

  private[frontend] def freshGameId(): String =
    s"manual-${js.Date.now().toLong}-${(js.Math.random() * 1000000).toInt}"

  private[frontend] def canonicalGameId(pathname: String): Option[String] =
    pathname.split("/", -1).toVector match {
      case Vector("", "games", encoded) if encoded.nonEmpty =>
        try Option(js.URIUtils.decodeURIComponent(encoded)).filter(_.nonEmpty)
        catch { case scala.util.control.NonFatal(_) => None }
      case _ => None
    }

  private[frontend] def queryParameter(name: String): Option[String] =
    dom.window.location.search.stripPrefix("?").split("&").toVector
      .flatMap { pair =>
        pair.split("=", 2).toVector match {
          case Vector(key, value) if key == name =>
            Some(js.URIUtils.decodeURIComponent(value))
          case _ => None
        }
      }.headOption.filter(_.nonEmpty)

  private[frontend] def sessionUrl(gameId: String, playerId: String): String =
    s"/?mode=server&gameId=${js.URIUtils.encodeURIComponent(gameId)}" +
      s"&playerId=${js.URIUtils.encodeURIComponent(playerId)}"

  private[frontend] def updateUrl(gameId: String, playerId: String): Unit =
    dom.window.history.replaceState(null, "", sessionUrl(gameId, playerId))

  private[frontend] def button(label: String, className: String): dom.html.Button = {
    val node =
      dom.document.createElement("button").asInstanceOf[dom.html.Button]
    node.className = className
    node.textContent = label
    node
  }

  private[frontend] def text(tag: String, className: String, value: String): dom.Element = {
    val node = element(tag, className)
    node.textContent = value
    node
  }

  private[frontend] def element(tag: String, className: String): dom.Element = {
    val node = dom.document.createElement(tag)
    if (className.nonEmpty) node.setAttribute("class", className)
    node
  }
}

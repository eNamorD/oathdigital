package oathdigital.application

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.gameplay.{PlayerSecretSummary, ReadyGame}
import oathdigital.gameplay.setup.{FirstGameParticipant, FirstGameSetupMaterial}
import oathdigital.model._
import oathdigital.protocol.projection._

private[application] final class GamePresentationProjector(
    catalog: ExecutableCatalog
) {
  private val siteNames = catalog.sites.map(site => site.id -> site.name).toMap
  private val denizenNames = catalog.denizens.map(d =>
    DenizenId(d.id.value) -> d.name).toMap
  private val edificeNames = catalog.edifices.map { edifice =>
    EdificeId(edifice.id.value) ->
      (edifice.intact.name -> edifice.ruined.name)
  }.toMap
  private val edificesById = catalog.edifices.map(e => e.id.value -> e).toMap
  private val siteDefinitions = catalog.sites.map(site => site.id -> site).toMap

  def siteLabel(id: SiteId): String = siteNames.getOrElse(id, safeLabel(id.value))
  def denizenLabel(id: DenizenId): String =
    denizenNames.getOrElse(id, safeLabel(id.value))
  def relicLabel(id: RelicId): String = catalog.relics.find(
    _.id.value == id.value).map(_.name).getOrElse(safeLabel(id.value))
  def edificeLabel(id: EdificeId, side: EdificeSide): String =
    edificeNames.get(id).fold(safeLabel(id.value)) {
      case (intact, ruined) => side match {
        case EdificeSide.Intact => intact
        case EdificeSide.Ruined => ruined
      }
    }

  private[application] def edificeCardDetails(value: EdificeState): CardDetailsProjection = {
    val definition = edificesById.get(value.id.value)
    val face = definition.map(e => value.side match {
      case EdificeSide.Intact => e.intact
      case EdificeSide.Ruined => e.ruined
    })
    CardDetailsProjection(value.id.value, "edifice", edificeLabel(value.id, value.side),
      suit = definition.map(_.suit.key),
      restrictions = face.map(e => restrictionName(e.restrictions)),
      rulesText = face.map(_.rulesText),
      side = Some(value.side match {
        case EdificeSide.Intact => "intact"
        case EdificeSide.Ruined => "ruined"
      }), favor = value.tokens.favor, secrets = value.tokens.secrets)
  }

  def setupPlayers(participants: Vector[FirstGameParticipant]) =
    participants.map { participant =>
      SetupPlayerProjection(participant.playerId.value,
        safeLabel(participant.playerId.value), "exile", participant.color.value)
    }

  def readyPlayers(ready: ReadyGame) = ready.game.current.players.map { player =>
    SetupPlayerProjection(player.player.value, safeLabel(player.player.value),
      "exile", ready.playerColors(player.player).value)
  }

  def setupWorld(sites: Vector[SiteId]): Vector[SetupRegionProjection] = Vector(
    region("cradle", sites.take(2)),
    region("provinces", sites.slice(2, 5)),
    region("hinterland", sites.slice(5, 8)))

  def setupWorld(material: FirstGameSetupMaterial): Vector[SetupRegionProjection] = Vector(
    region("cradle", material.map.cradle, material.map.sites,
      material.commonCards.discard(Region.Cradle)),
    region("provinces", material.map.provinces, material.map.sites,
      material.commonCards.discard(Region.Provinces)),
    region("hinterland", material.map.hinterland, material.map.sites,
      material.commonCards.discard(Region.Hinterland)))

  def readyWorld(ready: ReadyGame, viewer: Option[PlayerId]) = {
    val current = ready.game.current
    Vector(
      region("cradle", current.map.cradle, current.map.sites,
        current.commonCards.discard(Region.Cradle), Some(ready), viewer),
      region("provinces", current.map.provinces, current.map.sites,
        current.commonCards.discard(Region.Provinces), Some(ready), viewer),
      region("hinterland", current.map.hinterland, current.map.sites,
        current.commonCards.discard(Region.Hinterland), Some(ready), viewer))
  }

  private def region(id: String, sites: Vector[SiteId],
      states: Map[SiteId, SiteState] = Map.empty,
      discard: Vector[CardId] = Vector.empty,
      ready: Option[ReadyGame] = None,
      viewer: Option[PlayerId] = None): SetupRegionProjection =
    SetupRegionProjection(id, sites.map(site =>
      siteProjection(site, states.get(site), ready, viewer)), discard.size,
      discard.lastOption.map(cardKind))

  private def siteProjection(siteId: SiteId, state: Option[SiteState],
      ready: Option[ReadyGame], viewer: Option[PlayerId]): SetupSiteProjection = {
    val definition = siteDefinitions.get(siteId)
    SetupSiteProjection(siteId.value, siteLabel(siteId),
      state.fold(0)(_.tokens.favor), state.fold(0)(_.tokens.secrets),
      definition.fold(0)(_.capacity), definition.fold(0)(_.relicSlots),
      state.toVector.flatMap(_.denizens).map { denizen =>
        val label = denizen match {
          case value: DenizenState => denizenLabel(value.id)
          case value: EdificeState => edificeLabel(value.id, value.side)
        }
        val details = denizen match {
          case value: DenizenState => Some(cardDetails(value.id,
            Some(value.orientation), hidden = false).copy(
              favor = value.tokens.favor, secrets = value.tokens.secrets))
          case value: EdificeState => Some(edificeCardDetails(value))
        }
        SiteCardProjection(denizen.id.value, label, details)
      },
      SiteRelicsProjection(state.fold(0)(_.relics.size), for {
        game <- ready.toVector
        player <- viewer.toVector
        known <- game.knowledge.siteRelics.getOrElse(player, Map.empty)
          .getOrElse(siteId, Vector.empty)
        relic <- state.toVector.flatMap(_.relics).filter(_.id == known)
      } yield cardDetails(relic.id, Some(Orientation.FaceDown), hidden = false)),
      definition.fold(0)(_.defense),
      definition.flatMap(site => Option.when(site.forgeRequirements.isEmpty)(
        site.recoverDifficulty).flatten),
      definition.flatMap(_.forgeRequirements).map(tokens =>
        ForgeCostProjection(tokens.favor, tokens.secrets)),
      definition.toVector.flatMap(_.handlers).map(sitePower),
      state.flatMap(_.forces match {
        case value: SiteForces.Occupied => ready match {
          case Some(game) => Some(forceProjection(value, game))
          case None if value.kind == ForceKind.Bandit => Some(
            SiteForcesProjection("bandit", value.count, "bandit", None,
              "Bandit Warbands", "bandit"))
          case None => None
        }
        case SiteForces.Empty => None
      }))
  }

  private def forceProjection(forces: SiteForces.Occupied,
      ready: ReadyGame): SiteForcesProjection = {
    val ruler = SiteRule.ruler(forces, ready.game.current.players).fold(
      error => throw new IllegalStateException(s"invalid site ruler mapping: $error"),
      identity)
    forces.kind match {
      case ForceKind.Exile(_) =>
        val SiteRuler.Player(playerId) = ruler: @unchecked
        val color = ready.playerColors.getOrElse(playerId,
          throw new IllegalStateException(
            s"missing color for site ruler ${playerId.value}"))
        val colorLabel = color.value.headOption.fold(color.value)(head =>
          s"${head.toUpper}${color.value.drop(1)}")
        SiteForcesProjection("exile", forces.count, "player", Some(playerId.value),
          s"$colorLabel Warbands", color.value)
      case ForceKind.Imperial => SiteForcesProjection("imperial", forces.count,
        "empire", None, "Imperial Warbands", "empire")
      case ForceKind.Bandit => SiteForcesProjection("bandit", forces.count,
        "bandit", None, "Bandit Warbands", "bandit")
    }
  }

  def playerBoards(ready: ReadyGame, viewer: Option[PlayerId]) = {
    val players = ready.game.current.players
    val start = viewer.flatMap(id => Option(players.indexWhere(_.player == id))
      .filter(_ >= 0)).getOrElse(0)
    (players.drop(start) ++ players.take(start)).map { player =>
      val secrets = PlayerSecretSummary.derive(ready, player.player)
        .fold(error => throw new IllegalStateException(error), identity)
      def identifies(id: CardId, orientation: Orientation,
          area: PlayerCardArea): Boolean = identifiesCard(ready, viewer, id,
        Some(orientation), CardContainer.Player(player.player, area))
      PlayerBoardProjection(player.player.value, player.board.warbands,
        player.board.favor, player.board.faceUpSecrets, player.board.faceDownSecrets,
        secrets.committed, secrets.totalSecrets,
        player.board.supply.supply, player.pawnSite.map(_.value),
        player.advisers.map(card =>
          if (identifies(card.id, adviserOrientation(card),
              PlayerCardArea.Advisers))
            cardDetails(card.id, Some(adviserOrientation(card)),
              hidden = false)
          else hiddenCard("adviser")),
        player.relics.map(card =>
          if (identifies(card.id, card.orientation, PlayerCardArea.Relics))
            cardDetails(card.id, Some(card.orientation), hidden = false)
          else hiddenCard("relic")),
        player.revealedVision.map(card => cardDetails(card.id,
          Some(card.orientation), hidden = false)),
        banners(ready).filter(_.holderPlayerId.contains(player.player.value)))
    }
  }

  def setupPlayerBoards(material: FirstGameSetupMaterial): Vector[PlayerBoardProjection] =
    material.players.map(player => PlayerBoardProjection(player.player.value,
      player.board.warbands, player.board.favor, player.board.faceUpSecrets,
      player.board.faceDownSecrets, 0,
      player.board.faceUpSecrets + player.board.faceDownSecrets,
      player.board.supply.supply,
      player.pawnSite.map(_.value),
      player.advisers.map(card => hiddenCard("adviser")), Vector.empty, None))

  def banners(ready: ReadyGame): Vector[BannerProjection] = {
    val current = ready.game.current
    Vector(
      BannerProjection("peoples-favor", current.banners.peoplesFavor.active match {
        case PeoplesFavorFace.Mob => "mob"
        case PeoplesFavorFace.GrandCouncil => "grand-council"
      }, current.banners.peoplesFavor.holder.map(_.value),
        current.banners.peoplesFavor.favor),
      BannerProjection("darkest-secret", current.banners.darkestSecret.active match {
        case DarkestSecretFace.WanderingFlame => "wandering-flame"
        case DarkestSecretFace.Festival => "festival"
      }, current.banners.darkestSecret.holder.map(_.value),
        current.banners.darkestSecret.secrets))
  }

  private def hiddenCard(kind: String) = CardDetailsProjection(
    "hidden", kind, s"Facedown $kind", orientation = Some("face-down"), hidden = true)

  /** Whether `viewer` may be told WHICH card this is, as opposed to merely
    * that a card is there.
    *
    * One rule with two consumers, which redact differently because they have
    * to. [[playerBoards]] above substitutes [[hiddenCard]] and keeps the slot
    * visible, since a board needs to show that an adviser is there. A walker
    * decision option cannot do that: its reference IS the card's identity and
    * the client answers by sending it back, so an unidentifiable option is not
    * an option at all and `WalkerDecisionProjector` suppresses the whole
    * decision instead. Both ask this one question rather than each writing its
    * own disclosure test.
    *
    * The clauses, and where each comes from:
    *
    *  - A site's denizens are named to every viewer whatever their
    *    orientation. That is not a judgement made here; `siteProjection`
    *    above already projects them that way, and a rule stricter than the
    *    world projection would hide a card the same response already names.
    *    Tighten both together or neither.
    *  - A site's facedown relics are named only to a viewer who has peeked
    *    (`knowledge.siteRelics`) or whose pawn stands at that site. The pawn
    *    clause is Recover's shipped disclosure: succeeding at Recover means
    *    looking through the site's facedown relics to choose one, which is
    *    why the relic decision could name them before this predicate existed.
    *    It is stated once, here, instead of being a blanket exemption.
    *  - A player's facedown cards are named to their owner, or to a viewer
    *    whose recorded `knowledge` covers them -- the same two conditions
    *    [[playerBoards]] applied inline before this method.
    *  - A player's temporary hand is named to that player and to nobody
    *    else. It is drawn-but-unresolved cards, not a board slot, and it is
    *    the one player area with no orientation to reason about.
    *  - Everything else -- decks, discards, the reliquary, set-aside relics,
    *    the dispossessed pile, suited reserves, atlas sites -- is never
    *    identified. A card in a deck has no orientation at all, so it must be
    *    rejected by its container rather than by being facedown.
    */
  def identifiesCard(ready: ReadyGame, viewer: Option[PlayerId], id: CardId,
      orientation: Option[Orientation], container: CardContainer): Boolean = {
    // Known-faceup, never merely "not facedown". A card whose container
    // holds no state at all -- a deck, a discard, a temporary hand -- has NO
    // orientation, and reading that absence as public is how such a card
    // would slip through a clause meant for a board slot.
    val faceup = orientation.contains(Orientation.FaceUp)
    container match {
      case CardContainer.Site(_, SiteCardArea.Denizens) => true
      case CardContainer.Site(site, SiteCardArea.Relics) =>
        faceup || viewer.exists(player =>
          ready.knowledge.siteRelics.getOrElse(player, Map.empty)
            .getOrElse(site, Vector.empty).contains(id) ||
            pawnSiteOf(ready, player).contains(site))
      // A temporary hand is the cards a player has drawn and not yet
      // resolved. It is private to them outright -- `PendingProcedureProjector`
      // projects a Search hand only to the drawing actor -- and its cards
      // carry no orientation to reason about, so ownership is the whole
      // rule and there is no faceup case to fall through to.
      case CardContainer.Player(owner, PlayerCardArea.Hand) =>
        viewer.contains(owner)
      case CardContainer.Player(owner, area) =>
        faceup || viewer.exists(player => player == owner ||
          knownToViewer(ready, player, id, area))
      case _ => false
    }
  }

  private def pawnSiteOf(ready: ReadyGame, player: PlayerId): Option[SiteId] =
    ready.game.current.players.find(_.player == player).flatMap(_.pawnSite)

  /** Recorded knowledge for the areas that have any: relics by
    * `heldRelics`, world cards by `advisers`. `Hand` never reaches here --
    * [[identifiesCard]] settles it on ownership alone.
    */
  private def knownToViewer(ready: ReadyGame, player: PlayerId, id: CardId,
      area: PlayerCardArea): Boolean = area match {
    case PlayerCardArea.Relics =>
      ready.knowledge.heldRelics.getOrElse(player, Vector.empty).contains(id)
    case _ => ready.knowledge.advisers.getOrElse(player, Vector.empty)
      .exists(_.value == id.value)
  }

  def adviserOrientation(card: AdviserState): Orientation = card match {
    case value: DenizenState => value.orientation
    case value: VisionState => value.orientation
  }
  def orientationName(value: Orientation): String = value match {
    case Orientation.FaceUp => "face-up"
    case Orientation.FaceDown => "face-down"
  }
  def cardKind(card: CardId): String = card match {
    case _: VisionId => "vision"
    case _ => "denizen"
  }

  def cardDetails(id: CardId, orientation: Option[Orientation],
      hidden: Boolean): CardDetailsProjection = id match {
    case value: DenizenId => catalog.denizens.find(_.id.value == value.value).fold(
      CardDetailsProjection(value.value, "denizen", worldCardLabel(value),
        orientation = orientation.map(orientationName), hidden = hidden)) { d =>
      CardDetailsProjection(value.value, "denizen", d.name, Some(d.suit.key),
        Some(restrictionName(d.restrictions)), Some(d.rulesText),
        orientation.map(orientationName), hidden = hidden)
    }
    case value: VisionId => VisionCardPresentation.byId.get(value.value).fold(
      CardDetailsProjection(value.value, "vision", safeLabel(value.value),
        orientation = orientation.map(orientationName), hidden = hidden)) { vision =>
      CardDetailsProjection(value.value, "vision", vision.name,
        rulesText = Some(vision.rulesText),
        orientation = orientation.map(orientationName), hidden = hidden)
    }
    case value: RelicId => catalog.relics.find(_.id.value == value.value).fold(
      CardDetailsProjection(value.value, "relic", safeLabel(value.value),
        orientation = orientation.map(orientationName), hidden = hidden)) { r =>
      CardDetailsProjection(value.value, "relic", r.name, rulesText = Some(r.rulesText),
        orientation = orientation.map(orientationName), relicValue = Some(r.value),
        defense = Some(r.defense), hidden = hidden)
    }
    case other => CardDetailsProjection(other.value, other.getClass.getSimpleName,
      safeLabel(other.value), orientation = orientation.map(orientationName), hidden = hidden)
  }

  private def restrictionName(value: CardRestrictions): String = value match {
    case CardRestrictions.Unrestricted => "unrestricted"
    case CardRestrictions.Locked => "locked"
    case CardRestrictions.SiteOnly => "site-only"
    case CardRestrictions.AdviserOnly => "adviser-only"
    case CardRestrictions.LockedAdviserOnly => "locked-adviser-only"
  }
  def safeLabel(id: String): String = id.split(":").lastOption.getOrElse(id)
    .split("-").map(_.capitalize).mkString(" ")
  private def worldCardLabel(id: WorldCardId): String = id match {
    case value: DenizenId => denizenLabel(value)
    case value: VisionId => safeLabel(value.value)
  }
  private def sitePower(handler: String): SitePowerProjection = {
    val kind = handler.split('.').lastOption.getOrElse(handler)
    val known = Map(
      "coast" -> ("Coast", "Travel along the Coast route."),
      "mountain" -> ("Mountain", "Travel here costs additional Supply."),
      "river" -> ("River", "Part of the River route."),
      "island" -> ("Island", "Travel here follows Island travel rules."),
      "pass" -> ("Pass", "Travel through the Pass is restricted."),
      "plains" -> ("Plains", "This site has the Plains site power."))
    known.get(kind).fold(SitePowerProjection(kind, safeLabel(kind), None)) {
      case (label, description) => SitePowerProjection(kind, label, Some(description))
    }
  }
}

package oathdigital.model

sealed trait SiteCardArea extends Product with Serializable
object SiteCardArea {
  case object Denizens extends SiteCardArea
  case object Relics extends SiteCardArea
}

sealed trait PlayerCardArea extends Product with Serializable
object PlayerCardArea {
  case object Hand extends PlayerCardArea
  case object Advisers extends PlayerCardArea
  case object Relics extends PlayerCardArea
  case object RevealedVision extends PlayerCardArea
}

sealed trait LineageCardArea extends Product with Serializable
object LineageCardArea {
  case object Legacies extends LineageCardArea
  case object StartingAdvisers extends LineageCardArea
}

sealed trait CardContainer extends Product with Serializable
object CardContainer {
  final case class Deck(deck: CardDeck) extends CardContainer
  final case class RegionalDiscard(region: Region) extends CardContainer
  final case class Site(site: SiteId, area: SiteCardArea)
      extends CardContainer
  final case class Player(player: PlayerId, area: PlayerCardArea)
      extends CardContainer
  final case class Lineage(lineage: LineageId, area: LineageCardArea)
      extends CardContainer
  case object Reliquary extends CardContainer
  case object SetAsideRelics extends CardContainer
  case object Dispossessed extends CardContainer
  final case class SuitedReserve(suit: Suit) extends CardContainer
  final case class AtlasSite(
      atlasPosition: Int,
      site: SiteId,
      area: SiteCardArea
  ) extends CardContainer
}

final case class CardLocation(container: CardContainer, position: Int) {
  require(position >= 0, "card position must be non-negative")
}

final case class LocatedCard(
    id: CardId,
    state: Option[CardState],
    location: CardLocation
)

sealed trait CardIndexProblem extends Product with Serializable
object CardIndexProblem {
  final case class DuplicateCard(
      id: CardId,
      locations: Vector[CardLocation]
  ) extends CardIndexProblem

  final case class MissingCard(id: CardId) extends CardIndexProblem
}

/**
 * Derived reverse lookup over container-owned card state.
 *
 * It is intentionally not part of `OathGame` and must not be serialized.
 */
final case class CardIndex private (byId: Map[CardId, LocatedCard]) {
  def get(id: CardId): Option[LocatedCard] = byId.get(id)
  def locationOf(id: CardId): Option[CardLocation] = get(id).map(_.location)
  def stateOf(id: CardId): Option[CardState] = get(id).flatMap(_.state)
  def ids: Set[CardId] = byId.keySet
}

object CardIndex {
  def from(
      game: OathGame,
      expectedCards: Set[CardId] = Set.empty
  ): Either[Vector[CardIndexProblem], CardIndex] = {
    val located = Vector.newBuilder[LocatedCard]

    def addId(
        id: CardId,
        container: CardContainer,
        position: Int
    ): Unit =
      located += LocatedCard(
        id,
        state = None,
        CardLocation(container, position)
      )

    def addState(
        state: CardState,
        container: CardContainer,
        position: Int
    ): Unit =
      located += LocatedCard(
        state.id,
        state = Some(state),
        CardLocation(container, position)
      )

    def addIds(
        ids: Iterable[CardId],
        container: CardContainer
    ): Unit =
      ids.iterator.zipWithIndex.foreach { case (id, position) =>
        addId(id, container, position)
      }

    def addStates(
        states: Iterable[CardState],
        container: CardContainer
    ): Unit =
      states.iterator.zipWithIndex.foreach { case (state, position) =>
        addState(state, container, position)
      }

    val common = game.current.commonCards
    addIds(common.worldDeck, CardContainer.Deck(CardDeck.World))
    addIds(common.relicDeck, CardContainer.Deck(CardDeck.Relic))
    addIds(common.edificeDeck, CardContainer.Deck(CardDeck.Edifice))
    addIds(common.legacyDeck, CardContainer.Deck(CardDeck.Legacy))

    Region.all.foreach { region =>
      addIds(
        common.discard(region),
        CardContainer.RegionalDiscard(region)
      )
    }

    game.current.temporaryHands.toVector.sortBy(_._1.value).foreach {
      case (player, cards) =>
        addIds(
          cards,
          CardContainer.Player(player, PlayerCardArea.Hand)
        )
    }

    game.current.players.foreach { player =>
      addStates(
        player.advisers,
        CardContainer.Player(player.player, PlayerCardArea.Advisers)
      )
      addStates(
        player.relics,
        CardContainer.Player(player.player, PlayerCardArea.Relics)
      )
      player.revealedVision.foreach { vision =>
        addState(
          vision,
          CardContainer.Player(
            player.player,
            PlayerCardArea.RevealedVision
          ),
          0
        )
      }
    }

    game.current.map.sites.toVector
      .sortBy(_._1.value)
      .foreach { case (siteId, site) =>
        addStates(
          site.denizens,
          CardContainer.Site(siteId, SiteCardArea.Denizens)
        )
        addStates(
          site.relics,
          CardContainer.Site(siteId, SiteCardArea.Relics)
        )
      }

    game.campaign.lineages.toVector
      .sortBy(_._1.value)
      .foreach { case (lineageId, lineage) =>
        addStates(
          lineage.legacies,
          CardContainer.Lineage(lineageId, LineageCardArea.Legacies)
        )
        addStates(
          lineage.startingAdvisers,
          CardContainer.Lineage(
            lineageId,
            LineageCardArea.StartingAdvisers
          )
        )
      }

    addIds(game.campaign.reliquary, CardContainer.Reliquary)
    addIds(game.current.setAsideRelics, CardContainer.SetAsideRelics)
    addIds(game.campaign.dispossessed, CardContainer.Dispossessed)

    Suit.all.foreach { suit =>
      addIds(
        game.campaign.suitedReserves.getOrElse(suit, Vector.empty),
        CardContainer.SuitedReserve(suit)
      )
    }

    game.campaign.atlas.entries.zipWithIndex.foreach {
      case (stored: AtlasEntry.StoredSite, atlasPosition) =>
        addStates(
          stored.denizens,
          CardContainer.AtlasSite(
            atlasPosition,
            stored.id,
            SiteCardArea.Denizens
          )
        )
        addStates(
          stored.relics,
          CardContainer.AtlasSite(
            atlasPosition,
            stored.id,
            SiteCardArea.Relics
          )
        )
      case (AtlasEntry.EmpireDivider, _) => ()
    }

    val allLocated = located.result()
    val grouped = allLocated.groupBy(_.id)
    val duplicateProblems = grouped.toVector
      .collect {
        case (id, occurrences) if occurrences.size > 1 =>
          CardIndexProblem.DuplicateCard(
            id,
            occurrences.map(_.location)
          )
      }
      .sortBy(_.id.value)
    val missingProblems = (expectedCards -- grouped.keySet).toVector
      .sortBy(id => (id.kind, id.value))
      .map(CardIndexProblem.MissingCard)
    val problems = duplicateProblems ++ missingProblems

    if (problems.nonEmpty) Left(problems)
    else
      Right(
        CardIndex(
          grouped.iterator.map { case (id, occurrences) =>
            id -> occurrences.head
          }.toMap
        )
      )
  }
}

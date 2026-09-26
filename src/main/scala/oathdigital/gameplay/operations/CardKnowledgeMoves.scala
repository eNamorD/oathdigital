package oathdigital.gameplay.operations

import oathdigital.model._

/** Knowledge follows the card (Game Log design, "Knowledge follows the
  * card"). A player who knew a card keeps knowing it when it moves; a card
  * that goes into any card pile (a deck, a regional discard, the reliquary,
  * the set-aside relics, the dispossessed or the atlas) is known by nobody,
  * since no one at the table tracks a card inside a pile.
  *
  * `CardKnowledge` is derived during replay and never journaled, so a game
  * loaded after this change re-derives the fuller knowledge.
  */
private[operations] object CardKnowledgeMoves:
  /** `moved` pairs each card's destination with where it lay before the
    * batch. */
  def follow(ready: ReadyGame, moved: Vector[(Location, LocatedCard)])
      : ReadyGame =
    ready.copy(knowledge = moved.foldLeft(ready.knowledge) {
      case (knowledge, (to, located)) => after(knowledge, located, to) })

  private def after(knowledge: CardKnowledge, located: LocatedCard,
      to: Location): CardKnowledge =
    if hidden(to) then forget(knowledge, located.id)
    else located.location.container match
      // An owner knows its own cards without a record; a move between its
      // own hand and play area is not leaving them. A temporary hand is not
      // a row other players could learn a card in, so leaving it records
      // nothing.
      case CardContainer.Player(owner, area)
          if area != PlayerCardArea.Hand && !ownedBy(to, owner) =>
        remember(knowledge, owner, located.id)
      case CardContainer.Site(site, SiteCardArea.Relics) => located.id match
        case relic: RelicId => carry(knowledge, site, relic)
        case _ => knowledge
      case _ => knowledge

  private def ownedBy(to: Location, owner: PlayerId): Boolean = to match
    case Location.PlayArea(player) => player == owner
    case Location.Hand(player) => player == owner
    case _ => false

  private def hidden(to: Location): Boolean = to match
    case Location.Deck(_) | Location.RegionalDiscard(_) | Location.Reliquary |
        Location.SetAsideRelics | Location.Dispossessed | Location.Atlas => true
    case _ => false

  private def remember(knowledge: CardKnowledge, player: PlayerId,
      id: CardId): CardKnowledge = id match
    case relic: RelicId => knowledge.copy(heldRelics = knowledge.heldRelics
      .updated(player, (knowledge.heldRelics.getOrElse(player, Vector.empty)
        :+ relic).distinct))
    case card: WorldCardId => knowledge.copy(advisers = knowledge.advisers
      .updated(player, (knowledge.advisers.getOrElse(player, Vector.empty)
        :+ card).distinct))
    case _ => knowledge

  /** Every player who peeked `relic` at `site` knows it wherever it goes. */
  private def carry(knowledge: CardKnowledge, site: SiteId, relic: RelicId)
      : CardKnowledge =
    val peekers = knowledge.siteRelics.collect {
      case (player, sites)
          if sites.getOrElse(site, Vector.empty).contains(relic) => player
    }
    peekers.foldLeft(knowledge) { (known, player) =>
      val sites = known.siteRelics(player)
      remember(known.copy(siteRelics = known.siteRelics.updated(player,
        sites.updated(site, sites(site).filterNot(_ == relic)))), player, relic)
    }

  private def forget(knowledge: CardKnowledge, id: CardId): CardKnowledge =
    CardKnowledge(
      siteRelics = knowledge.siteRelics.view.mapValues(_.view
        .mapValues(_.filterNot(_ == id)).toMap).toMap,
      advisers = knowledge.advisers.view.mapValues(_.filterNot(_ == id)).toMap,
      heldRelics = knowledge.heldRelics.view
        .mapValues(_.filterNot(_ == id)).toMap)

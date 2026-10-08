package oathdigital.frontend

/** Whether an option has a place on the table (board decisions design,
  * "Board home"): a site's box, a card or an empty slot in a site's row or
  * on a player board, a revealed Vision spot, the world deck or a discard
  * pile, a banner, a
  * favor bank, or a player's name and pawns. An option
  * with none is drawn in the Action panel. The rule mirrors the keys the
  * table's renderers hand `BoardOptions`, so a home here is an element there.
  */
private[frontend] object BoardHomes:
  def has(value: GameProjection, option: DecisionOptionState): Boolean =
    option.kind match
      case "site" => sites(value).exists(_.siteId == option.id)
      case "denizen" | "edifice" | "vision" =>
        siteCards(value).contains(option.id) ||
          shown(value.playerBoards.flatMap(_.advisers)).contains(option.id)
      case "relic" =>
        siteRelics(value).contains(option.id) ||
          shown(value.playerBoards.flatMap(_.relics)).contains(option.id)
      case "adviser-slot" | "adviser-play" =>
        val id = if option.kind == "adviser-play" then adviserSlot(option.id)
          else option.id
        slot(id).exists((player, at) => value.playerBoards
          .exists(board => board.playerId == player &&
            at < math.max(board.advisers.size, board.adviserLimit)))
      case "site-slot" =>
        slot(option.id).exists((site, at) => sites(value)
          .exists(found => found.siteId == site && at < found.denizenCapacity))
      case "revealed-vision" =>
        value.playerBoards.exists(_.playerId == option.id)
      case "discard-pile" => value.world.exists(_.regionId == option.id)
      // The world deck is drawn at the top of the World pane; no other deck
      // is drawn.
      case "deck" => option.id == "world"
      case "relic-slot" =>
        slot(option.id).exists((player, at) => value.playerBoards
          .exists(board => board.playerId == player &&
            board.relics.isDefinedAt(at)))
      case "banner" => value.banners.exists(_.key == option.id)
      case "favor-bank" => value.favorBanks.exists(_.suit == option.id)
      case "player" => value.players.exists(_.playerId == option.id)
      case _ => false

  private def sites(value: GameProjection): Vector[GameSite] =
    value.world.flatMap(_.sites)

  private def siteCards(value: GameProjection): Vector[String] =
    sites(value).flatMap(_.denizens.filter(_.details.nonEmpty).map(_.cardId))

  private def siteRelics(value: GameProjection): Vector[String] =
    sites(value).flatMap(_.relics.knownRelics.map(_.cardId))

  private def shown(cards: Vector[CardDetails]): Vector[String] =
    cards.filterNot(_.hidden).map(_.cardId)

  /** An adviser play's slot: its id without the facing after the last
    * colon. */
  def adviserSlot(id: String): String =
    id.take(math.max(0, id.lastIndexOf(':')))

  /** A slot reference's owner and index: the owner may hold a colon, so the
    * index is what follows the last one.
    */
  private def slot(id: String): Option[(String, Int)] =
    val at = id.lastIndexOf(':')
    Option.when(at > 0)(id.take(at)).flatMap(owner =>
      id.drop(at + 1).toIntOption.filter(_ >= 0).map(owner -> _))

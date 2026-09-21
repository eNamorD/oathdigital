package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerDice, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The boards the Campaign suites share. The actor stands at `origin`, ruled
  * by two Bandits, in the Act phase with Supply and warbands; `extras` further
  * sites are also Bandit-ruled and every other site is empty and unruled; no
  * site holds a denizen. The other player stands elsewhere.
  */
object CampaignFixture {
  final case class Board(ready: ReadyGame, actor: PlayerId, other: PlayerId,
      origin: SiteId) {
    def player(id: PlayerId): PlayerState =
      ready.game.current.players.find(_.player == id).get
    def extras: Vector[SiteId] = ready.game.current.map.inPlay.filter(site =>
      site != origin && ready.game.current.map.sites(site).forces ==
        SiteForces.Occupied(ForceKind.Bandit, 2))
  }

  private val setup = new FirstGameSetupRules(catalog)

  def board(extras: Int = 0, warbands: Int = 5, supply: Int = 7): Board = {
    val Ready(base) = execute(setup)._1: @unchecked
    val current = base.game.current
    val inPlay = current.map.inPlay
    val origin = inPlay.find(id => catalog.sites.find(_.id == id).exists(
      _.handlers.forall(h => !h.endsWith(".mountain") && !h.endsWith(".plains")))).get
    val ruled = (origin +: inPlay.filter(_ != origin).take(extras)).toSet
    val elsewhere = inPlay.find(!ruled(_)).getOrElse(inPlay.find(_ != origin).get)
    val activeId = current.turn.activePlayer
    val otherId = current.players.map(_.player).find(_ != activeId).get
    val players = current.players.map { player =>
      if (player.player == activeId) player.copy(pawnSite = Some(origin),
        board = player.board.copy(warbands = warbands,
          supply = SupplyTrack(supply)))
      else player.copy(pawnSite = Some(elsewhere))
    }
    val sites = current.map.sites.map { case (id, site) =>
      id -> site.copy(denizens = Vector.empty, forces =
        if (ruled(id)) SiteForces.Occupied(ForceKind.Bandit, 2)
        else SiteForces.Empty)
    }
    def bandits(forces: SiteForces): Int = forces match {
      case SiteForces.Occupied(ForceKind.Bandit, count) => count
      case _ => 0
    }
    val delta = current.map.sites.values.map(s => bandits(s.forces)).sum -
      sites.values.map(s => bandits(s.forces)).sum
    val ready = base.updateCurrent(_.copy(players = players,
      map = current.map.copy(sites = sites),
      turn = current.turn.copy(phase = Phase.Act))).copy(banks =
      base.banks.copy(warbandSupply = base.banks.warbandSupply.updated(
        ForceKind.Bandit, base.banks.warbandSupply.getOrElse(ForceKind.Bandit, 0) + delta)))
    Board(ready, activeId, otherId, origin)
  }

  /** The other player joins the actor at `origin`, so a Raid is legal. */
  def withEnemyAtOrigin(b: Board): Board = b.copy(ready = b.ready.updateCurrent(
    current => current.copy(players = current.players.map(p =>
      if (p.player == b.other) p.copy(pawnSite = Some(b.origin)) else p))))

  /** Battle plans are powers, so a Campaign runs with the walker power catalog
    * unless a suite asks for none.
    */
  def rules(dice: WalkerDice = WalkerDice.unavailable,
      powers: Boolean = true): OathRules = new OathRules(catalog,
    walkerPowerCatalog =
      if (powers) WalkerPowerCatalog.default(catalog) else WalkerPowers.empty,
    walkerDice = dice)

  /** Rules with exactly these walker powers, for a suite that tests the plan
    * window with plans of its own.
    */
  def rulesWith(powers: Vector[oathdigital.gameplay.powerresolver.ContributingPower],
      dice: WalkerDice = anyDice): OathRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(powers), walkerDice = dice)

  /** Dice for tests that only walk through the battle: hollow swords and
    * blanks, of whatever count the pool holds.
    */
  val anyDice: WalkerDice = (kind, count) => Right(kind match {
    case DiceKind.Attack => Vector.fill(count)(AttackDieFace.HollowSword: DieFace)
    case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace)
  })

  /** Dice that return exactly these faces, and fail loudly on a wrong count. */
  def dice(attack: Vector[AttackDieFace] = Vector.empty,
      defense: Vector[DefenseDieFace] = Vector.empty): WalkerDice =
    (kind, count) => kind match {
      case DiceKind.Attack => Either.cond(attack.size == count, attack,
        OathViolation.InvalidEventOrder(
          s"test dice: ${attack.size} attack faces for a pool of $count"))
      case DiceKind.Defense => Either.cond(defense.size == count, defense,
        OathViolation.InvalidEventOrder(
          s"test dice: ${defense.size} defense faces for a pool of $count"))
    }

  /** Takes a card out of every zone, so placing it keeps the card index valid. */
  private def scrub(ready: ReadyGame, card: String): ReadyGame =
    ready.updateCurrent(current => current.copy(
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot(_.value == card),
        relicDeck = current.commonCards.relicDeck.filterNot(_.value == card),
        regionalDiscards = current.commonCards.regionalDiscards.map {
          case (region, cards) => region -> cards.filterNot(_.value == card) }),
      players = current.players.map(p => p.copy(
        advisers = p.advisers.filter {
          case held: DenizenState => held.id.value != card
          case _ => true },
        relics = p.relics.filterNot(_.id.value == card))),
      map = current.map.copy(sites = current.map.sites.map { case (id, site) =>
        id -> site.copy(
          denizens = site.denizens.filter {
            case held: DenizenState => held.id.value != card
            case _ => true },
          relics = site.relics.filterNot(_.id.value == card)) })))

  def replacePlayer(b: Board, id: PlayerId)(f: PlayerState => PlayerState)
      : Board = b.copy(ready = b.ready.updateCurrent(current => current.copy(
    players = current.players.map(p => if (p.player == id) f(p) else p))))

  def withAdviserFor(b: Board, player: PlayerId, card: String,
      orientation: Orientation, tokens: Tokens = Tokens.empty): Board =
    replacePlayer(b.copy(ready = scrub(b.ready, card)), player)(p => p.copy(
      advisers = p.advisers :+ DenizenState(DenizenId(card), orientation, tokens)))

  def withAdviser(b: Board, card: String, orientation: Orientation): Board =
    withAdviserFor(b, b.actor, card, orientation)

  def withRelic(b: Board, relic: String): Board =
    replacePlayer(b.copy(ready = scrub(b.ready, relic)), b.actor)(p =>
    p.copy(relics = p.relics :+ RelicState(RelicId(relic), Orientation.FaceUp,
      Tokens.empty)))

  def withSecrets(b: Board, faceUp: Int): Board = replacePlayer(b, b.actor)(p =>
    p.copy(board = p.board.copy(faceUpSecrets = faceUp)))

  /** The origin becomes ruled by the other player, who holds the title. */
  def againstPlayer(b: Board): Board = {
    val lineage = b.player(b.other).lineage
    b.copy(ready = b.ready.updateCurrent(current => current.copy(
      map = current.map.copy(sites = current.map.sites.updated(b.origin,
        current.map.sites(b.origin).copy(forces =
          SiteForces.Occupied(ForceKind.Exile(lineage), 2)))),
      title = current.title.copy(holder = Some(b.other),
        side = TitleSide.Oathkeeper))))
  }

  /** The actor rules `site`, holding it with two warbands of their own. */
  def actorRules(b: Board, site: SiteId): Board = b.copy(ready =
    b.ready.updateCurrent(current => current.copy(map = current.map.copy(
      sites = current.map.sites.updated(site, current.map.sites(site).copy(
        forces = SiteForces.Occupied(ForceKind.Exile(
          b.player(b.actor).lineage), 2)))))))

  def withSiteCard(b: Board, site: SiteId, card: String): Board =
    b.copy(ready = scrub(b.ready, card).updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(site,
        current.map.sites(site).copy(denizens = Vector(DenizenState(
          DenizenId(card), Orientation.FaceUp, Tokens.empty))))))))

  def cardWith(handler: String): String =
    catalog.denizens.find(_.handlers.contains(handler)).get.id.value
  def relicWith(handler: String): String =
    catalog.relics.find(_.handlers.contains(handler)).get.id.value

  /** A Raid board: the other player stands at the origin holding a faceup relic,
    * a facedown relic, three facedown advisers (one a Conspiracy), both banners
    * and 5 favor; the actor has 4 warbands.
    */
  def raidBoard(defenderWarbands: Int = 3): (Board, RelicId) = {
    val b = withEnemyAtOrigin(board(warbands = 4))
    val relic = RelicId(catalog.relics.head.id.value)
    val ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (p.player == b.other) p.copy(
          board = p.board.copy(warbands = defenderWarbands, favor = 5),
          advisers = Vector(
            DenizenState(DenizenId("raid-facedown-denizen"), Orientation.FaceDown,
              Tokens.empty),
            VisionState(VisionId("raid-facedown-vision"), Orientation.FaceDown),
            VisionState(VisionRules.Conspiracy, Orientation.FaceDown)),
          relics = Vector(
            RelicState(relic, Orientation.FaceUp, Tokens.empty),
            RelicState(RelicId("raid-facedown-relic"), Orientation.FaceDown,
              Tokens.empty)))
        else p),
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot(_ == VisionRules.Conspiracy),
        relicDeck = current.commonCards.relicDeck.filterNot(_ == relic)),
      map = current.map.copy(sites = current.map.sites.map { case (id, site) =>
        id -> site.copy(relics = site.relics.filterNot(_.id == relic)) }),
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(
          holder = Some(b.other), favor = 3),
        darkestSecret = current.banners.darkestSecret.copy(
          holder = Some(b.other), secrets = 2))))
    (b.copy(ready = ready), relic)
  }
}

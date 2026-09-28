package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerDice, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.Table

/** The boards the Campaign suites share, built on the quiet `Table`.
  *
  * p1, the actor, stands at Ancient City (the origin), which two Bandits
  * rule, in p1's Act with `warbands` warbands and `supply` Supply. `extras`
  * further sites are Bandit-ruled too, in map order after the origin: Broken
  * Peaks, Buried Giant, Deep Woods, and so on. Every other site is empty.
  * p2, the other player, stands at the first site nobody rules; p3 stays at
  * Buried Giant unless Bandits rule it.
  */
object CampaignFixture:
  final case class Board(ready: ReadyGame, actor: PlayerId, other: PlayerId,
      origin: SiteId):
    def player(id: PlayerId): PlayerState =
      ready.game.current.players.find(_.player == id).get
    def extras: Vector[SiteId] = ready.game.current.map.inPlay.filter(site =>
      site != origin && ready.game.current.map.sites(site).forces ==
        SiteForces.Occupied(ForceKind.Bandit, 2))

  def board(extras: Int = 0, warbands: Int = 5, supply: Int = 7): Board =
    val origin = Table.homeOf(Table.p1)
    val inPlay = Table.start.ready.game.current.map.inPlay
    val ruled = origin +: inPlay.filter(_ != origin).take(extras)
    val elsewhere = inPlay.find(!ruled.contains(_)).get
    val table = ruled.foldLeft(Table.start)(_.bandits(_, 2))
      .pawn(Table.p2, at = elsewhere)
      .warbands(Table.p1, warbands).supply(Table.p1, supply)
    Board(table.ready, Table.p1, Table.p2, origin)

  /** The other player joins the actor at `origin`, so a Raid is legal. */
  def withEnemyAtOrigin(b: Board): Board =
    on(b)(_.pawn(b.other, at = b.origin))

  /** `b` continued by Table steps. */
  def on(b: Board)(steps: Table => Table): Board =
    b.copy(ready = steps(Table.from(b.ready)).ready)

  /** Battle plans are powers, so a Campaign runs with the walker power catalog
    * unless a suite asks for none.
    */
  def rules(dice: WalkerDice = WalkerDice.unavailable,
      powers: Boolean = true): OathRules = new OathRules(catalog,
    walkerPowerCatalog =
      if powers then WalkerPowerCatalog.default(catalog) else WalkerPowers.empty,
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
    (kind, count) => kind match
      case DiceKind.Attack => Either.cond(attack.size == count, attack,
        OathViolation.InvalidEventOrder(
          s"test dice: ${attack.size} attack faces for a pool of $count"))
      case DiceKind.Defense => Either.cond(defense.size == count, defense,
        OathViolation.InvalidEventOrder(
          s"test dice: ${defense.size} defense faces for a pool of $count"))

  def replacePlayer(b: Board, id: PlayerId)(f: PlayerState => PlayerState)
      : Board = b.copy(ready = b.ready.updateCurrent(current => current.copy(
    players = current.players.map(p => if p.player == id then f(p) else p))))

  def withAdviserFor(b: Board, player: PlayerId, card: String,
      orientation: Orientation, tokens: Tokens = Tokens.empty): Board =
    on(b)(_.adviser(player, DenizenId(card),
      facedown = orientation == Orientation.FaceDown)
      .tokens(DenizenId(card), favor = tokens.favor, secrets = tokens.secrets))

  def withAdviser(b: Board, card: String, orientation: Orientation): Board =
    withAdviserFor(b, b.actor, card, orientation)

  def withRelic(b: Board, relic: String): Board =
    withRelicFor(b, b.actor, relic)

  /** `player` holds a faceup relic. */
  def withRelicFor(b: Board, player: PlayerId, relic: String): Board =
    on(b)(_.relic(player, RelicId(relic)))

  /** An edifice stands at `site`, on the given face. */
  def withEdifice(b: Board, site: SiteId, edifice: String, side: EdificeSide)
      : Board = on(b)(_.edifice(EdificeId(edifice), side, at = site))

  def withSecrets(b: Board, faceUp: Int): Board =
    on(b)(_.secrets(b.actor, faceUp = faceUp,
      faceDown = b.player(b.actor).board.faceDownSecrets))

  /** The origin becomes ruled by the other player, who holds the title. */
  def againstPlayer(b: Board): Board =
    on(b)(_.warbandsAt(b.origin, b.other, 2).oathkeeper(Some(b.other)))

  /** The actor rules `site`, holding it with two warbands of their own. */
  def actorRules(b: Board, site: SiteId): Board =
    on(b)(_.warbandsAt(site, b.actor, 2))

  /** `site` holds `card` and nothing else. */
  def withSiteCard(b: Board, site: SiteId, card: String): Board =
    val cleared = b.ready.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(site,
        current.map.sites(site).copy(denizens = Vector.empty)))))
    on(b.copy(ready = cleared))(_.denizen(DenizenId(card), at = site))

  def cardWith(handler: String): String =
    catalog.denizens.find(_.handlers.contains(handler)).get.id.value
  def relicWith(handler: String): String =
    catalog.relics.find(_.handlers.contains(handler)).get.id.value

  /** A Raid board: the other player stands at the origin holding a faceup relic,
    * a facedown relic, three facedown advisers (one a Conspiracy), both banners
    * and 5 favor; the actor has 4 warbands.
    */
  def raidBoard(defenderWarbands: Int = 3): (Board, RelicId) =
    val b = withEnemyAtOrigin(board(warbands = 4))
    // A relic that prints no battle plan, so the defender is offered none.
    val plans = Set("relic.sticky-fire", "relic.fearsome-shield",
      "relic.brass-army.campaign", "relic.bag-of-siegeworks")
    val relic = RelicId(catalog.relics.find(_.handlers.forall(!plans(_))).get
      .id.value)
    // Ids no card carries stand for cards whose identity the Raid never
    // learns: they join the table as they are.
    val raided = on(b)(_
      .warbands(b.other, defenderWarbands).favor(b.other, 5)
      .adviser(b.other, DenizenId("raid-facedown-denizen"), facedown = true)
      .adviser(b.other, VisionId("raid-facedown-vision"), facedown = true)
      .adviser(b.other, VisionRules.Conspiracy, facedown = true)
      .relic(b.other, relic)
      .relic(b.other, RelicId("raid-facedown-relic"), facedown = true)
      .peoplesFavor(Some(b.other), favor = 3)
      .darkestSecret(Some(b.other), secrets = 2))
    (raided, relic)

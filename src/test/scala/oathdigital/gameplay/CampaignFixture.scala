package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerDice, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}

/** The boards the Campaign suites share, built on the quiet `Table`.
  *
  * p1, the actor, stands at Ancient City (the origin), which two Bandits
  * rule, in p1's Act with `warbands` warbands and `supply` Supply. `extras`
  * further sites are Bandit-ruled too, in map order after the origin: Broken
  * Peaks, Buried Giant, Deep Woods, and so on. Every other site is empty.
  * p2, the rival, and p3, the bystander, stand at the first site nobody
  * rules.
  */
object CampaignFixture:
  final case class Board(ready: ReadyGame, actor: PlayerId, rival: PlayerId,
      bystander: PlayerId, origin: SiteId):
    def player(id: PlayerId): PlayerState =
      Look(ready).player(id)
    def extras: Vector[SiteId] = ready.game.current.map.inPlay.filter(site =>
      site != origin && ready.game.current.map.sites(site).forces ==
        SiteForces.Occupied(ForceKind.Bandit, 2))

  def board(extras: Int = 0, warbands: Int, supply: Int)(
      using munit.Location): Board =
    val origin = Table.quietPawnSite(Table.p1)
    val inPlay = Table.start.ready.game.current.map.inPlay
    val ruled = origin +: inPlay.filter(_ != origin).take(extras)
    val elsewhere = inPlay.find(!ruled.contains(_)).get
    val table = ruled.foldLeft(Table.start)(_.bandits(_, 2))
      .pawn(Table.p2, at = elsewhere).pawn(Table.p3, at = elsewhere)
      .boardWarbands(Table.p1, warbands).supply(Table.p1, supply)
    Board(table.ready, Table.p1, Table.p2, Table.p3, origin)

  /** The rival joins the actor at `origin`, so a Raid is legal. */
  def withEnemyAtOrigin(b: Board)(using munit.Location): Board =
    on(b)(_.pawn(b.rival, at = b.origin))

  /** `b` continued by Table steps. Suites build further on a board by hand,
    * so nothing is checked here. */
  def on(b: Board)(steps: Table => Table): Board =
    b.copy(ready = steps(Table.from(b.ready)).unchecked)

  /** Battle plans are powers, so a Campaign runs with the walker power
    * catalog. */
  def rules(dice: WalkerDice = WalkerDice.unavailable): OathRules =
    new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      walkerDice = dice)

  /** Rules with exactly these walker powers, for a suite that tests the plan
    * window with plans of its own.
    */
  def rulesWith(powers: Vector[powerresolver.ContributingPower],
      dice: WalkerDice = WalkerDiceFixture.blanks): OathRules =
    new OathRules(catalog, walkerPowerCatalog = WalkerPowers(powers),
      walkerDice = dice)

  /** Dice that return exactly these faces: the shared stub. */
  export WalkerDiceFixture.dice

  /** `player` holds `card`, by name or id, on `orientation`, with `tokens` on
    * it. */
  def withAdviserFor(b: Board, player: PlayerId, card: String | DenizenId,
      orientation: Orientation, tokens: Tokens = Tokens.empty)(
      using munit.Location): Board =
    val id = CatalogNames.denizen(card)
    on(b)(_.adviser(player, id, facedown = orientation == Orientation.FaceDown)
      .tokens(id, favor = tokens.favor, secrets = tokens.secrets))

  def withAdviser(b: Board, card: String | DenizenId, orientation: Orientation)(
      using munit.Location): Board =
    withAdviserFor(b, b.actor, card, orientation)

  def withRelic(b: Board, relic: String | RelicId)(
      using munit.Location): Board =
    withRelicFor(b, b.actor, relic)

  /** `player` holds a faceup relic, by name or id. */
  def withRelicFor(b: Board, player: PlayerId, relic: String | RelicId)(
      using munit.Location): Board =
    on(b)(_.relic(player, relic))

  /** An edifice stands at `site`, on the given face, by name or id. */
  def withEdifice(b: Board, site: SiteId, edifice: String | EdificeId,
      side: EdificeSide)(using munit.Location): Board =
    on(b)(_.edifice(edifice, side, at = site))

  def withSecrets(b: Board, faceUp: Int): Board =
    on(b)(_.secrets(b.actor, faceUp = faceUp,
      faceDown = b.player(b.actor).board.faceDownSecrets))

  /** The origin becomes ruled by the rival, with 2 warbands, and the rival
    * holds the Oathkeeper title. */
  def rivalRulesOriginAndHoldsOathkeeper(b: Board)(
      using munit.Location): Board =
    on(b)(_.warbandsAt(b.origin, b.rival, 2).oathkeeper(Some(b.rival)))

  /** The actor rules `site`, holding it with two warbands of their own. */
  def actorRules(b: Board, site: SiteId)(using munit.Location): Board =
    on(b)(_.warbandsAt(site, b.actor, 2))

  /** `site` also holds `card`, by name or id. The board's sites start empty. */
  def withSiteCard(b: Board, site: SiteId, card: String | DenizenId)(
      using munit.Location): Board =
    on(b)(_.denizen(card, at = site))

  /** A Raid board and the faceup relic the defender holds. */
  final case class RaidBoard(board: Board, relic: RelicId)

  /** A Raid board: the rival stands at the origin holding Cursed Cauldron
    * faceup, a facedown relic, three facedown advisers (one a Conspiracy),
    * both banners and 5 favor; the actor has 4 warbands and 7 Supply.
    */
  def raidBoard(defenderWarbands: Int = 3)(using munit.Location): RaidBoard =
    val b = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    // Cursed Cauldron prints no battle plan, so the defender is offered none.
    val relic = CatalogNames.relic("Cursed Cauldron")
    val plans = Set("relic.sticky-fire", "relic.fearsome-shield",
      "relic.brass-army.campaign", "relic.bag-of-siegeworks")
    assert(catalog.relics.find(_.id.value == relic.value).get.powers
      .forall(power => !plans(power.id.value)), s"$relic prints a battle plan")
    // Ids no card carries stand for cards whose identity the Raid never
    // learns: they join the table as they are.
    val raided = on(b)(_
      .boardWarbands(b.rival, defenderWarbands).favor(b.rival, 5)
      .adviser(b.rival, DenizenId("raid-facedown-denizen"), facedown = true)
      .adviser(b.rival, VisionId("raid-facedown-vision"), facedown = true)
      .adviser(b.rival, VisionRules.Conspiracy, facedown = true)
      .relic(b.rival, relic)
      .relic(b.rival, RelicId("raid-facedown-relic"), facedown = true)
      .peoplesFavor(Some(b.rival), favor = 3)
      .darkestSecret(Some(b.rival), secrets = 2))
    RaidBoard(raided, relic)

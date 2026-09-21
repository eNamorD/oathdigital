package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** A Travel board for the modifiers and rules of Travel: the map of
  * `TravelProcedureSuite`, with named sites, every site ruled by bandits, and
  * the active player at the first plains.
  *
  * Regions: the cradle holds `plains(0)` and `coast`, the provinces hold
  * `plains(1)`, `mountain` and `pass`, the hinterland holds `plains(2)`,
  * `island` and `plains(3)`. The printed base is 1 within the cradle, 2 from the
  * cradle to the provinces and within them, 4 from the cradle to the
  * hinterland.
  */
object TravelFixture {
  import PowerFixture._

  val rules: OathRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  private def site(power: String): SiteId = catalog.sites.find(
    _.handlers.exists(_.endsWith(s".$power"))).get.id
  val plains: Vector[SiteId] = catalog.sites.filter(
    _.handlers.exists(_.endsWith(".plains"))).map(_.id)
  val coast: SiteId = site("coast")
  val island: SiteId = site("island")
  val mountain: SiteId = site("mountain")
  val pass: SiteId = site("pass")

  /** The Act phase on the board. `source` is the active player's site; the
    * other players stand on the sites after it.
    */
  def board(source: SiteId = plains.head, supply: Int = 7): ReadyGame = {
    val chosen = Vector(source, coast, plains(1), mountain, pass, plains(2),
      island, plains(3))
    val ids = (chosen.distinct ++ catalog.sites.map(_.id)
      .filterNot(chosen.contains)).take(8)
    val states = ids.map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if (definition.capacity == 0) SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    val players = base.game.current.players.zipWithIndex.map {
      case (player, index) => player.copy(
        pawnSite = Some(if (player.player == actor) source else ids(index + 1)),
        board = if (player.player == actor)
          player.board.copy(supply = SupplyTrack(supply)) else player.board)
    }
    inPhase(base.updateCurrent(_.copy(players = players,
      map = MapState(ids.take(2), ids.slice(2, 5), ids.slice(5, 8), states))),
      Phase.Act)
  }

  /** The pass ruled by the actor, so a cross-region route past it is legal. */
  def passRuled(ready: ReadyGame): ReadyGame =
    ruledBy(ready, pass, actor)

  def ruledBy(ready: ReadyGame, site: SiteId, ruler: PlayerId): ReadyGame =
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces =
        SiteForces.Occupied(ForceKind.Exile(player(ready, ruler).lineage), 1))))))

  /** A denizen or an edifice face put at `site`; it leaves the world or
    * edifice deck if it was there, so the inventory stays whole.
    */
  def denizenAt(ready: ReadyGame, id: DenizenId, at: SiteId): ReadyGame =
    atSite(CardStaging.without(ready, id), id, at)

  /** `id` as an adviser of the actor, taken from wherever it was dealt. */
  def adviser(ready: ReadyGame, id: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    asAdviser(CardStaging.without(ready, id), id, orientation)

  def edificeAt(ready: ReadyGame, id: EdificeId, side: EdificeSide,
      at: SiteId): ReadyGame = CardStaging.without(ready, id).updateCurrent(c =>
    c.copy(
    map = c.map.copy(sites = c.map.sites.updated(at, c.map.sites(at).copy(
      denizens = c.map.sites(at).denizens :+
        EdificeState(id, side, Tokens.empty))))))

  def powers(modifiers: Vector[PowerId]): WalkerPowers =
    WalkerPowers.selected(WalkerPowerCatalog.default(catalog), modifiers)

  /** The destinations Travel offers with `modifiers` selected, and their Supply. */
  def candidates(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Map[SiteId, Int] = TravelProcedure.candidates(catalog, ready, actor,
    powers(modifiers)).toMap

  def travel(ready: ReadyGame, destination: SiteId,
      modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.Travel, actor, modifiers,
      Vector(DecisionOptionRef.Site(destination)))

  def after(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  def supplyOf(ready: ReadyGame, id: PlayerId = actor): Int =
    player(ready, id).board.supply.supply

  /** The favor on an adviser card of the actor. */
  def adviserTokens(ready: ReadyGame, id: CardId): Tokens =
    player(ready).advisers.collectFirst {
      case card: DenizenState if card.id == id => card.tokens
    }.get
}

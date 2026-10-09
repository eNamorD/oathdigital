package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.{OathRules, TravelPlay}
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Refusals, Table}
import oathdigital.testkit.PowerRules

/** A Travel board for the modifiers and rules of Travel: the map of
  * `TravelProcedureSuite`, with named sites and the active player at the
  * Cradle's plains.
  *
  * Regions: the Cradle holds `cradlePlains` (Buried Giant) and `coast`
  * (Desolate Shore); the Provinces hold `provincePlains` (Dunes),
  * `mountain` (Broken Peaks) and `pass` (Narrow Pass); the Hinterland holds
  * `hinterlandPlains` (Salt Flats), `island` (Fair Isle) and
  * `secondHinterlandPlains` (Shrouded Woods). The printed base is 1 within
  * the Cradle, 2 from the Cradle to the Provinces and within them, 4 from the
  * Cradle to the Hinterland. Every site with a printed capacity holds a
  * bandit stack of it, and every site its printed starting wealth: Fair
  * Isle 3 favor, Broken Peaks 2 secrets, Shrouded Woods 1 favor and 1
  * secret.
  */
object TravelFixture:
  import PowerFixture._

  val rules: OathRules = PowerRules.withWalkerPowers

  private def site(power: String): SiteId = catalog.sites.find(
    _.handlers.exists(_.value.endsWith(s".$power"))).get.id
  val cradlePlains: SiteId = CatalogNames.site("Buried Giant")
  val provincePlains: SiteId = CatalogNames.site("Dunes")
  val hinterlandPlains: SiteId = CatalogNames.site("Salt Flats")
  val secondHinterlandPlains: SiteId = CatalogNames.site("Shrouded Woods")
  val coast: SiteId = site("coast")
  val island: SiteId = site("island")
  val mountain: SiteId = site("mountain")
  val pass: SiteId = site("pass")

  /** The Act phase on the board. `source` is the active player's site; the
    * other players stand on the sites after it.
    */
  def board(source: SiteId = cradlePlains, supply: Int = 7): ReadyGame =
    val chosen = Vector(source, coast, provincePlains, mountain, pass,
      hinterlandPlains, island, secondHinterlandPlains)
    val ids = (chosen.distinct ++ catalog.sites.map(_.id)
      .filterNot(chosen.contains)).take(8)
    val states = ids.map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if definition.capacity == 0 then SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    val players = quietTable.game.current.players.zipWithIndex.map:
      case (player, index) => player.copy(
        pawnSite = Some(if player.player == actor then source else ids(index + 1)),
        board = if player.player == actor then
          player.board.copy(supply = SupplyTrack(supply)) else player.board)
    // No Table step lays out regions: the board replaces the whole map.
    inPhase(quietTable.updateCurrent(_.copy(players = players,
      map = MapState(ids.take(2), ids.slice(2, 5), ids.slice(5, 8), states))),
      Phase.Act)

  /** `ready` continued by Table steps, read without the card check: the board
    * replaces the whole map, so the cards it lays out are not the ones the
    * check expects the first game to have dealt. */
  def on(ready: ReadyGame)(steps: Table => Table): ReadyGame =
    steps(Table.from(ready)).unchecked

  def powers(modifiers: Vector[PowerId]): WalkerPowers =
    WalkerPowers.selected(WalkerPowerCatalog.default(catalog), modifiers)

  /** The destinations Travel offers with `modifiers` selected, and their Supply. */
  def candidates(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Map[SiteId, Int] =
    TravelProcedure.build(catalog, ready, actor, Vector.empty)
      .flatMap(WalkerSimulation.preview(_, ready, powers(modifiers)))
      .getOrElse(Vector.empty).collect {
        case PreviewedOption(DecisionOption.Site(DecisionOptionRef.Site(site)),
            Right(outcome)) => site -> outcome.operations.collect {
          case SpendSupply(player, amount, _) if player == actor => amount }.sum
      }.toMap

  /** What picking a destination the decision left out is refused with: the
    * look-ahead hides a destination no path completes, so the answer names an
    * option the decision does not offer. */
  val destinationNotOffered: OathViolation =
    Refusals.notOffered(TravelProcedure.destinationDecisionId)

  /** `actor` starts a Travel with `modifiers` and picks `destination`. */
  def travel(ready: ReadyGame, destination: SiteId,
      modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    TravelPlay.travelled(rules, Ready(ready), actor, destination, modifiers)

  /** `from`, parked on a pay-or-ask step, continued by `actor` answering it
    * with `choice`. The events are both transitions'. */
  def answeringStep(from: OathTransition, decisionId: String,
      choice: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(choice))
      .map(done => done.copy(events = from.events ++ done.events))

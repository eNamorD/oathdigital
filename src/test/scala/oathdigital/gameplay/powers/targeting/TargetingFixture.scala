package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.{CampaignFixture, OathRules}
import oathdigital.gameplay.powers.{CardStaging, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Shared staging and reading for the suites of the rules that keep a player
  * from being targeted.
  */
object TargetingFixture {
  val rules: OathRules = CampaignFixture.rules(powers = true)
  val fortress: EdificeId = EdificeId("E28")
  val circlet: RelicId = RelicId("R15")

  def ready(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  def playerOf(state: ReadyGame, id: PlayerId): PlayerState =
    state.game.current.players.find(_.player == id).get

  /** The Fortress on `side` at `site`, taken from wherever it was. */
  def fortressAt(state: ReadyGame, side: EdificeSide, site: SiteId): ReadyGame =
    CardStaging.without(state, fortress).updateCurrent(c => c.copy(map =
      c.map.copy(sites = c.map.sites.updated(site, c.map.sites(site).copy(
        denizens = c.map.sites(site).denizens :+
          EdificeState(fortress, side, Tokens.empty))))))

  /** `site` ruled by `ruler`. */
  def ruledBy(state: ReadyGame, site: SiteId, ruler: PlayerId): ReadyGame =
    state.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces =
        SiteForces.Occupied(ForceKind.Exile(playerOf(state, ruler).lineage),
          2))))))

  def unruled(state: ReadyGame, site: SiteId): ReadyGame =
    state.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces =
        SiteForces.Empty)))))

  def pawnAt(state: ReadyGame, player: PlayerId, site: SiteId): ReadyGame =
    state.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == player) p.copy(pawnSite = Some(site)) else p)))

  /** `player` holds the relic, faceup or facedown. */
  def holds(state: ReadyGame, player: PlayerId, relic: RelicId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    CardStaging.without(state, relic).updateCurrent(c => c.copy(players =
      c.players.map(p => if (p.player == player) p.copy(relics = p.relics :+
        RelicState(relic, orientation, Tokens.empty)) else p)))

  /** `player` holds a faceup adviser of `suit`. */
  def adviserOf(state: ReadyGame, player: PlayerId, suit: Suit): ReadyGame = {
    val card = DenizenId(catalog.denizens.find(_.suit == suit).get.id.value)
    CardStaging.without(state, card).updateCurrent(c => c.copy(players =
      c.players.map(p => if (p.player == player) p.copy(advisers = p.advisers :+
        DenizenState(card, Orientation.FaceUp, Tokens.empty)) else p)))
  }

  /** The options of the decision a parked walk of `procedure` is waiting on. */
  def optionsAt(transition: OathTransition, procedure: ProcedureRef)
      : Vector[DecisionOptionRef] = {
    val state = ready(transition)
    val current = state.game.current
    val tree = WalkerProcedureRegistry.rebuild(procedure, catalog, state,
      current.turn.activePlayer, current.walkerStartArgs).toOption.get
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      current.walkerModifiers)
    ProcedureWalker.parkedDecide(state, tree, current.walkerPending.get, powers)
      .get.query match {
      case one: DecisionQuery.ChooseOne => one.options.map(_.ref)
      case many: DecisionQuery.ChooseMany => many.options.map(_.ref)
      case other => throw new AssertionError(s"unexpected query $other")
    }
  }

  def start(state: ReadyGame, procedure: ActionRef, actor: PlayerId)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(state), procedure, actor)
}

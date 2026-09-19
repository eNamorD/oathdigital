package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The board the Muster and Trade suites share: the active player at a site
  * holding one token-free plain denizen, with a matching-suit denizen available
  * to hold as an adviser. Denizens with an Economy power are excluded so a
  * power cannot change the arithmetic under test.
  */
object EconomyFixture {
  private val setup = new FirstGameSetupRules(catalog)
  private val economic = Set("73", "76", "40", "42", "176", "177", "193",
    "196", "81", "144", "6", "119", "120", "199", "102", "224",
    "229", "231", "238", "241", "248")

  val plain = catalog.denizens.find(d => !economic(d.id.value)).get
  val matching = catalog.denizens.find(d => d.suit == plain.suit &&
    d.id != plain.id && !economic(d.id.value)).get
  val plainId = DenizenId(plain.id.value)
  val matchingId = DenizenId(matching.id.value)
  val springDefinition = catalog.edifices.find(
    _.intact.handlers.contains("edifice.e26.intact")).get
  val springId = EdificeId(springDefinition.id.value)

  def act(tokens: Tokens = Tokens.empty, favor: Int = 4,
      secrets: Int = 2, supply: Int = 7,
      advisers: Vector[AdviserState] = Vector.empty,
      bank: Int = 5, boardWarbands: Int = 3): ReadyGame = {
    val Ready(initial) = execute(setup)._1: @unchecked
    val activeId = initial.game.current.turn.activePlayer
    val active = initial.game.current.players.find(_.player == activeId).get
    val siteId = active.pawnSite.get
    val site = initial.game.current.map.sites(siteId).copy(denizens = Vector(
      DenizenState(plainId, Orientation.FaceUp, tokens)))
    val inserted = advisers.map(_.id).toSet + plainId
    initial.copy(
      banks = initial.banks.copy(favor = initial.banks.favor.updated(
        plain.suit, bank)),
      game = initial.game.copy(current = initial.game.current.copy(
        turn = initial.game.current.turn.copy(phase = Phase.Act),
        commonCards = initial.game.current.commonCards.copy(worldDeck =
          initial.game.current.commonCards.worldDeck.filterNot(inserted)),
        map = initial.game.current.map.copy(sites =
          initial.game.current.map.sites.updated(siteId, site)),
        players = initial.game.current.players.map(p => if (p.player != activeId) p
          else p.copy(board = p.board.copy(favor = favor,
            faceUpSecrets = secrets, supply = SupplyTrack(supply),
            warbands = boardWarbands), advisers = advisers)))))
  }

  def player(ready: ReadyGame): PlayerState = ready.game.current.players.find(
    _.player == ready.game.current.turn.activePlayer).get

  /** The actor's site holds only the Hallowed Spring edifice, on `side`. */
  def spring(ready: ReadyGame, side: EdificeSide): ReadyGame = {
    val actor = player(ready)
    val siteId = actor.pawnSite.get
    val withoutSpring = ready.game.current.map.sites.map {
      case (id, site) => id -> site.copy(
        denizens = site.denizens.filterNot(_.id == springId))
    }
    ready.updateCurrent(_.copy(
      map = ready.game.current.map.copy(sites = withoutSpring.updated(
        siteId, ready.game.current.map.sites(siteId).copy(denizens = Vector(
          EdificeState(springId, side, Tokens.empty))))),
      commonCards = ready.game.current.commonCards.copy(edificeDeck =
        ready.game.current.commonCards.edificeDeck.filterNot(_ == springId))))
  }

  def matchingAdviser: AdviserState =
    DenizenState(matchingId, Orientation.FaceUp, Tokens.empty)

  /** Adds the actor's own adviser to the Muster source decision, which is
    * exactly what Golem Legions will do; only the acceptance rule stops it
    * today.
    */
  final case class AddAdviserSource(id: PowerId) extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterSourceSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case DecisionQuery.ChooseOne(options, heading) =>
              decide.copy(query = DecisionQuery.ChooseOne(options :+
                DecisionOption.Denizen(DecisionOptionRef.Denizen(matchingId)),
                heading)): Operation
            case _ => decide
          }
          case other => other
        })))
  }

  /** Removes the payment from a Muster's cost window, keeping the Supply. */
  final case class FreePayment(id: PowerId) extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterCost -> Vector(Transform((ctx, operations) =>
        operations.map {
          case _: BuildOps => BuildOps((_, _) => Right(Vector[CoreOperation](
            SpendSupply(ctx.activePlayer, 1))))
          case other => other
        })))
  }
}

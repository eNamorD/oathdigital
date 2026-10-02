package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{PowerAnswers, WarbandArrangement}
import oathdigital.model._

object MessengerCard extends Denizen(DenizenId("105"), "Messenger", Suit.Order):
  val power = PrintedPower(PowerId("denizen.messenger"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Move any warbands to and from your board and " +
      "any sites you rule _(except the last warband from a site)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Messenger (card 105), ACTION: place 1 favor on this card, then move any
  * warbands to and from your board and any sites you rule, except the last
  * warband from a site.
  *
  * The player arranges their warbands again over their board and every site
  * they rule, as Warning Signals does ([[WarbandArrangement]]): one exact
  * distribution that keeps the total and leaves each site at least one
  * warband. It is asked only when a warband can move, and an answer that
  * changes nothing is allowed. With nothing to move the cost stays paid and
  * the line says so.
  *
  * The line follows the answer, as Warning Signals' does, and covers
  * nothing: the generic Moved lines stay, since they tell where warbands
  * went.
  */
case object Messenger extends PaidAction(MessengerCard.power):
  val decisionId: String = "power.messenger.arrange"
  /** "{Red} redistributed their warbands." */
  val redistributed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" redistributed their warbands.")))
  /** "No warband could be moved." */
  val stuck: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No warband could be moved.")))
  override def noteKeys: Vector[NoteKey] = Vector(redistributed, stuck)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Branch((live, _) => {
    val (board, sites) = WarbandArrangement.holdings(live, player,
      ruled(live, player))
    if !WarbandArrangement.movable(board, sites) then
      Vector(Note(id, _ => PowerSourceRef.of(source).map(stuck(_))))
    else Vector(
      Decide(decisionId, player, WarbandArrangement.query(player, board, sites,
        "Messenger: arrange your warbands. Your board holds the ones no site " +
          "keeps, and each site keeps at least one")),
      Note(id, _ => PowerSourceRef.of(source).map(card =>
        redistributed(card, NoteArg.Player(player)))),
      BuildOps((state, pending) => PowerAnswers.distribution(pending,
        decisionId).toRight(PowerAnswers.missing(decisionId)).flatMap(rows =>
        WarbandArrangement.moves(state, player, ruled(state, player), rows))))
  }))

  /** The sites the player rules, in map order. */
  private def ruled(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    val sites = PowerAccess.ruledSites(ready, player)
    ready.game.current.map.inPlay.filter(sites.contains)

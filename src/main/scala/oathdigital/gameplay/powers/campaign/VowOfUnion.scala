package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.campaign.{CampaignIds,
  CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers,
  WarbandArrangement}
import oathdigital.model._

object VowOfUnionCard extends Denizen(DenizenId("185"), "Vow of Union", Suit.Beast) with Locked with AdviserOnly:
  val campaign = PrintedPower(PowerId("denizen.vow-of-union.campaign"),
    persistent = true, cost = Cost.free,
    text = "At the start of **any** campaign, you may move any number " +
      "of warbands from sites you rule in your region to your " +
      "board or vice versa.")
  val restriction = PrintedPower(PowerId("denizen.vow-of-union.restriction"),
    persistent = true, cost = Cost.free,
    text = "You may only have [suit-beast] advisers—-bury other " +
      "advisers you have or would gain.")
  val powers: Vector[PrintedPower] = Vector(campaign, restriction)

/** Vow of Union's Campaign half (card 185, adviser-only, locked): "At the
  * start of any campaign, you may move any number of warbands from sites
  * you rule in your region to your board or vice versa."
  *
  * A transform at `CampaignBeforeTargets` puts one `Branch` ahead of the
  * kind, the defender and the targets, in every Campaign. It finds the
  * player who has Vow of Union faceup. That holder may be the attacker, the
  * defender or neither, and the holder answers both questions, which are
  * named under the Campaign's prefix:
  *  - the direction: to the board, to the sites, or "Move none";
  *  - the amounts, one `Distribute` whose bounds keep the direction
  *    ([[WarbandArrangement.oneWay]]).
  *
  * The sites are those the holder rules in the region of their pawn. Nothing
  * is asked without one. "To your sites" with an empty board is shown
  * unavailable. A site may empty. When at least one warband moved, it offers
  * the attacker the Skip (`CampaignIds.skipOffered`); and if no Campaign
  * kind is legal any more, the Campaign ends (`CampaignProcedure`).
  *
  * Its line follows the moves and names the count and the direction. It
  * names the holder only when the holder is not the active player. It covers
  * nothing: the generic move lines say which sites. Both questions are
  * narrated. Its restriction half is
  * [[oathdigital.gameplay.powers.cardplay.VowOfUnionAdvisers]].
  */
case object VowOfUnion extends ContributingPower:
  val cardId: DenizenId = VowOfUnionCard.id
  val id: PowerId = VowOfUnionCard.campaign.id
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val directionDecisionId: String =
    CampaignProcedure.decisionPrefix + "vow-of-union.direction"
  val arrangeDecisionId: String =
    CampaignProcedure.decisionPrefix + "vow-of-union.arrange"
  val toBoard: DecisionOptionRef.Button = DecisionOptionRef.Button("to-board")
  val toSites: DecisionOptionRef.Button = DecisionOptionRef.Button("to-sites")
  val stay: DecisionOptionRef.Button = DecisionOptionRef.Button("stay")
  val noBoardWarbands: String = "No warbands on your board"
  private val notes = NoteSet(id)
  /** "Moved {2 warbands} from sites to the board." */
  val movedToBoard: NoteKey = notes.key("moved.board", Vector(
    NotePart.Text("Moved "), NotePart.Arg(0),
    NotePart.Text(" from sites to the board.")))
  /** "Moved {3 warbands} from the board to sites." */
  val movedToSites: NoteKey = notes.key("moved.sites", Vector(
    NotePart.Text("Moved "), NotePart.Arg(0),
    NotePart.Text(" from the board to sites.")))
  /** "{Blue} moved {2 warbands} from sites to their board." */
  val holderMovedToBoard: NoteKey = notes.key("moved.board.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" moved "), NotePart.Arg(1),
    NotePart.Text(" from sites to their board.")))
  /** "{Blue} moved {3 warbands} from their board to sites." */
  val holderMovedToSites: NoteKey = notes.key("moved.sites.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" moved "), NotePart.Arg(1),
    NotePart.Text(" from their board to sites.")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] =
    Set(directionDecisionId, arrangeDecisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignBeforeTargets -> Vector(Transform((ctx, children) =>
      start(ctx.actor) +: children)))

  private def start(attacker: PlayerId): Operation = Branch((ready, _) =>
    holderOf(ready).toVector.flatMap(direction(ready, attacker, _)))

  private def direction(ready: ReadyGame, attacker: PlayerId,
      holder: PlayerId): Vector[Operation] =
    val (board, sites) = held(ready, holder)
    if sites.isEmpty then Vector.empty
    else Vector(
      Decide(directionDecisionId, holder, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(toBoard, "Move warbands to your board"),
        shown(toSites, "Move warbands to your sites",
          Option.when(board == 0)(noBoardWarbands)),
        DecisionOption.Button(stay, "Move none")), heading = Some(
        "Vow of Union: move warbands between your board and the sites " +
          "you rule in your region"))),
      Branch((live, pending) => arrange(live, attacker, holder, pending)))

  private def shown(ref: DecisionOptionRef.Button, label: String,
      reason: Option[String]): DecisionOption =
    val button = DecisionOption.Button(ref, label)
    reason.fold[DecisionOption](button)(why =>
      DecisionOption.Unavailable(button, OptionReason.Written(why)))

  private def arrange(ready: ReadyGame, attacker: PlayerId,
      holder: PlayerId, pending: PendingTree): Vector[Operation] =
    val way = PowerAnswers.one(pending, directionDecisionId)
    if !way.contains(toBoard) && !way.contains(toSites) then Vector.empty
    else
      val intoSites = way.contains(toSites)
      val (board, sites) = held(ready, holder)
      Vector(
        Decide(arrangeDecisionId, holder, WarbandArrangement.oneWay(holder,
          board, sites, intoSites, heading(intoSites))),
        BuildOps((live, answers) => PowerAnswers.distribution(answers,
          arrangeDecisionId).toRight(PowerAnswers.missing(arrangeDecisionId))
          .flatMap(rows => WarbandArrangement.moves(live, holder,
            region(live, holder), rows)).map(offeringSkip)),
        Note(id, movedNote(_, attacker, holder)))

  /** `moves`, and, when any warband moves, the Skip offered to the
    * attacker. */
  private def offeringSkip(moves: Vector[CoreOperation])
      : Vector[CoreOperation] =
    if moves.isEmpty then moves
    else moves :+ ModifyDicePool(CampaignIds.skipOffered, 1)

  private def heading(intoSites: Boolean): String =
    if intoSites then "Vow of Union: move warbands from your board to the " +
      "sites you rule in your region"
    else "Vow of Union: move warbands from the sites you rule in your " +
      "region to your board"

  /** The player who has Vow of Union faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    PowerAccess.faceupAdviserHolder(ready, cardId)

  /** The sites `holder` rules in the region they campaign from, in map
    * order. */
  private def region(ready: ReadyGame, holder: PlayerId): Vector[SiteId] =
    val map = ready.game.current.map
    val ruled = PowerAccess.ruledSites(ready, holder)
    CampaignSetup.originOf(ready, holder).flatMap(map.regionOf).toVector
      .flatMap(home => map.inPlay.filter(site =>
        ruled.contains(site) && map.regionOf(site).contains(home)))

  /** The holder's board, and each site of their region with what it
    * holds. */
  private def held(ready: ReadyGame, holder: PlayerId)
      : (Int, Vector[(SiteId, Int)]) =
    WarbandArrangement.holdings(ready, holder, region(ready, holder))

  private def boardWarbands(ready: ReadyGame, player: PlayerId): Int =
    NoteSupport.board(ready, player).fold(0)(_.warbands)

  /** The warbands the step before moved, by the change on the holder's
    * board: a gain came from the sites, a loss went to them. */
  private def movedNote(states: NoteStates, attacker: PlayerId,
      holder: PlayerId): Option[LogNote] = for
    step <- states.previous
    change = boardWarbands(step._2, holder) - boardWarbands(step._1, holder)
    if change != 0
  yield
    val card = PowerSourceRef.Card(cardId)
    val count = NoteArg.Amount(math.abs(change), NoteUnit.Warband)
    if holder == attacker then
      (if change > 0 then movedToBoard else movedToSites)(card, count)
    else (if change > 0 then holderMovedToBoard else holderMovedToSites)(
      card, NoteArg.Player(holder), count)

package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PowerAnswers, RuledCards}
import oathdigital.model._

object WizardsConclaveCard extends Denizen(DenizenId("208"), "Wizard's Conclave", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.wizard-s-conclave"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** the player who rules the most " +
      "[suit-arcane] cards takes the Darkest Secret. You decide " +
      "ties. _Don't remove secrets from it._")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wizard's Conclave (card 208), WHEN PLAYED: the player who rules the most
  * Arcane cards takes the Darkest Secret. You decide ties. Don't remove
  * secrets from it.
  *
  * Each player's Arcane cards are counted as [[RuledCards]] counts them;
  * when nobody rules one, every player ties. A tie is the actor's question,
  * among the tied players, themselves included. The winner takes the banner
  * by a `Take`, from its holder or, unheld, from the bank, and its secrets
  * stay on it. When the winner already holds it, nothing moves. The take is
  * not required: when Lost Tongue refuses it, it is skipped and the banner
  * stays where it was.
  *
  * The line names the winner, so the tie question is narrated. No generic
  * line exists for a banner's take.
  */
final case class WizardsConclave private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = WizardsConclaveCard.id
  import WizardsConclave._
  def id: PowerId = WizardsConclave.id

  override def noteKeys: Vector[NoteKey] = Vector(took, kept, none)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)
  private val banner = NoteArg.Banner(Banner.DarkestSecret)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      Branch((live, _) => ask(live, actor)),
      Branch((live, pending) => award(live, pending)))

  /** The players ruling the most Arcane cards, in seat order: every player
    * when nobody rules one. */
  private def leaders(ready: ReadyGame): Vector[PlayerId] =
    val counts = ready.game.current.players.map(seat => seat.player ->
      RuledCards.of(catalog, ready, SiteRuler.Player(seat.player),
        Suit.Arcane).size)
    val most = counts.map(_._2).maxOption.getOrElse(0)
    counts.collect { case (player, count) if count == most => player }

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    leaders(ready) match
      case found if found.size > 1 => Vector(Decide(decisionId, actor,
        DecisionQuery.ChooseOne(found.map(player =>
          DecisionOption.Player(DecisionOptionRef.Player(player))),
          heading = Some("Wizard's Conclave: choose which tied player " +
            "takes the Darkest Secret"))))
      case _ => Vector.empty

  /** The one leader, or the leader the actor chose. */
  private def winner(ready: ReadyGame, pending: PendingTree)
      : Option[PlayerId] = leaders(ready) match
    case Vector(only) => Some(only)
    case found => PowerAnswers.one(pending, decisionId).collect {
      case DecisionOptionRef.Player(player) if found.contains(player) =>
        player }

  /** The take and its line, or the line that the winner kept it. */
  private def award(ready: ReadyGame, pending: PendingTree)
      : Vector[Operation] = winner(ready, pending).toVector.flatMap { taker =>
    val holder = BannerRules.holder(ready.game.current, Banner.DarkestSecret)
    if holder.contains(taker) then Vector(Note(id, _ =>
      Some(kept(cardSource, NoteArg.Player(taker), banner))))
    else Vector(
      BuildOps((_, _) => Right(Vector(Take(Piece.Banner(Banner.DarkestSecret),
        taker, holder.fold[Location](Location.SharedBank)(Location.PlayArea(_)),
        Location.PlayArea(taker))))),
      Note(id, takenNote(_, taker)))
  }

  /** Whether the winner holds the banner now, or Lost Tongue kept it. */
  private def takenNote(states: NoteStates, taker: PlayerId)
      : Option[PowerNote] = Some(
    if BannerRules.holder(states.now.game.current, Banner.DarkestSecret)
        .contains(taker)
    then took(cardSource, NoteArg.Player(taker), banner)
    else none(cardSource, banner))

object WizardsConclave:
  val id: PowerId = WizardsConclaveCard.power.id
  val decisionId: String = "cardplay.wizards-conclave.winner"
  /** "{Blue} took the {Darkest Secret}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Arg(0),
    NotePart.Text(" took the "), NotePart.Arg(1), NotePart.Text(".")))
  /** "{Blue} kept the {Darkest Secret}." */
  val kept: NoteKey = NoteKey("kept", Vector(NotePart.Arg(0),
    NotePart.Text(" kept the "), NotePart.Arg(1), NotePart.Text(".")))
  /** "The {Darkest Secret} did not move." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Text("The "),
    NotePart.Arg(0), NotePart.Text(" did not move.")))

  def forCatalog(catalog: ExecutableCatalog): WizardsConclave =
    new WizardsConclave(catalog)

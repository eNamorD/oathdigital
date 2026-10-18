package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, RuledCards}
import oathdigital.model._

object DissentCard extends Denizen(DenizenId("84"), "Dissent", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.dissent"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** each player, except the holder of the " +
      "People's Favor, places one [favor] on this card for each " +
      "suit of card they rule.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Dissent (card 84), WHEN PLAYED: each player, except the holder of the
  * People's Favor, places one favor on this card for each suit of card they
  * rule.
  *
  * The players are read live, in turn order from the first player, the
  * actor included. Each counts the suits among the cards they rule
  * ([[RuledCards]]) and places that many favor, or all they hold, by a
  * `Give` onto this card wherever it lies. Nobody is asked. The Rest cleanup
  * returns the favor to the Discord bank, as it returns any card's favor to
  * its suit's bank. One line per player who placed; with nobody placing,
  * one line says so.
  *
  * No generic line exists for favor placed on a card, so the lines cover
  * nothing.
  */
final case class Dissent private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = DissentCard.id
  import Dissent._
  def id: PowerId = Dissent.id

  override def noteKeys: Vector[NoteKey] = Dissent.noteKeys

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(Branch((live, _) => placeAll(live)))

  /** What each player places, in turn order: one favor per suit they rule,
    * or all they hold. A player who places nothing is left out. */
  private def placings(ready: ReadyGame): Vector[(PlayerId, Int)] =
    val holder = BannerRules.holder(ready.game.current, Banner.PeoplesFavor)
    FinishRestProcedure.turnOrder(ready).filterNot(holder.contains)
      .flatMap { player =>
        val suits = Suit.all.count(suit => RuledCards.of(catalog, ready,
          SiteRuler.Player(player), suit).nonEmpty)
        val amount = math.min(suits,
          NoteSupport.board(ready, player).fold(0)(_.favor))
        Option.when(amount > 0)(player -> amount)
      }

  private def placeAll(ready: ReadyGame): Vector[Operation] =
    placings(ready) match
      case Vector() => Vector(Note(id, _ => Some(none(cardSource))))
      case found => found.flatMap { (player, amount) => Vector(
        BuildOps((_, _) => Right(Vector(Give(Piece.Favor(amount), player,
          Location.PlayArea(player), Location.OnCard(cardId))))),
        Note(id, placedNote(_, player))) }

  /** The favor `player` lost in the step before. */
  private def placedNote(states: NoteStates, player: PlayerId)
      : Option[LogNote] = for
    step <- states.previous
    lost = -NoteSupport.favor(step, player)
    if lost > 0
  yield placed(cardSource, NoteArg.Player(player),
    NoteArg.Amount(lost, NoteUnit.Favor))

object Dissent:
  val id: PowerId = DissentCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "{Blue} placed {n favor} on it." */
  val placed: NoteKey = notes.key("placed", Vector(NotePart.Arg(0),
    NotePart.Text(" placed "), NotePart.Arg(1), NotePart.Text(" on it.")))
  /** "Nobody placed favor." */
  val none: NoteKey = notes.key("none", Vector(
    NotePart.Text("Nobody placed favor.")))

  def forCatalog(catalog: ExecutableCatalog): Dissent = new Dissent(catalog)

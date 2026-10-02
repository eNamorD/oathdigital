package oathdigital.gameplay.powers.wake

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PowerAnswers}
import oathdigital.model._

object HungerCard extends Denizen(DenizenId("216"), "Hunger", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.hunger"),
    persistent = false, cost = Cost.free,
    text = "**WAKE:** You **must** bury an adviser held by a player " +
      "whose pawn is at your site _(even yourself)_, but cannot " +
      "bury this card.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Hunger (card 216, adviser-only, locked), WAKE: you must bury an adviser
  * held by a player whose pawn is at your site, even yourself, but cannot
  * bury this card.
  *
  * A forced power (catalog batch 2, N8): the forced Wake step runs it at the
  * start of its holder's Wake. The candidates are every adviser, denizen or
  * Vision, in either orientation, of every player at the holder's site, the
  * holder included and Hunger excluded, in seat order. They are offered as
  * adviser slots, so another player's facedown adviser is not disclosed.
  * `Bury` ignores locked, so a locked adviser is a candidate. The question
  * is asked whenever there is one. The burial uses the standard returns:
  * favor to the card's suit bank, secrets to Hunger's holder facedown. With
  * no candidate, nothing is buried and the line says so.
  *
  * Its line covers the generic "Buried" line.
  */
final case class Hunger private (cardId: DenizenId, catalog: ExecutableCatalog)
    extends PhasePower:
  import Hunger._
  def id: PowerId = Hunger.id
  def timing: PowerTiming = PowerTiming.Wake
  override def forced: Boolean = true

  override def noteKeys: Vector[NoteKey] = Vector(buried, spared)

  def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Boolean = true

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending)),
    Note(id, buriedNote(_, player), covers = true))))

  private def candidates(ready: ReadyGame, holder: PlayerId): Vector[Candidate] =
    val site = PowerAccess.pawnSite(ready, holder)
    for
      owner <- ready.game.current.players
      if site.nonEmpty && owner.pawnSite == site
      (held, slot) <- owner.advisers.zipWithIndex
      if held.id != cardId
    yield held match
      case d: DenizenState => Candidate(owner.player, slot,
        BuryableCard.Denizen(d.id), d.tokens)
      case v: VisionState => Candidate(owner.player, slot,
        BuryableCard.Vision(v.id), Tokens.empty)

  private def ask(ready: ReadyGame, holder: PlayerId): Vector[Operation] =
    val found = candidates(ready, holder)
    if found.isEmpty then Vector.empty
    else Vector(Decide(decisionId, holder, DecisionQuery.ChooseOne(
      found.map(c => DecisionOption.AdviserSlot(c.ref)),
      heading = Some("Hunger: bury an adviser at your site"))))

  private def bury(ready: ReadyGame, holder: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = candidates(ready, holder)
    if found.isEmpty then Right(Vector.empty)
    else for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      chosen <- found.find(_.ref == ref).toRight(OathViolation
        .InvalidEventOrder(s"${ref.wireId} is not an adviser Hunger can bury"))
      suit = catalog.suitOf(chosen.card.id)
      _ <- Either.cond(chosen.tokens.favor == 0 || suit.isDefined, (),
        OathViolation.InvalidEventOrder(
          s"no suit is known for ${chosen.card.id.value}"))
    yield Bury.standard(chosen.card,
      PositionedLocation(Location.PlayArea(chosen.owner)), suit,
      chosen.tokens.favor, chosen.tokens.secrets, holder)

  /** The chosen adviser, read where it stood before the bury step. No
    * answer means nothing could be buried. */
  private def buriedNote(states: NoteStates, holder: PlayerId)
      : Option[PowerNote] =
    val card = PowerSourceRef.Card(cardId)
    NoteSupport.answer(states, decisionId) match
      case None => Some(spared(card))
      case Some(ref) =>
        states.previous.flatMap((before, _) =>
          candidates(before, holder).find(_.ref == ref)).map(chosen =>
          buried(card, NoteArg.Player(holder), NoteArg.Card(chosen.card.id),
            NoteArg.Player(chosen.owner)))

object Hunger:
  val id: PowerId = PowerId("denizen.hunger")
  val decisionId: String = "power.hunger.adviser"
  /** "{Red} buried {card} from {Blue}'s advisers." The card reads as its
    * back to anyone who may not identify it, so the owner is named apart
    * from it, as Ivory Eye's line does. */
  val buried: NoteKey = NoteKey("buried", Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s advisers.")))
  /** "No adviser could be buried." */
  val spared: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No adviser could be buried.")))

  private final case class Candidate(owner: PlayerId, slot: Int,
      card: BuryableCard, tokens: Tokens):
    def ref: DecisionOptionRef.AdviserSlot =
      DecisionOptionRef.AdviserSlot(owner, slot)

  def forCatalog(catalog: ExecutableCatalog): Option[Hunger] =
    CatalogCards.denizen(catalog, id).map(new Hunger(_, catalog))

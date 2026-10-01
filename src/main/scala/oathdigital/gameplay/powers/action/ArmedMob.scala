package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.{BannerRules, CardPlay}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts,
  PowerAnswers}
import oathdigital.model._

/** Armed Mob (card 53, site-only), ACTION: place 1 favor on this card, then
  * discard a faceup adviser from a player who holds the Darkest Secret but
  * not the People's Favor.
  *
  * The target is the Darkest Secret's holder when that player does not also
  * hold the People's Favor; it may be the player. The candidates are the
  * target's faceup denizen advisers, in adviser order. The chosen card gets
  * the standard discard: to the discard pile of the region after the
  * player's, its favor to its suit's bank and its secrets to the player
  * facedown. The discard is a required batch, so the Locked restriction
  * hides a locked adviser, and the question passes when nothing is left.
  * With no target, or nothing to discard, the cost stays paid and one line
  * says so.
  *
  * Its line covers the generic Discarded line.
  */
final case class ArmedMob private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.armed-mob", Cost(favor = 1)):
  import ArmedMob._

  override def noteKeys: Vector[NoteKey] = Vector(discarded, nobody, bare)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => discard(live, player, pending),
      required = true),
    Note(this.id, discardedNote(_, player, source), covers = true))))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(card => DecisionOption.Denizen(
          DecisionOptionRef.Denizen(card.id))),
        heading = Some("Armed Mob: discard a faceup adviser of the Darkest " +
          "Secret's holder")), passWhenEmpty = true))

  /** No answer means there was no target or nothing it could lose. */
  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => discardOf(ready, actor, ref)

  private def discardOf(ready: ReadyGame, actor: PlayerId,
      ref: DecisionOptionRef): Either[OathViolation, Vector[CoreOperation]] =
    for
      owner <- target(ready).toRight(OathViolation.InvalidEventOrder(
        "no player is an Armed Mob target"))
      chosen <- candidates(ready)
        .find(card => DecisionOptionRef.Denizen(card.id) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not an adviser Armed Mob can discard"))
      suit <- catalog.suitOf(chosen.id)
        .toRight(OathViolation.UnknownWorldCard(chosen.id))
      region <- PowerAccess.pawnSite(ready, actor)
        .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
        .toRight(OathViolation.PawnSiteMissing(actor))
    yield Vector[CoreOperation](Discard.Denizen(chosen.id,
      PositionedLocation(Location.PlayArea(owner)), region, suit,
      chosen.tokens.favor, chosen.tokens.secrets, actor, required = true))

  /** The card discarded and its owner; with no answer, whether there was a
    * target. */
  private def discardedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Denizen(chosen)) =>
          discardedLine(states, actor, card, chosen)
        case _ => Some(target(states.now).fold(nobody(card))(owner =>
          bare(card, NoteArg.Player(owner)))))

  private def discardedLine(states: NoteStates, actor: PlayerId,
      card: PowerSourceRef, chosen: DenizenId): Option[PowerNote] = for
    step <- states.previous
    owner <- target(step._1)
  yield discarded(card, NoteArg.Player(actor), NoteArg.Card(chosen),
    NoteArg.Player(owner))

object ArmedMob:
  val id: PowerId = PowerId("denizen.armed-mob")
  val decisionId: String = "power.armed-mob.adviser"
  /** "{Red} discarded {card} from {Blue}'s advisers." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s advisers.")))
  /** "No player held the Darkest Secret without the People's Favor." */
  val nobody: NoteKey = NoteKey("used.none", Vector(NotePart.Text(
    "No player held the Darkest Secret without the People's Favor.")))
  /** "{Blue} had no faceup adviser to discard." */
  val bare: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" had no faceup adviser to discard.")))

  /** The Darkest Secret's holder, unless they also hold the People's Favor. */
  def target(ready: ReadyGame): Option[PlayerId] =
    val current = ready.game.current
    BannerRules.holder(current, Banner.DarkestSecret).filterNot(holder =>
      BannerRules.holder(current, Banner.PeoplesFavor).contains(holder))

  /** The target's faceup denizen advisers, in adviser order. */
  private def candidates(ready: ReadyGame): Vector[DenizenState] =
    target(ready).toVector.flatMap(owner => PlayerFacts.player(ready, owner)
      .toOption.toVector.flatMap(_.advisers.collect {
        case card @ DenizenState(_, Orientation.FaceUp, _) => card }))

  def forCatalog(catalog: ExecutableCatalog): Option[ArmedMob] =
    CatalogCards.denizen(catalog, id).map(_ => new ArmedMob(catalog))

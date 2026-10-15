package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.{EffectiveSuit, PowerAccess}
import oathdigital.gameplay.actions.{BannerRules, CardPlay}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.gameplay.powers.banner.CardPowerBanners
import oathdigital.model._

object ArmedMobCard extends Denizen(DenizenId("53"), "Armed Mob", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.armed-mob"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Discard a faceup adviser from a player who " +
      "holds the Darkest Secret but not the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Armed Mob (card 53, site-only), ACTION: place 1 favor on this card, then
  * discard a faceup adviser from a player who holds the Darkest Secret but
  * not the People's Favor.
  *
  * The targets are the players who hold the Darkest Secret but not the
  * People's Favor, in seat order. The player holds it as their card powers
  * see it (Yew Staff, `CardPowerBanners`); every other player only by
  * holding the banner. So the player may be a target, and with Yew Staff
  * one beside the banner's holder.
  *
  * With two targets the player first chooses whose adviser to discard. A
  * target with no faceup adviser is shown unavailable, and with neither
  * having one nothing is asked. The candidates are the target's faceup
  * denizen advisers, in adviser order. The chosen card gets the standard
  * discard: to the discard pile of the region after the player's, its
  * favor to its suit's bank and its secrets to the player facedown. The
  * discard is a required batch, so the Locked restriction hides a locked
  * adviser, and the question passes when nothing is left. With no target,
  * or nothing to discard, the cost stays paid and one line says so.
  *
  * Its line covers the generic Discarded line.
  */
final case class ArmedMob private (catalog: ExecutableCatalog)
    extends PaidAction(ArmedMobCard.power):
  import ArmedMob._

  override def noteKeys: Vector[NoteKey] = ArmedMob.noteKeys
  override def narratedDecisions: Set[String] =
    Set(playerDecisionId, decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askPlayer(live, player)),
    Branch((live, pending) => askAdviser(live, player, pending)),
    BuildOps((live, pending) => discard(live, player, pending),
      required = true),
    Note(this.id, discardedNote(_, player, source), covers = true))))

  /** Whose adviser to discard, while two players are targets and one of
    * them has a faceup adviser. A target with none is shown unavailable. */
  private def askPlayer(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] =
    val found = targets(ready, actor)
    if found.size < 2 || found.forall(candidates(ready, _).isEmpty) then
      Vector.empty
    else Vector(Decide(playerDecisionId, actor, DecisionQuery.ChooseOne(
      found.map { owner =>
        val option = DecisionOption.Player(DecisionOptionRef.Player(owner))
        if candidates(ready, owner).nonEmpty then option
        else DecisionOption.Unavailable(option,
          OptionReason.Written(noAdviser))
      },
      heading = Some(playerHeading))))

  private def askAdviser(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    owner(ready, actor, pending).toVector.flatMap(target =>
      candidates(ready, target) match
        case Vector() => Vector.empty
        case found => Vector(Decide(decisionId, actor,
          DecisionQuery.ChooseOne(found.map(card => DecisionOption.Denizen(
            DecisionOptionRef.Denizen(card.id))),
            heading = Some(adviserHeading(target == actor))),
          passWhenEmpty = true)))

  /** The target the discard comes from: the player chosen, else the only
    * target. */
  private def owner(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[PlayerId] =
    PowerAnswers.one(pending, playerDecisionId) match
      case Some(DecisionOptionRef.Player(chosen)) => Some(chosen)
      case _ => targets(ready, actor) match
        case Vector(only) => Some(only)
        case _ => None

  /** No answer means there was no target or nothing it could lose. */
  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => discardOf(ready, actor, pending, ref)

  private def discardOf(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree, ref: DecisionOptionRef)
      : Either[OathViolation, Vector[CoreOperation]] =
    for
      target <- owner(ready, actor, pending).toRight(
        OathViolation.InvalidEventOrder("no player is an Armed Mob target"))
      chosen <- candidates(ready, target)
        .find(card => DecisionOptionRef.Denizen(card.id) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not an adviser Armed Mob can discard"))
      suit <- EffectiveSuit.of(catalog, ready, chosen.id)
        .toRight(OathViolation.UnknownWorldCard(chosen.id))
      region <- PowerAccess.pawnSite(ready, actor)
        .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
        .toRight(OathViolation.PawnSiteMissing(actor))
    yield Vector[CoreOperation](Discard.Denizen(chosen.id,
      PositionedLocation(Location.PlayArea(target)), region, suit,
      chosen.tokens.favor, chosen.tokens.secrets, actor, required = true))

  /** The card discarded and its owner; with no card chosen, why. */
  private def discardedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Denizen(chosen)) =>
          discardedLine(states, actor, card, chosen)
        case _ => Some(unansweredLine(states, actor, card)))

  /** Why nothing was discarded: no target, a target with nothing to lose,
    * or two such targets. */
  private def unansweredLine(states: NoteStates, actor: PlayerId,
      card: PowerSourceRef): PowerNote =
    NoteSupport.answer(states, playerDecisionId) match
      case Some(DecisionOptionRef.Player(chosen)) =>
        bare(card, NoteArg.Player(chosen))
      case _ => targets(states.now, actor) match
        case Vector() => nobody(card)
        case Vector(only) => bare(card, NoteArg.Player(only))
        case _ => neither(card)

  /** The owner is whoever held the card before the discard. */
  private def discardedLine(states: NoteStates, actor: PlayerId,
      card: PowerSourceRef, chosen: DenizenId): Option[PowerNote] = for
    step <- states.previous
    owner <- step._1.game.current.players
      .find(_.advisers.exists(_.id == chosen)).map(_.player)
  yield discarded(card, NoteArg.Player(actor), NoteArg.Card(chosen),
    NoteArg.Player(owner))

object ArmedMob:
  val id: PowerId = ArmedMobCard.power.id
  val decisionId: String = "power.armed-mob.adviser"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  val playerDecisionId: String = "power.armed-mob.player"
  val playerHeading: String =
    "Armed Mob: discard a faceup adviser of which player?"
  val noAdviser: String = "No faceup adviser to discard"

  /** The adviser question's heading, for the player's own advisers or the
    * banner holder's. */
  def adviserHeading(own: Boolean): String =
    if own then "Armed Mob: discard one of your faceup advisers"
    else "Armed Mob: discard a faceup adviser of the Darkest Secret's holder"

  /** "{Red} discarded {card} from {Blue}'s advisers." */
  val discarded: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s advisers.")))
  /** "No player held the Darkest Secret without the People's Favor." */
  val nobody: NoteKey = notes.key("used.none", Vector(NotePart.Text(
    "No player held the Darkest Secret without the People's Favor.")))
  /** "{Blue} had no faceup adviser to discard." */
  val bare: NoteKey = notes.key("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" had no faceup adviser to discard.")))
  /** "Neither target had a faceup adviser to discard." */
  val neither: NoteKey = notes.key("used.empty-both", Vector(NotePart.Text(
    "Neither target had a faceup adviser to discard.")))

  /** The players who hold the Darkest Secret but not the People's Favor,
    * in seat order. The actor holds it as their card powers see it; every
    * other player only by holding the banner. */
  def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    val current = ready.game.current
    current.players.map(_.player).filter(player =>
      holds(ready, actor, player) &&
        !BannerRules.holder(current, Banner.PeoplesFavor).contains(player))

  private def holds(ready: ReadyGame, actor: PlayerId, player: PlayerId)
      : Boolean =
    if player == actor then CardPowerBanners.holdsDarkestSecret(ready, actor)
    else BannerRules.holder(ready.game.current, Banner.DarkestSecret)
      .contains(player)

  /** `owner`'s faceup denizen advisers, in adviser order. */
  private def candidates(ready: ReadyGame, owner: PlayerId)
      : Vector[DenizenState] =
    PlayerFacts.player(ready, owner).toOption.toVector.flatMap(
      _.advisers.collect {
        case card @ DenizenState(_, Orientation.FaceUp, _) => card })

  def forCatalog(catalog: ExecutableCatalog): ArmedMob =
    new ArmedMob(catalog)

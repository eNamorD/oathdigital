package oathdigital.gameplay.powers.wake

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.DiscardRestrictions
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.{AdviserLimit, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Horned Mask (relic R06), WAKE: take a non-edifice denizen from your pawn's
  * site as a facedown adviser. The engine records the once-per-turn use.
  *
  * The tree is two live `Branch`es (which denizen, then which adviser to
  * discard when the area is full) and one `BuildOps`. Each reads live state,
  * so the tree is the same whether it is derived at the start or on a resume.
  * The effect is: discard the chosen adviser when the area is full, return the
  * taken card's favor and secrets, `Take` the card into the play area and
  * `Flip` it facedown.
  *
  * The discard follows card play: a denizen or Vision goes to the next
  * region's discard pile, and a `LockedAdviserOnly` card cannot be discarded.
  * When the area is full and nothing is discardable, nothing can be taken.
  * "Full" is the player's adviser limit, [[AdviserLimit.of]]: 3, or 2 for a
  * Silver Tongue holder.
  */
final case class HornedMask(catalog: ExecutableCatalog) extends PhasePower {
  import HornedMask._

  def id: PowerId = HornedMask.id
  def timing: PowerTiming = PowerTiming.Wake
  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = true

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askDenizen(live, player)),
    Branch((live, pending) => askDiscard(live, player, pending)),
    BuildOps((live, pending) => take(live, player, pending),
      restrictions = (_, _) => Vector(
        new DiscardRestrictions(catalog, player))))))

  private def site(ready: ReadyGame, actor: PlayerId)
      : Option[(SiteId, SiteState)] = PowerAccess.pawnSite(ready, actor)
    .flatMap(id => ready.game.current.map.sites.get(id).map(id -> _))

  private def advisers(ready: ReadyGame, actor: PlayerId): Vector[AdviserState] =
    PlayerFacts.player(ready, actor).map(_.advisers).getOrElse(Vector.empty)

  private def full(ready: ReadyGame, actor: PlayerId): Boolean =
    advisers(ready, actor).size >= AdviserLimit.of(catalog, ready, actor)

  private def lockedAdviser(id: DenizenId): Boolean = catalog.denizens
    .find(_.id.value == id.value)
    .exists(_.restrictions == CardRestrictions.LockedAdviserOnly)

  private def discardable(ready: ReadyGame, actor: PlayerId)
      : Vector[AdviserState] = advisers(ready, actor).filter {
    case d: DenizenState => !lockedAdviser(d.id)
    case _ => true
  }

  private def takeable(ready: ReadyGame, actor: PlayerId): Vector[DenizenState] =
    if (full(ready, actor) && discardable(ready, actor).isEmpty) Vector.empty
    else site(ready, actor).toVector.flatMap(_._2.denizens.collect {
      case d: DenizenState => d })

  private def ref(adviser: AdviserState): DecisionOptionRef = adviser match {
    case d: DenizenState => DecisionOptionRef.Denizen(d.id)
    case v: VisionState => DecisionOptionRef.Vision(v.id)
  }

  private def askDenizen(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val found = takeable(ready, actor)
    if (found.isEmpty) Vector.empty
    else Vector(Decide(denizenDecisionId, actor, DecisionQuery.ChooseOne(
      found.map(d => DecisionOption.Denizen(DecisionOptionRef.Denizen(d.id))),
      heading = Some("Horned Mask: take a denizen as a facedown adviser"))))
  }

  private def askDiscard(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    if (!full(ready, actor) ||
        PowerAnswers.one(pending, denizenDecisionId).isEmpty) Vector.empty
    else Vector(Decide(discardDecisionId, actor, DecisionQuery.ChooseOne(
      discardable(ready, actor).flatMap(a => DecisionOption.forRef(ref(a))),
      heading = Some("Horned Mask: choose an adviser to discard"))))

  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val found = takeable(ready, actor)
    if (found.isEmpty) Right(Vector.empty)
    else for {
      answered <- PowerAnswers.one(pending, denizenDecisionId)
        .toRight(PowerAnswers.missing(denizenDecisionId))
      card <- found.find(d => DecisionOptionRef.Denizen(d.id) == answered)
        .toRight(OathViolation.InvalidEventOrder(
          s"${answered.wireId} is not a denizen Horned Mask can take"))
      siteId <- site(ready, actor).map(_._1)
        .toRight(OathViolation.PawnSiteMissing(actor))
      discards <-
        if (full(ready, actor)) discard(ready, actor, pending)
        else Right(Vector.empty[CoreOperation])
      returns <- returnsOf(card, siteId, actor)
    } yield discards ++ returns ++ Vector[CoreOperation](
      Take(Piece.Card(card.id), actor, Location.Site(siteId),
        Location.PlayArea(actor)),
      Flip(card.id, Location.PlayArea(actor), Orientation.FaceDown))
  }

  /** The favor and secrets of the taken card, returned as a discard returns
    * them: favor to the card's suit bank, secrets to the actor facedown. It is
    * the returns half of `Bury.standard`.
    */
  private def returnsOf(card: DenizenState, site: SiteId, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val suit = catalog.suitOf(card.id)
    if (card.tokens.favor > 0 && suit.isEmpty) Left(OathViolation
      .InvalidEventOrder(s"no suit is known for ${card.id.value}"))
    else Right(Bury.standard(BuryableCard.Denizen(card.id),
      PositionedLocation(Location.Site(site)), suit, card.tokens.favor,
      card.tokens.secrets, actor).filterNot(_.isInstanceOf[Bury]))
  }

  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    answered <- PowerAnswers.one(pending, discardDecisionId)
      .toRight(PowerAnswers.missing(discardDecisionId))
    chosen <- discardable(ready, actor).find(ref(_) == answered)
      .toRight(OathViolation.InvalidEventOrder(
        s"${answered.wireId} is not an adviser Horned Mask can discard"))
    region <- PowerAccess.pawnSite(ready, actor)
      .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
      .toRight(OathViolation.PawnSiteMissing(actor))
    from = PositionedLocation(Location.PlayArea(actor))
    operation <- chosen match {
      case d: DenizenState => catalog.suitOf(d.id)
        .toRight(OathViolation.UnknownWorldCard(d.id)).map(suit =>
          Discard.Denizen(d.id, from, region, suit, d.tokens.favor,
            d.tokens.secrets, actor, required = true))
      case v: VisionState =>
        Right(Discard.Vision(v.id, from, region, required = true))
    }
  } yield Vector[CoreOperation](operation)
}

object HornedMask {
  val id: PowerId = PowerId("relic.horned-mask")
  val denizenDecisionId: String = "power.horned-mask.denizen"
  val discardDecisionId: String = "power.horned-mask.discard"
}

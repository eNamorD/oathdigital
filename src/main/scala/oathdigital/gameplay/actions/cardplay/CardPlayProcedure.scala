package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.actions.{CardPlay, CardPlayRules}
import oathdigital.model._

/** The embeddable card play (card play slots design, "Placement procedure"):
  *
  *  1. `cardplay.place.{kind}.{value}`, a choose-one of the card's slots
  *     (`CardSlots`).
  *  2. When the answer was a Replace site slot and site discard permissions
  *     remain, `cardplay.discard.{kind}.{value}`: a choose-many of the other
  *     site cards, from none up to the permissions left, the replaced card
  *     locked.
  *  3. The line of each permission a site discard spent, the play's
  *     operations, then the `CardPlayedFacedown` or `CardPlayedFaceup` hook.
  *
  * The headings name neither the card nor the site; the projector does.
  */
object CardPlayProcedure:
  enum Origin { case TemporaryHand, FacedownAdviser }

  /** How a placement answer plays the card, for the readers that name a play
    * by what it does rather than by its option: the Game Log, and tests. A
    * Vision played faceup and a played Conspiracy are `Faceup`.
    */
  enum PlayedAs:
    case Discarded, AtSite, Faceup, Facedown

  /** How the placement option `ref` plays the card; `None` for a ref no
    * placement decision offers. */
  def playedAs(ref: DecisionOptionRef): Option[PlayedAs] = ref match
    case _: DecisionOptionRef.DiscardPile => Some(PlayedAs.Discarded)
    case _: DecisionOptionRef.SiteSlot => Some(PlayedAs.AtSite)
    case DecisionOptionRef.AdviserPlay(_, _, faceup) =>
      Some(if faceup then PlayedAs.Faceup else PlayedAs.Facedown)
    case _: DecisionOptionRef.RevealedVision | CardSlots.play =>
      Some(PlayedAs.Faceup)
    case _ => None

  def buildFacedown(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
    card <- facedownCard(args)
    tree <- build(catalog, ready, actor, card, Origin.FacedownAdviser)
  yield tree

  def rebuildFacedown(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] =
    facedownCard(args).map(unchecked(catalog, ready, actor, _,
      Origin.FacedownAdviser))

  private def facedownCard(args: Vector[DecisionOptionRef])
      : Either[OathViolation, WorldCardId] = args match
    case Vector(DecisionOptionRef.Denizen(id)) => Right(id)
    case Vector(DecisionOptionRef.Vision(id)) => Right(id)
    case _ => Left(OathViolation.InvalidEventOrder(
      "facedown-adviser play requires exactly one held card"))

  /** The placement subtree planned under one set of [[CardPlayRules]]. */
  final class PlacementBody private[cardplay](val rules: CardPlayRules,
      childrenAt: CardPlayRules => Vector[Operation]) extends Operation:
    override val children: Vector[Operation] = childrenAt(rules)
    private[cardplay] def adjust(change: CardPlayRules => CardPlayRules)
        : PlacementBody = new PlacementBody(change(rules), childrenAt)

  /** Generic rules-aware placement seam. A power changes the rules its play is
    * planned under without placing its identity in the card-play code.
    *
    * `children` is the play under the default rules. A `Transform` at
    * `SearchPlayAdviser` calls [[adjust]] with the children it was handed and
    * the change it wants. Every contributor's change is applied to the same
    * rules, so contributors compose in any order, and the result is the play
    * planned under all of them. A node a contributor appends after the body
    * is kept by the later ones.
    */
  final class PlacementTree private[cardplay](val card: WorldCardId,
      childrenAt: CardPlayRules => Vector[Operation])
      extends Operation:
    override val window: Option[PowerWindow] =
      Some(PowerWindow.SearchPlayAdviser)
    override val children: Vector[Operation] =
      childrenAt(CardPlayRules.default)

    def adjust(current: Vector[Operation])(
        change: CardPlayRules => CardPlayRules): Vector[Operation] =
      // An earlier contributor may have appended nodes after the body:
      // change the body in place and keep them.
      if current.exists(_.isInstanceOf[PlacementBody]) then current.map {
        case body: PlacementBody => body.adjust(change)
        case other => other
      }
      else Vector(new PlacementBody(change(CardPlayRules.default), childrenAt))

  /** Whether `card` is still at the origin the play started from. Once it is
    * not, the play has been made and the tree is settled from the answers.
    */
  private def heldAtOrigin(ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Boolean =
    val player = ready.game.current.players.find(_.player == actor)
    origin match
      case Origin.TemporaryHand =>
        ready.game.current.temporaryHands.getOrElse(actor, Vector.empty).contains(card)
      case Origin.FacedownAdviser => player.exists(_.advisers.exists {
        case DenizenState(id, Orientation.FaceDown, _) => id == card
        case VisionState(id, Orientation.FaceDown) => id == card
        case _ => false
      })

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Either[OathViolation, Operation] =
    if !heldAtOrigin(ready, actor, card, origin) then Left(
      OathViolation.InvalidSearchPlacement(
        "card is not held at the selected origin"))
    else Right(unchecked(catalog, ready, actor, card, origin))

  /** The tree with no check that the card is at its origin: a walker that
    * resumes rebuilds the tree from the state after the play, when it is not.
    */
  def unchecked(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Operation =
    new PlacementTree(card, rules =>
      childrenFor(catalog, ready, actor, card, origin, rules))

  private val placementPrefix = "cardplay.place."
  private val discardPrefix = "cardplay.discard."

  /** `card`'s placement decision id, `cardplay.place.{kind}.{value}` -- the
    * one place that spells it, so every reader agrees with `childrenFor`.
    */
  def placementDecisionId(card: WorldCardId): String =
    s"$placementPrefix${card.kind}.${card.value}"

  /** `card`'s follow-up discard decision id,
    * `cardplay.discard.{kind}.{value}`. */
  def discardDecisionId(card: WorldCardId): String =
    s"$discardPrefix${card.kind}.${card.value}"

  /** The card whose placement was answered in `pending`, inverting
    * [[placementDecisionId]]. The parse splits the remainder at the first
    * '.' after the prefix: safe because no card kind contains a '.', whereas
    * the value that follows may contain anything, '.' and ':' included.
    * Takes the first recorded placement answer, which is right because a
    * single-card Search (the settle path this exists for) records exactly
    * one. `None` when nothing has answered a placement decision yet.
    */
  def placedCard(pending: PendingTree): Option[WorldCardId] =
    pending.answered.flatMap(answered => cardFor(answered.decisionId))
      .headOption

  private def cardFor(decisionId: String): Option[WorldCardId] =
    Option.when(decisionId.startsWith(placementPrefix))(
      decisionId.stripPrefix(placementPrefix)).flatMap { rest =>
      val dot = rest.indexOf('.')
      Option.when(dot >= 0)(rest.take(dot) -> rest.drop(dot + 1))
    }.flatMap {
      case ("denizen", value) => Some(DenizenId(value))
      case ("vision", value) => Some(VisionId(value))
      case _ => None
    }

  private def cardPlayOrigin(origin: Origin): CardPlay.Origin = origin match
    case Origin.TemporaryHand => CardPlay.Origin.TemporaryHand
    case Origin.FacedownAdviser => CardPlay.Origin.FacedownAdviser

  private def childrenFor(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: Origin,
      rules: CardPlayRules): Vector[Operation] =
    val from = cardPlayOrigin(origin)
    val held = heldAtOrigin(ready, actor, card, origin)
    val slots = if held then CardSlots.slots(catalog, ready, actor, card, from,
      rules) else Vector.empty
    val placeId = placementDecisionId(card)
    val discardId = discardDecisionId(card)
    val choose = Decide(placeId, actor, DecisionQuery.ChooseOne(
      slots.map(_.option), heading = Some("Play the card")))
    val selected = Branch((_, pending) => {
      val answer = pending.answered.collectFirst {
        case Answered(`placeId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
      }
      val more = pending.answered.collectFirst {
        case Answered(`discardId`, DecisionAnswer.ChooseManyAnswer(refs), _) =>
          refs
      }
      // Once the card has left its origin its slots can no longer be built:
      // the tree is settled from the answers instead, keeping the shape it
      // had when the decisions were asked.
      if !held then answer.toVector.flatMap(ref =>
        settled(ready, actor, card, ref, askedMore = more.nonEmpty))
      else answer.flatMap(ref => slots.find(_.ref == ref)).toVector.flatMap(
        slot => played(catalog, ready, actor, card, from, rules, slot))
    })
    Vector(choose, selected)

  /** The play of `slot`: the follow-up when it is a Replace site slot that
    * leaves permissions, the line of each permission its site discards
    * spent, the operations, and the hook. */
  private def played(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, from: CardPlay.Origin,
      rules: CardPlayRules, slot: CardSlot): Vector[Operation] =
    val followUp = CardSlots.moreDiscards(catalog, ready, actor, card, from,
      rules, slot).map(offer => Decide(discardDecisionId(card), actor,
        DecisionQuery.ChooseMany(0, offer.most, offer.options,
          heading = Some("Discard more cards from the site"),
          locked = Vector(offer.locked))))
    // A Branch is selected when the walk reaches it, which for this one is
    // after the follow-up's answer, so the play sees the cards chosen there.
    // The lines come after the last answer and before the play, joining the
    // play's own node so the hook keeps its place.
    val play = Branch((_, pending) => {
      val extra = pending.answered.collectFirst {
        case Answered(id, DecisionAnswer.ChooseManyAnswer(refs), _)
            if id == discardDecisionId(card) => refs.flatMap(siteCard)
      }.getOrElse(Vector.empty)
      val apply = BuildOps((state, _) => CardPlay.operations(catalog, state,
        actor, card, slot.placement, from, extra))
      Vector(CardSlots.credits(catalog, ready, actor, card, rules,
          slot.replacedAtSite.toVector ++ extra) match
        case Left(error) => BuildOps((_, _) => Left(error))
        case Right(notes) if notes.isEmpty => apply
        case Right(notes) => Sequence(notes :+ apply))
    })
    followUp.toVector ++ Vector(play) ++ hook(ready, actor, card,
      slot.placement)

  /** The shape of a play once the card has left its origin: the follow-up if
    * it was asked, the play, and the hook. A resumed walk starts past the
    * decisions and the play, so neither runs again. */
  private def settled(ready: ReadyGame, actor: PlayerId, card: WorldCardId,
      ref: DecisionOptionRef, askedMore: Boolean): Vector[Operation] =
    playedAs(ref).map(placementOf).toVector.flatMap { placement =>
      val followUp = Option.when(askedMore)(Decide(discardDecisionId(card),
        actor, DecisionQuery.ChooseMany(0, 0, Vector.empty)))
      val play = BuildOps((_, _) => Left(OathViolation.InvalidSearchPlacement(
        "the card has already been played")))
      followUp.toVector ++ Vector(play) ++ hook(ready, actor, card, placement)
    }

  private def placementOf(way: PlayedAs): SearchPlacement = way match
    case PlayedAs.Discarded => SearchPlacement.Discard
    case PlayedAs.AtSite => SearchPlacement.Site(None)
    case PlayedAs.Faceup => SearchPlacement.Adviser(Orientation.FaceUp, None)
    case PlayedAs.Facedown => SearchPlacement.Adviser(Orientation.FaceDown, None)

  private def hook(ready: ReadyGame, actor: PlayerId, card: WorldCardId,
      placement: SearchPlacement): Vector[Operation] = placement match
    case SearchPlacement.Adviser(Orientation.FaceDown, _) =>
      Vector(CardPlayedFacedown(card, actor))
    case _ => CardPlay.playedSource(ready, actor, card, placement)
      .map(CardPlayedFaceup(card, _)).toVector

  private def siteCard(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{CardPlay, CardPlayRules}
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.walker.ModifierChoice
import oathdigital.model._

/** The embeddable card play (card play slots design, "Placement procedure"):
  *
  *  1. `cardplay.place.{kind}.{value}`, a choose-one of the card's slots
  *     (`CardSlots`).
  *  2. The follow-ups, each asked only when it applies:
  *     - `cardplay.replaced.{kind}.{value}`, when a discard and a bury may
  *       both take the replaced card: a choose-one of Bury or Discard.
  *     - `cardplay.discard.{kind}.{value}`, when the answer was a Replace
  *       site slot and discard permissions remain: a choose-many of the
  *       site's other row cards, from none up to the permissions left, the
  *       replaced card locked.
  *     - `cardplay.bury.{kind}.{value}`, when the replaced card was
  *       discarded and a bury permission holds: a choose-many of at most one
  *       of the site's remaining row cards and its relics.
  *  3. The line of each permission a site removal spent, the play's
  *     operations, then the `CardPlayedFacedown` or `CardPlayedFaceup` hook.
  *
  * The facedown-adviser play asks one decision before these:
  * [[facedownAdviserDecisionId]], a board choose-one over the actor's
  * facedown advisers. Its start names no card, so Undo can take the start
  * back.
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
    case _: DecisionOptionRef.SiteRelicSlot => Some(PlayedAs.AtSite)
    case DecisionOptionRef.AdviserPlay(_, _, faceup) =>
      Some(if faceup then PlayedAs.Faceup else PlayedAs.Facedown)
    case _: DecisionOptionRef.RevealedVision | CardSlots.play =>
      Some(PlayedAs.Faceup)
    case _ => None

  /** The site a placement option plays the card to, for a site slot or a
    * site relic's bury. */
  def siteOf(ref: DecisionOptionRef): Option[SiteId] = ref match
    case DecisionOptionRef.SiteSlot(site, _) => Some(site)
    case DecisionOptionRef.SiteRelicSlot(site, _) => Some(site)
    case _ => None

  /** The facedown-adviser play's first decision: which facedown adviser to
    * play, answered on the actor's board. */
  val facedownAdviserDecisionId: String = "cardplay.facedown-adviser"

  def buildFacedown(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
      "facedown-adviser play takes no start selection"))
    _ <- Either.cond(facedownAdvisers(ready, actor).nonEmpty, (),
      OathViolation.InvalidSearchPlacement("no facedown adviser to play"))
  yield facedownTree(catalog, ready, actor)

  /** Resume rebuilds what the start built. A start journaled before the pick
    * moved into the walk names its card as the one start selection, and its
    * tree is that card's placement alone. */
  def rebuildFacedown(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] =
    if args.isEmpty then Right(facedownTree(catalog, ready, actor))
    else facedownCard(args).map(unchecked(catalog, ready, actor, _,
      Origin.FacedownAdviser))

  /** The modifiers, then the pick, then the picked card's placement. Once
    * the card has been played, a resumed walk rebuilds the pick without it;
    * the walk is past the pick by then, and the placement settles from its
    * answers. */
  private def facedownTree(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId): Operation =
    val pick = Decide(facedownAdviserDecisionId, actor, DecisionQuery.ChooseOne(
      facedownAdvisers(ready, actor).map(adviserOption),
      heading = Some("Choose a facedown adviser to play")))
    // The walk reaches the Branch after the pick's answer, so the placement
    // is planned from the state the card is played in.
    val placement = Branch((current, pending) =>
      pending.answered.collectFirst {
        case Answered(`facedownAdviserDecisionId`,
            DecisionAnswer.ChooseOneAnswer(ref), _) => ref
      }.flatMap(worldCard).toVector.map(card =>
        unchecked(catalog, current, actor, card, Origin.FacedownAdviser,
          ModifierPicks.cards(pending.answered))))
    val rest = Sequence(Vector(pick, placement))
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.SearchModifierSelection, rest, "Play facedown adviser"),
      rest))

  /** The cards `actor` holds as facedown advisers, in board order. */
  private def facedownAdvisers(ready: ReadyGame, actor: PlayerId)
      : Vector[WorldCardId] =
    ready.game.current.players.find(_.player == actor).toVector
      .flatMap(_.advisers.collect[WorldCardId] {
        case DenizenState(id, Orientation.FaceDown, _) => id
        case VisionState(id, Orientation.FaceDown) => id
      })

  private def adviserOption(card: WorldCardId): DecisionOption = card match
    case id: DenizenId => DecisionOption.Denizen(DecisionOptionRef.Denizen(id))
    case id: VisionId => DecisionOption.Vision(DecisionOptionRef.Vision(id))

  private def worldCard(ref: DecisionOptionRef): Option[WorldCardId] =
    ref match
      case DecisionOptionRef.Denizen(id) => Some(id)
      case DecisionOptionRef.Vision(id) => Some(id)
      case _ => None

  private def facedownCard(args: Vector[DecisionOptionRef])
      : Either[OathViolation, WorldCardId] = args match
    case Vector(DecisionOptionRef.Denizen(id)) => Right(id)
    case Vector(DecisionOptionRef.Vision(id)) => Right(id)
    case _ => Left(OathViolation.InvalidEventOrder(
      "facedown-adviser play requires exactly one held card"))

  /** The placement subtree planned under one set of [[CardPlayRules]]. A
    * rewrite of its children joins the planning, so a later `adjust` keeps
    * it. */
  final class PlacementBody private[cardplay](val rules: CardPlayRules,
      childrenAt: CardPlayRules => Vector[Operation])
      extends RewritableComposite:
    override val children: Vector[Operation] = childrenAt(rules)
    private[cardplay] def adjust(change: CardPlayRules => CardPlayRules)
        : PlacementBody = new PlacementBody(change(rules), childrenAt)
    def rewritten(rewrite: Operation => Operation): Operation =
      new PlacementBody(rules, childrenAt.andThen(_.map(rewrite)))

  /** Generic rules-aware placement seam. A power changes the rules its play is
    * planned under without placing its identity in the card-play code.
    *
    * `children` is the play under the default rules, in one body, so a
    * rewrite of the body and an [[adjust]] compose in either order. A
    * `Transform` at `SearchPlayAdviser` calls [[adjust]] with the children
    * it was handed and the change it wants. Every contributor's change is
    * applied to the same rules, so contributors compose in any order, and
    * the result is the play planned under all of them. A node a contributor
    * appends after the body is kept by the later ones.
    */
  final class PlacementTree private[cardplay](val card: WorldCardId,
      childrenAt: CardPlayRules => Vector[Operation])
      extends Operation:
    override val window: Option[PowerWindow] =
      Some(PowerWindow.SearchPlayAdviser)
    override val children: Vector[Operation] =
      Vector(new PlacementBody(CardPlayRules.default, childrenAt))

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
      card: WorldCardId, origin: Origin): Boolean = origin match
    case Origin.TemporaryHand =>
      ready.game.current.temporaryHands.getOrElse(actor, Vector.empty).contains(card)
    case Origin.FacedownAdviser => facedownAdvisers(ready, actor).contains(card)

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
      card: WorldCardId, origin: Origin,
      picked: Vector[CardId] = Vector.empty): Operation =
    new PlacementTree(card, rules =>
      childrenFor(catalog, ready, actor, card, origin, rules, picked))

  private val placementPrefix = "cardplay.place."
  private val discardPrefix = "cardplay.discard."
  private val replacedPrefix = "cardplay.replaced."
  private val buryPrefix = "cardplay.bury."

  /** `card`'s placement decision id, `cardplay.place.{kind}.{value}` -- the
    * one place that spells it, so every reader agrees with `childrenFor`.
    */
  def placementDecisionId(card: WorldCardId): String =
    s"$placementPrefix${card.kind}.${card.value}"

  /** `card`'s follow-up discard decision id,
    * `cardplay.discard.{kind}.{value}`. */
  def discardDecisionId(card: WorldCardId): String =
    s"$discardPrefix${card.kind}.${card.value}"

  /** `card`'s bury-or-discard decision id,
    * `cardplay.replaced.{kind}.{value}`. */
  def replacedDecisionId(card: WorldCardId): String =
    s"$replacedPrefix${card.kind}.${card.value}"

  /** `card`'s bury follow-up decision id, `cardplay.bury.{kind}.{value}`. */
  def buryDecisionId(card: WorldCardId): String =
    s"$buryPrefix${card.kind}.${card.value}"

  /** The answers of the bury-or-discard decision. */
  val buryReplaced: DecisionOptionRef.Button = DecisionOptionRef.Button("bury")
  val discardReplaced: DecisionOptionRef.Button =
    DecisionOptionRef.Button("discard")

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
      rules: CardPlayRules, picked: Vector[CardId]): Vector[Operation] =
    val from = cardPlayOrigin(origin)
    val held = heldAtOrigin(ready, actor, card, origin)
    val slots = if held then CardSlots.slots(catalog, ready, actor, card, from,
      rules, picked) else Vector.empty
    val placeId = placementDecisionId(card)
    val choose = Decide(placeId, actor, DecisionQuery.ChooseOne(
      slots.map(_.option), heading = Some("Play the card")))
    val selected = Branch((_, pending) => {
      val answer = pending.answered.collectFirst {
        case Answered(`placeId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
      }
      // Once the card has left its origin its slots can no longer be built:
      // the tree is settled from the answers instead, keeping the shape it
      // had when the decisions were asked.
      if !held then answer.toVector.flatMap(ref =>
        settled(ready, actor, card, ref))
      else answer.flatMap(ref => slots.find(_.ref == ref)).toVector.flatMap(
        slot => played(catalog, ready, actor, card, from, rules, slot,
          picked))
    })
    Vector(choose, selected)

  /** The play of `slot`: the follow-ups, the line of each permission its
    * site removals spent and the operations, and the hook. The follow-ups
    * sit in one node whose three places are always filled, by a Decide or a
    * Branch that offers nothing, so the shape never depends on whether the
    * walker asked them (it skips a choose-many with no option left,
    * recording no answer). */
  private def played(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, from: CardPlay.Origin,
      rules: CardPlayRules, slot: CardSlot, picked: Vector[CardId])
      : Vector[Operation] =
    val replacedId = replacedDecisionId(card)
    val discardId = discardDecisionId(card)
    val buryId = buryDecisionId(card)
    def chose(pending: PendingTree, ref: DecisionOptionRef): Boolean =
      pending.answered.collectFirst {
        case Answered(`replacedId`, DecisionAnswer.ChooseOneAnswer(r), _) =>
          r
      }.contains(ref)
    // The replaced card is buried when the slot is a bury, or its follow-up
    // says Bury.
    def buriesReplaced(pending: PendingTree): Boolean =
      slot.buried.exists(slot.replacedAtSite.contains) ||
        chose(pending, buryReplaced)
    def discarded(pending: PendingTree): Vector[CardId] =
      pending.answered.collectFirst {
        case Answered(`discardId`, DecisionAnswer.ChooseManyAnswer(refs), _) =>
          refs.flatMap(siteCard)
      }.getOrElse(Vector.empty)
    def buriedMore(pending: PendingTree): Option[CardId] =
      pending.answered.collectFirst {
        case Answered(`buryId`, DecisionAnswer.ChooseManyAnswer(refs), _) =>
          refs.flatMap(ref => siteCard(ref).orElse(siteRelic(ready, ref)))
      }.flatMap(_.headOption)
    val askReplaced: Operation =
      if !slot.mayBury then noFollowUp
      else Decide(replacedId, actor, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(buryReplaced, "Bury"),
        DecisionOption.Button(discardReplaced, "Discard")),
        heading = Some("Bury or discard the replaced card")))
    val moreDiscards = Branch((_, pending) => CardSlots.moreDiscards(catalog,
      ready, actor, card, from, rules, slot, buriesReplaced(pending), picked)
      .toVector.map(offer => Decide(discardId, actor,
        DecisionQuery.ChooseMany(0, offer.most, offer.options,
          heading = Some("Discard more cards from the site"),
          locked = Vector(offer.locked),
          confirm = Some(CountedConfirm("Discard", "more card", "more cards",
            Some("Discard no more")))))))
    // A bury the slot made, or the replaced card's, spends the bury.
    val moreBuries = Branch((_, pending) =>
      if slot.buried.nonEmpty || buriesReplaced(pending) then Vector.empty
      else CardSlots.moreBuries(catalog, ready, actor, card, from, rules,
        slot, discarded(pending), picked).toVector.map(offer =>
        Decide(buryId, actor, DecisionQuery.ChooseMany(0, 1, offer.options,
          heading = Some("Bury a card at the site"),
          confirm = Some(CountedConfirm("Bury", "card", "cards",
            Some("Bury nothing")))))))
    val followUp = Sequence(Vector(askReplaced, moreDiscards, moreBuries))
    // A Branch is selected when the walk reaches it, which for this one is
    // after the follow-up's answer, so the play sees the cards chosen there.
    // The lines come after the last answer and before the play, joining the
    // play's own node so the hook keeps its place. They sit in a node of
    // their own, so the play is always the node's second child, as in
    // `settled`: a trigger's follow-up after the play is addressed under it.
    val play = Branch((_, pending) => {
      val extra = discarded(pending)
      val buried =
        if buriesReplaced(pending) then slot.replacedAtSite
        else slot.buried.orElse(buriedMore(pending))
      val apply = BuildOps((state, _) => CardPlay.operations(catalog, state,
        actor, card, slot.placement, from, extra, buried))
      val credited = slot.site.fold[Either[OathViolation, Vector[Note]]](
        Right(Vector.empty))(site => CardSlots.credits(catalog, ready, actor,
        card, site, rules,
        slot.replacedAtSite.filterNot(buried.contains).toVector ++ extra,
        buried))
      credited match
        case Left(error) => Vector(Sequence(Vector.empty),
          BuildOps((_, _) => Left(error)))
        case Right(notes) => Vector(Sequence(notes), apply)
    })
    Vector(followUp, play) ++ hook(ready, actor, card, slot.placement)

  /** The follow-up's place when nothing is to be asked. */
  private def noFollowUp: Operation = Sequence(Vector.empty)

  /** The shape of a play once the card has left its origin: the follow-ups'
    * place, left empty, then the play and the hook at the positions the
    * live shape gives them. A resumed walk starts past the decisions and
    * the play, so neither runs again, and nothing addresses inside the
    * follow-ups' place. */
  private def settled(ready: ReadyGame, actor: PlayerId, card: WorldCardId,
      ref: DecisionOptionRef): Vector[Operation] =
    playedAs(ref).flatMap(way => siteOf(ref)
        .map(SearchPlacement.Site(_, None)).orElse(placementOf(way)))
        .toVector.flatMap { placement =>
      // The play's shape as `played` built it, lines then play, so a path
      // into a follow-up after the play finds it.
      val play = Sequence(Vector(Sequence(Vector.empty),
        BuildOps((_, _) => Left(OathViolation.InvalidSearchPlacement(
          "the card has already been played")))))
      Vector(noFollowUp, play) ++ hook(ready, actor, card, placement)
    }

  /** The placement a way of playing names on its own. A play at a site
    * names its site only in its ref (`siteOf`). */
  private def placementOf(way: PlayedAs): Option[SearchPlacement] = way match
    case PlayedAs.Discarded => Some(SearchPlacement.Discard)
    case PlayedAs.AtSite => None
    case PlayedAs.Faceup =>
      Some(SearchPlacement.Adviser(Orientation.FaceUp, None))
    case PlayedAs.Facedown =>
      Some(SearchPlacement.Adviser(Orientation.FaceDown, None))

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

  /** The relic a site relic slot names, on the board the play was planned
    * on. */
  private def siteRelic(ready: ReadyGame, ref: DecisionOptionRef)
      : Option[CardId] = ref match
    case DecisionOptionRef.SiteRelicSlot(site, index) =>
      ready.game.current.map.sites.get(site).flatMap(_.relics.lift(index))
        .map(_.id)
    case _ => None

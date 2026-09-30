package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.actions.{CardPlay, PlacementRules, RuleNotes}
import oathdigital.model._

/** Embeddable card-placement tree using CardPlay's pure operation planner. */
object CardPlayProcedure:
  enum Origin { case TemporaryHand, FacedownAdviser }

  private val discard = DecisionOptionRef.Button("discard")
  private val site = DecisionOptionRef.Button("site")
  private val adviserFaceUp = DecisionOptionRef.Button("adviser-faceup")
  private val adviserFaceDown = DecisionOptionRef.Button("adviser-facedown")

  /** The option that plays to a site without discarding a site card first,
    * offered only when `PlacementRules.siteDiscardFirst` makes the discard
    * optional.
    */
  val noReplacement: DecisionOption = DecisionOption.Button(
    DecisionOptionRef.Button("replace:none"), "Discard nothing")

  private def label(ref: DecisionOptionRef.Button): String = ref match
    case `discard` => "Discard"
    case `site` => "Play at site"
    case `adviserFaceUp` => "Play faceup"
    case `adviserFaceDown` => "Play facedown"
    case other => other.key

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

  /** The placement subtree planned under one set of [[PlacementRules]]. */
  final class PlacementBody private[cardplay](val rules: PlacementRules,
      childrenAt: PlacementRules => Vector[Operation]) extends Operation:
    override val children: Vector[Operation] = childrenAt(rules)
    private[cardplay] def adjust(change: PlacementRules => PlacementRules)
        : PlacementBody = new PlacementBody(change(rules), childrenAt)

  /** Generic rules-aware placement seam. A power changes the rules its play is
    * planned under without placing its identity in the card-play code.
    *
    * `children` is the play under the default rules. A `Transform` at
    * `SearchPlayAdviser` calls [[adjust]] with the children it was handed and
    * the change it wants. Every contributor's change is applied to the same
    * rules, so contributors compose in any order, and the result is the play
    * planned under all of them.
    */
  final class PlacementTree private[cardplay](val card: WorldCardId,
      childrenAt: PlacementRules => Vector[Operation])
      extends Operation:
    override val window: Option[PowerWindow] =
      Some(PowerWindow.SearchPlayAdviser)
    override val children: Vector[Operation] =
      childrenAt(PlacementRules.default)

    def adjust(current: Vector[Operation])(
        change: PlacementRules => PlacementRules): Vector[Operation] =
      current match
        case Vector(body: PlacementBody) => Vector(body.adjust(change))
        case _ => Vector(new PlacementBody(change(PlacementRules.default),
          childrenAt))

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

  /** `card`'s placement decision id, `cardplay.place.{kind}.{value}` -- the
    * one place that spells it, so every reader agrees with `childrenFor`.
    */
  def placementDecisionId(card: WorldCardId): String =
    s"$placementPrefix${card.kind}.${card.value}"

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

  private def childrenFor(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: Origin,
      rules: PlacementRules)
      : Vector[Operation] =
      val legacyOrigin = origin match
        case Origin.TemporaryHand => CardPlay.Origin.TemporaryHand
        case Origin.FacedownAdviser => CardPlay.Origin.FacedownAdviser
      val held = heldAtOrigin(ready, actor, card, origin)
      val candidates = if !held then Vector.empty else CardPlay.legalChoices(
        catalog, ready, actor, card, legacyOrigin, rules).map { choice =>
        val ref = choice.placement match
          case SearchPlacement.Discard => discard
          case _: SearchPlacement.Site => site
          case SearchPlacement.Adviser(Orientation.FaceUp, _) => adviserFaceUp
          case SearchPlacement.Adviser(Orientation.FaceDown, _) => adviserFaceDown
        (ref, choice.placement,
          choice.replacements.map(id => replacementOption(id) -> id),
          choice.replacementOptional)
      }
      // A Vision is never played to a site, so that button is not offered:
      // rejecting it after the click told the player only that a button they
      // were given does not work. It may still be held as a facedown adviser
      // -- that is how an Exile keeps a Vision to reveal later, and how
      // Conspiracy is later played faceup from the Advisers area.
      val offered = if !card.isInstanceOf[VisionId] then candidates
        else candidates.filter(pair => pair._1 != site)
      val options = offered.map { case (ref, _, _, _) =>
        DecisionOption.Button(ref, label(ref))
      }
      val decisionId = placementDecisionId(card)
      val choose = Decide(decisionId, actor, DecisionQuery.ChooseOne(options,
        heading = Some("Play or discard card")))
      val selected = Branch((_, pending) => {
        val ref = pending.answered.collectFirst {
          case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(value), _) =>
            value
        }
        val replacementId = s"cardplay.replace.${card.kind}.${card.value}"
        val replaced = pending.answered.exists(_.decisionId == replacementId)
        // Once the card has left its origin the choices can no longer be
        // planned: the tree is settled from the answers instead, keeping the
        // shape it had when the decisions were asked.
        val chosen = if held then offered.find(pair => ref.contains(pair._1))
        else ref.flatMap(settled(_, card, replaced))
        chosen.toVector.flatMap {
          case (_, placement, replacements, optional) =>
            val choice = if replacements.isEmpty then Vector.empty else Vector(
              Decide(replacementId, actor, DecisionQuery.ChooseOne(
                (if optional then Vector(noReplacement) else Vector.empty) ++
                  replacements.map(_._1),
                heading = Some("Choose a card to discard"))))
            val apply = BuildOps((state, pending) => {
              val chosen = if replacements.isEmpty then Right(placement)
              else pending.answered.collectFirst {
                case Answered(`replacementId`,
                    DecisionAnswer.ChooseOneAnswer(value), _) => value
              }.toRight(OathViolation.InvalidSearchPlacement(
                "replacement was not selected")).flatMap { value =>
                if optional && value == noReplacement.ref then Right(placement)
                else replacements.find(_._1.ref == value).map(_._2)
                  .toRight(OathViolation.InvalidSearchPlacement(
                    "replacement was not selected")).map { id => placement match {
                      case _: SearchPlacement.Site =>
                        SearchPlacement.Site(Some(id))
                      case value: SearchPlacement.Adviser =>
                        value.copy(replace = Some(id))
                      case SearchPlacement.Discard => SearchPlacement.Discard
                    }}
              }
              chosen.flatMap(CardPlay.plannedOperations(catalog, state, actor,
                card, _, legacyOrigin, rules))
            })
            val hook: Vector[Operation] = placement match {
              case SearchPlacement.Adviser(Orientation.FaceDown, _) =>
                Vector(CardPlayedFacedown(card, actor))
              case _ => CardPlay.playedSource(ready, actor, card, placement)
                .map(CardPlayedFaceup(card, _)).toVector
            }
            // The line of whatever asked for the discard, after its answer
            // and before the play. A note never comes before a decision
            // already in the tree, so a game parked on one resumes where it
            // was; it joins the play's own node, keeping the hook's place.
            val notice: Vector[Operation] = placement match
              case _: SearchPlacement.Site if replacements.nonEmpty =>
                if rules.siteDiscardFirst then rules.siteDiscardNote.toVector
                else homelandNote(ready, actor).toVector
              case _ => Vector.empty
            val played = if notice.isEmpty then apply
              else Sequence(notice :+ apply)
            choice ++ Vector(played) ++ hook
        }
      })
      Vector(choose, selected)

  /** The Homeland rule's line, "{site}: {Red} may discard a card at their
    * site first.": without a power's permission, only the Homeland of the
    * card's suit asks for a discard at a site. */
  private def homelandNote(ready: ReadyGame, actor: PlayerId): Option[Note] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .map(site => Note(RuleNotes.homelandDiscard, _ => Some(
        PlacementRules.discardFirst(PowerSourceRef.Site(site),
          NoteArg.Player(actor)))))

  /** The replacement's options are not read once the play is made, only that
    * the decision held its place in the tree. */
  private def settled(ref: DecisionOptionRef, card: WorldCardId,
      replaced: Boolean)
      : Option[(DecisionOptionRef, SearchPlacement,
          Vector[(DecisionOption, CardId)], Boolean)] =
    val placement: Option[SearchPlacement] = ref match
      case `discard` => Some(SearchPlacement.Discard)
      case `site` => Some(SearchPlacement.Site(None))
      case `adviserFaceUp` =>
        Some(SearchPlacement.Adviser(Orientation.FaceUp, None))
      case `adviserFaceDown` =>
        Some(SearchPlacement.Adviser(Orientation.FaceDown, None))
      case _ => None
    placement.map(value => (ref, value,
      if replaced then Vector((noReplacement: DecisionOption) -> card)
      else Vector.empty,
      false))

  private def replacementOption(id: CardId): DecisionOption = id match
    case value: DenizenId =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(value))
    case value: VisionId =>
      DecisionOption.Vision(DecisionOptionRef.Vision(value))
    case value => DecisionOption.Button(
      DecisionOptionRef.Button(s"replace:${value.kind}:${value.value}"),
      value.value)

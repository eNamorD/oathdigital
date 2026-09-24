package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.actions.{CardPlay, PlacementRules}
import oathdigital.gameplay.operations.DiscardRestrictions
import oathdigital.model._

/** Embeddable card-placement tree using CardPlay's pure operation planner. */
object CardPlayProcedure {
  sealed trait Origin
  object Origin {
    case object TemporaryHand extends Origin
    case object FacedownAdviser extends Origin
  }

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

  private def label(ref: DecisionOptionRef.Button): String = ref match {
    case `discard` => "Discard"
    case `site` => "Play at site"
    case `adviserFaceUp` => "Play faceup"
    case `adviserFaceDown` => "Play facedown"
    case other => other.key
  }

  def buildFacedown(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
    card <- facedownCard(args)
    tree <- build(catalog, ready, actor, card, Origin.FacedownAdviser)
  } yield tree

  def rebuildFacedown(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] =
    facedownCard(args).map(unchecked(catalog, ready, actor, _,
      Origin.FacedownAdviser))

  private def facedownCard(args: Vector[DecisionOptionRef])
      : Either[OathViolation, WorldCardId] = args match {
    case Vector(DecisionOptionRef.Denizen(id)) => Right(id)
    case Vector(DecisionOptionRef.Vision(id)) => Right(id)
    case _ => Left(OathViolation.InvalidEventOrder(
      "facedown-adviser play requires exactly one held card"))
  }

  /** The placement subtree planned under one set of [[PlacementRules]]. */
  final class PlacementBody private[cardplay](val rules: PlacementRules,
      childrenAt: PlacementRules => Vector[Operation]) extends Operation {
    override val children: Vector[Operation] = childrenAt(rules)
    private[cardplay] def adjust(change: PlacementRules => PlacementRules)
        : PlacementBody = new PlacementBody(change(rules), childrenAt)
  }

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
      extends Operation {
    override val window: Option[PowerWindow] =
      Some(PowerWindow.SearchPlayAdviser)
    override val children: Vector[Operation] =
      childrenAt(PlacementRules.default)

    def adjust(current: Vector[Operation])(
        change: PlacementRules => PlacementRules): Vector[Operation] =
      current match {
        case Vector(body: PlacementBody) => Vector(body.adjust(change))
        case _ => Vector(new PlacementBody(change(PlacementRules.default),
          childrenAt))
      }
  }

  /** Whether `card` is still at the origin the play started from. Once it is
    * not, the play has been made and the tree is settled from the answers.
    */
  private def heldAtOrigin(ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Boolean = {
    val player = ready.game.current.players.find(_.player == actor)
    origin match {
      case Origin.TemporaryHand =>
        ready.game.current.temporaryHands.getOrElse(actor, Vector.empty).contains(card)
      case Origin.FacedownAdviser => player.exists(_.advisers.exists {
        case DenizenState(id, Orientation.FaceDown, _) => id == card
        case VisionState(id, Orientation.FaceDown) => id == card
        case _ => false
      })
    }
  }

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Either[OathViolation, Operation] =
    if (!heldAtOrigin(ready, actor, card, origin)) Left(
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

  private def childrenFor(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: Origin,
      rules: PlacementRules)
      : Vector[Operation] = {
      val legacyOrigin = origin match {
        case Origin.TemporaryHand => CardPlay.Origin.TemporaryHand
        case Origin.FacedownAdviser => CardPlay.Origin.FacedownAdviser
      }
      val held = heldAtOrigin(ready, actor, card, origin)
      val candidates = if (!held) Vector.empty else CardPlay.legalChoices(
        catalog, ready, actor, card, legacyOrigin, rules).map { choice =>
        val ref = choice.placement match {
          case SearchPlacement.Discard => discard
          case _: SearchPlacement.Site => site
          case SearchPlacement.Adviser(Orientation.FaceUp, _) => adviserFaceUp
          case SearchPlacement.Adviser(Orientation.FaceDown, _) => adviserFaceDown
        }
        (ref, choice.placement,
          choice.replacements.map(id => replacementOption(id) -> id),
          choice.replacementOptional)
      }
      val options = candidates.map { case (ref, _, _, _) =>
        DecisionOption.Button(ref, label(ref))
      }
      val decisionId = s"cardplay.place.${card.kind}.${card.value}"
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
        val chosen = if (held) candidates.find(pair => ref.contains(pair._1))
        else ref.flatMap(settled(_, card, replaced))
        chosen.toVector.flatMap {
          case (_, placement, replacements, optional) =>
            val choice = if (replacements.isEmpty) Vector.empty else Vector(
              Decide(replacementId, actor, DecisionQuery.ChooseOne(
                (if (optional) Vector(noReplacement) else Vector.empty) ++
                  replacements.map(_._1),
                heading = Some("Choose a card to discard"))))
            val apply = BuildOps((state, pending) => {
              val chosen = if (replacements.isEmpty) Right(placement)
              else pending.answered.collectFirst {
                case Answered(`replacementId`,
                    DecisionAnswer.ChooseOneAnswer(value), _) => value
              }.toRight(OathViolation.InvalidSearchPlacement(
                "replacement was not selected")).flatMap { value =>
                if (optional && value == noReplacement.ref) Right(placement)
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
            }, restrictions = (_, _) => Vector(
              new DiscardRestrictions(catalog, actor)))
            val hook: Vector[Operation] = placement match {
              case SearchPlacement.Adviser(Orientation.FaceDown, _) =>
                Vector(CardPlayedFacedown(card, actor))
              case _ => CardPlay.playedSource(ready, actor, card, placement)
                .map(CardPlayedFaceup(card, _)).toVector
            }
            choice ++ Vector(apply) ++ hook
        }
      })
      Vector(choose, selected)
  }

  /** The replacement's options are not read once the play is made, only that
    * the decision held its place in the tree. */
  private def settled(ref: DecisionOptionRef, card: WorldCardId,
      replaced: Boolean)
      : Option[(DecisionOptionRef, SearchPlacement,
          Vector[(DecisionOption, CardId)], Boolean)] = {
    val placement: Option[SearchPlacement] = ref match {
      case `discard` => Some(SearchPlacement.Discard)
      case `site` => Some(SearchPlacement.Site(None))
      case `adviserFaceUp` =>
        Some(SearchPlacement.Adviser(Orientation.FaceUp, None))
      case `adviserFaceDown` =>
        Some(SearchPlacement.Adviser(Orientation.FaceDown, None))
      case _ => None
    }
    placement.map(value => (ref, value,
      if (replaced) Vector((noReplacement: DecisionOption) -> card)
      else Vector.empty,
      false))
  }

  private def replacementOption(id: CardId): DecisionOption = id match {
    case value: DenizenId =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(value))
    case value: VisionId =>
      DecisionOption.Vision(DecisionOptionRef.Vision(value))
    case value => DecisionOption.Button(
      DecisionOptionRef.Button(s"replace:${value.kind}:${value.value}"),
      value.value)
  }
}

package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.actions.{CardPlay, VisionRules}
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
    facedownCard(args).flatMap(build(catalog, ready, actor, _,
      Origin.FacedownAdviser))

  private def facedownCard(args: Vector[DecisionOptionRef])
      : Either[OathViolation, WorldCardId] = args match {
    case Vector(DecisionOptionRef.Denizen(id)) => Right(id)
    case Vector(DecisionOptionRef.Vision(id)) => Right(id)
    case _ => Left(OathViolation.InvalidEventOrder(
      "facedown-adviser play requires exactly one held card"))
  }

  /** Generic limit-aware placement seam. A power can replace children using
    * a different limit without placing its identity in the card-play rules.
    */
  final class PlacementTree private[cardplay](val card: WorldCardId,
      childrenAt: (Int, Int) => Vector[Operation])
      extends Operation {
    override val window: Option[PowerWindow] =
      Some(PowerWindow.SearchPlayAdviser)
    override val children: Vector[Operation] = childrenAt(3, 3)
    def withAdviserLimit(limit: Int): Vector[Operation] =
      childrenAt(limit, limit)
    def withFaceupAdviserLimit(limit: Int): Vector[Operation] =
      childrenAt(limit, 3)
  }

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Either[OathViolation, Operation] = {
    val player = ready.game.current.players.find(_.player == actor)
    val present = origin match {
      case Origin.TemporaryHand =>
        ready.game.current.temporaryHands.getOrElse(actor, Vector.empty).contains(card)
      case Origin.FacedownAdviser => player.exists(_.advisers.exists {
        case DenizenState(id, Orientation.FaceDown, _) => id == card
        case VisionState(id, Orientation.FaceDown) => id == card
        case _ => false
      })
    }
    if (!present) Left(OathViolation.InvalidSearchPlacement(
      "card is not held at the selected origin"))
    else Right(new PlacementTree(card, (faceup, facedown) =>
      childrenFor(catalog, ready, actor, card, origin, faceup, facedown)))
  }

  private def childrenFor(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: Origin, faceupLimit: Int,
      facedownLimit: Int)
      : Vector[Operation] = {
      val legacyOrigin = origin match {
        case Origin.TemporaryHand => CardPlay.Origin.TemporaryHand
        case Origin.FacedownAdviser => CardPlay.Origin.FacedownAdviser
      }
      val candidates = CardPlay.legalChoices(catalog, ready, actor, card,
        legacyOrigin, faceupLimit, facedownLimit).map { choice =>
        val ref = choice.placement match {
          case SearchPlacement.Discard => discard
          case _: SearchPlacement.Site => site
          case SearchPlacement.Adviser(Orientation.FaceUp, _) => adviserFaceUp
          case SearchPlacement.Adviser(Orientation.FaceDown, _) => adviserFaceDown
        }
        (ref, choice.placement,
          choice.replacements.map(id => replacementOption(id) -> id))
      }
      val options = candidates.map { case (ref, _, _) =>
        DecisionOption.Button(ref, ref.key.replace('-', ' '))
      }
      val decisionId = s"cardplay.place.${card.kind}.${card.value}"
      val choose = Decide(decisionId, actor, DecisionQuery.ChooseOne(options,
        heading = Some("Play or discard card")))
      val selected = Branch((_, pending) => {
        val ref = pending.answered.collectFirst {
          case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(value), _) =>
            value
        }
        candidates.find(pair => ref.contains(pair._1)).toVector.flatMap {
          case (_, placement, replacements) =>
            val adviserLimit = placement match {
              case SearchPlacement.Adviser(Orientation.FaceUp, _) => faceupLimit
              case _ => facedownLimit
            }
            val replacementId = s"cardplay.replace.${card.kind}.${card.value}"
            val choice = if (replacements.isEmpty) Vector.empty else Vector(
              Decide(replacementId, actor, DecisionQuery.ChooseOne(
                replacements.map(_._1),
                heading = Some("Choose a card to discard"))))
            val apply = BuildOps((state, pending) => {
              val chosen = if (replacements.isEmpty) Right(placement)
              else pending.answered.collectFirst {
                case Answered(`replacementId`,
                    DecisionAnswer.ChooseOneAnswer(value), _) => value
              }.flatMap(value => replacements.find(_._1.ref == value)
                .map(_._2)).toRight(OathViolation.InvalidSearchPlacement(
                  "replacement was not selected")).map { id => placement match {
                    case _: SearchPlacement.Site => SearchPlacement.Site(Some(id))
                    case value: SearchPlacement.Adviser =>
                      value.copy(replace = Some(id))
                    case SearchPlacement.Discard => SearchPlacement.Discard
                  }}
              chosen.flatMap(CardPlay.plannedOperations(catalog, state, actor,
                card, _, legacyOrigin, adviserLimit))
            })
            val hook = Option.when(card != VisionRules.Conspiracy)(placement)
              .flatMap(CardPlay.playedSource(ready, actor, card, _))
              .map(CardPlayed(card, _)).toVector
            choice ++ Vector(apply) ++ hook
        }
      })
      Vector(choose, selected)
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

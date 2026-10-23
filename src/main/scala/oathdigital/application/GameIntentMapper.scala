package oathdigital.application

import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseManyAnswer, ChooseOneAnswer, DistributeAnswer, PartitionAnswer}
import oathdigital.protocol.{GameIntent => Intent, _}

final case class GameIntentMappingFailure(path: String, message: String)

/** Sole conversion and actor-binding boundary for transport-safe commands. */
object GameIntentMapper:
  private type Result[A] = Either[GameIntentMappingFailure, A]

  def bind(actorId: PlayerId, intent: Intent): Result[GameCommand] =
    val actor = AuthorizedPlayer.forPlayer(actorId)
    intent match
      case Intent.Undo => Right(actor.undo)
      case Intent.Redo => Right(actor.redo)
      case Intent.ResolveWalker(id, value) => decisionAnswer(value).map(p =>
        actor.resolveWalker(TreeDecision(id, p)))
      case Intent.RequestConsent(id, target, granters) =>
        optionRef(target.optionKind, target.optionId, "$.intent.target")
          .map(actor.requestConsent(id, _, granters.map(PlayerId(_))))
      case Intent.AnswerConsent(value) => ConsentChoice.fromKey(value)
        .toRight(GameIntentMappingFailure("$.intent.choice",
          s"invalid consent choice '$value'")).map(actor.answerConsent)
      case Intent.SetConsentPreference(value, requester, setting) =>
        for
          power <- PowerId.fromValue(value).toRight(GameIntentMappingFailure(
            "$.intent.powerId", s"invalid power id '$value'"))
          chosen <- ConsentSetting.fromKey(setting).toRight(
            GameIntentMappingFailure("$.intent.setting",
              s"invalid consent setting '$setting'"))
        yield actor.setConsentPreference(power, PlayerId(requester), chosen)

  private def invalid(path: String, value: String, kind: String) = Left(GameIntentMappingFailure(path, s"unknown $kind '$value'"))
  private def world(value: WorldCard, path: String): Result[WorldCardId] = value.kind match { case "denizen" => Right(DenizenId(value.id)); case "vision" => Right(VisionId(value.id)); case v => invalid(s"$path.kind", v, "world card kind") }
  private def negotiation(value: oathdigital.protocol.NegotiationTerms): Result[oathdigital.model.NegotiationTerms] = traverse(value.disclosures)(disclosure).map(ds => oathdigital.model.NegotiationTerms(value.transfers.map(v => oathdigital.model.NegotiationTransfer(PlayerId(v.recipientPlayerId), v.favor, v.relicIds.map(RelicId.apply))), ds))
  private def disclosure(value: oathdigital.protocol.NegotiationDisclosure): Result[oathdigital.model.NegotiationDisclosure] = value.information match
    case NegotiationInformation.Adviser(owner, c) => world(c, "$.intent.terms.disclosures.information.card").map(v => oathdigital.model.NegotiationDisclosure(PlayerId(value.recipientPlayerId), NegotiationDisclosureRef.Adviser(PlayerId(owner), v)))
    case NegotiationInformation.HeldRelic(owner, relic) => Right(oathdigital.model.NegotiationDisclosure(PlayerId(value.recipientPlayerId), NegotiationDisclosureRef.HeldRelic(PlayerId(owner), RelicId(relic))))
    case NegotiationInformation.SiteRelic(site, relic) => Right(oathdigital.model.NegotiationDisclosure(PlayerId(value.recipientPlayerId), NegotiationDisclosureRef.SiteRelic(SiteId(site), RelicId(relic))))
  /** The kind/id pair back to the engine's option reference. Total over the
    * seven declared variants, and deliberately not a table written here:
    * `DecisionOptionRef.fromWire` is the same function the journal codec
    * decodes with, so a client's answer and a replayed one resolve a given
    * pair identically or not at all.
    */
  private def optionRef(kind: String, id: String,
      path: String): Result[DecisionOptionRef] =
    DecisionOptionRef.fromWire(kind, id).toRight(GameIntentMappingFailure(path,
      s"unknown decision option '$kind/$id'"))

  /** Names no action and no decision id: a walker answer is generic over the
    * query shapes the engine declares, and the engine checks it against the
    * query the parked node actually carries.
    */
  private def decisionAnswer(value: DecisionAnswerWire): Result[DecisionAnswer] = value match
    case DecisionAnswerWire.ChooseOneWire(kind, id) =>
      optionRef(kind, id, "$.intent.payload.option").map(ChooseOneAnswer.apply)
    case DecisionAnswerWire.PartitionWire(placements) =>
      traverse(placements)(row => optionRef(row.optionKind, row.optionId,
        "$.intent.payload.placements.option").map(
          DecisionPlacement(_, row.sectionKey))).map(PartitionAnswer.apply)
    case DecisionAnswerWire.DistributeWire(amounts) =>
      traverse(amounts)(row => optionRef(row.optionKind, row.optionId,
        "$.intent.payload.amounts.option").map(
          DistributeAmount(_, row.amount))).map(DistributeAnswer.apply)
    case DecisionAnswerWire.ChooseManyWire(options) =>
      traverse(options)(row => optionRef(row.optionKind, row.optionId,
        "$.intent.payload.options")).map(ChooseManyAnswer.apply)
    case DecisionAnswerWire.ChooseAmountWire(amount) =>
      Right(ChooseAmountAnswer(amount))
    case DecisionAnswerWire.ProposeTermsWire(terms) =>
      negotiation(terms).map(DecisionAnswer.ProposeTerms.apply)
    case DecisionAnswerWire.AcceptDealWire => Right(DecisionAnswer.AcceptDeal)
    case DecisionAnswerWire.DeclineDealWire => Right(DecisionAnswer.DeclineDeal)
  private def traverse[A,B](values: Vector[A])(f: A => Result[B]): Result[Vector[B]] = values.foldLeft[Result[Vector[B]]](Right(Vector.empty)) { case (Right(acc), v) => f(v).map(acc :+ _); case (l @ Left(_), _) => l }

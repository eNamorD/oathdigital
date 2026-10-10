package oathdigital.protocol

/** Actorless, cross-platform command vocabulary. All identifiers remain opaque
  * strings until the application mapping boundary validates them.
  */
sealed trait GameIntent extends Product with Serializable

object GameIntent:
  case object BeginRest extends GameIntent
  /** Takes back the sender's latest step (undo design). */
  case object Undo extends GameIntent
  /** Restores the sender's latest taken-back step (undo design, "Redo"). */
  case object Redo extends GameIntent
  final case class UsePower(powerId: String, source: WalkerStartArgWire)
      extends GameIntent
  /** Starts a walker procedure. `action` is the engine's persisted
    * `StartableRef` wire key (e.g. `"recover"`). An action asks for its
    * modifiers inside the walk, as its first decision, so a start names none
    * (modifier walker design).
    *
    * `startArgs` is what the player selected before the action started, for
    * an action whose tree needs it: Trade's resource, Take Wealth's pile and
    * the source of a used power. The engine rejects a selection an action did
    * not ask for.
    */
  final case class StartWalker(action: String,
      startArgs: Vector[WalkerStartArgWire] = Vector.empty) extends GameIntent
  /** Answers the currently parked Roll node for `pool`. Carries no die
    * faces: those are generated application-side once the parked pool is
    * validated against this command.
    */
  final case class RollWalker(pool: String) extends GameIntent
  final case class ResolveWalker(decisionId: String,
      payload: DecisionAnswerWire) extends GameIntent
  /** Asks the rulers of the powers blocking `target` at the parked
    * `decisionId` for consent to choose it (consent design). */
  final case class RequestConsent(decisionId: String,
      target: WalkerStartArgWire) extends GameIntent
  /** Answers the consent request waiting on the sender: `never`, `no`,
    * `yes` or `always`. */
  final case class AnswerConsent(choice: String) extends GameIntent
  /** Sets the sender's standing answer for `requesterId` on `powerId`:
    * `never`, `case-by-case` or `always`. Not `playerId`: the codec refuses
    * a field that could name the sender. */
  final case class SetConsentPreference(powerId: String, requesterId: String,
      setting: String) extends GameIntent

/** One game-object reference in a walker procedure's start selection, spelled
  * exactly as a decision answer spells one: a kind and an id, decoded by the
  * same `DecisionOptionRef.fromWire` the engine decodes an answer with.
  *
  * Deliberately NOT a case per action. The protocol has no business knowing
  * that a Travel start names a destination -- only the action itself does,
  * and it checks the shape when it builds its tree.
  */
final case class WalkerStartArgWire(optionKind: String, optionId: String)

final case class WorldCard(kind: String, id: String)
final case class NegotiationTerms(
    transfers: Vector[NegotiationTransfer],
    disclosures: Vector[NegotiationDisclosure]
)
final case class NegotiationTransfer(
    recipientPlayerId: String, favor: Int, relicIds: Vector[String])
final case class NegotiationDisclosure(
    recipientPlayerId: String, information: NegotiationInformation)

sealed trait NegotiationInformation extends Product with Serializable
object NegotiationInformation:
  final case class Adviser(ownerPlayerId: String, card: WorldCard)
      extends NegotiationInformation
  final case class HeldRelic(ownerPlayerId: String, relicId: String)
      extends NegotiationInformation
  final case class SiteRelic(siteId: String, relicId: String)
      extends NegotiationInformation

/** Wire form of a walker decision answer, generic over the engine's
  * `DecisionQuery` shapes rather than over any action's own vocabulary.
  *
  * Every option is named by the kind/id pair the engine's option references
  * carry, so neither case here knows that Recover or Forge exists, and a new
  * walker procedure adds no case. Both fields stay opaque strings until the
  * application mapping boundary resolves them, exactly like every other
  * identifier in this protocol.
  */
sealed trait DecisionAnswerWire extends Product with Serializable
object DecisionAnswerWire:
  /** Answers a choose-one decision with the single option selected. */
  final case class ChooseOneWire(optionKind: String, optionId: String)
      extends DecisionAnswerWire

  /** Answers a partition decision: every offered option, each placed in one
    * declared section.
    */
  final case class PartitionWire(placements: Vector[DecisionPlacementWire])
      extends DecisionAnswerWire

  final case class DistributeWire(amounts: Vector[DistributeAmountWire])
      extends DecisionAnswerWire

  /** Answers a choose-many decision with the options selected. */
  final case class ChooseManyWire(options: Vector[DecisionOptionWire])
      extends DecisionAnswerWire

  /** Answers a choose-amount decision with the amount picked. */
  final case class ChooseAmountWire(amount: Int) extends DecisionAnswerWire

  /** Answers a negotiation decision with the sender's complete terms. */
  final case class ProposeTermsWire(terms: NegotiationTerms)
      extends DecisionAnswerWire

  /** Accepts the current deal. */
  case object AcceptDealWire extends DecisionAnswerWire

  /** Declines, which ends the negotiation. */
  case object DeclineDealWire extends DecisionAnswerWire

/** One option named in a [[DecisionAnswerWire.ChooseManyWire]]. */
final case class DecisionOptionWire(optionKind: String, optionId: String)

/** One option placed in one section of a [[DecisionAnswerWire.PartitionWire]].
  */
final case class DecisionPlacementWire(optionKind: String, optionId: String,
    sectionKey: String)

final case class DistributeAmountWire(optionKind: String, optionId: String,
    amount: Int)

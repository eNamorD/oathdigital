package oathdigital.application

import oathdigital.model._

sealed trait GameCommand extends Product with Serializable
object GameCommand:
  final case class Begin(chronicle: Chronicle, orders: SetupOrders)
      extends GameCommand
  final case class ResolveWalker(actor: PlayerId, treeDecision: TreeDecision)
      extends GameCommand
  /** Takes back the latest live command batch, which `actor` must have
    * issued (undo design, "Rulings"). */
  final case class Undo(actor: PlayerId) extends GameCommand
  /** Restores the batch most recently taken back since the last command
    * batch, which `actor` must have issued (undo design, "Redo"). */
  final case class Redo(actor: PlayerId) extends GameCommand
  /** A command the service routes to its consent desk, never to the rules'
    * walker gate: each is legal over a parked decision (consent design,
    * "Commands"). */
  sealed trait ConsentCommand extends GameCommand
  /** `granter` sets their standing answer for `requester` on `power`. */
  final case class SetConsentPreference(granter: PlayerId, power: PowerId,
      requester: PlayerId, setting: ConsentSetting) extends ConsentCommand
  /** `requester` asks the rulers of the powers blocking `target` at the
    * parked `decisionId` for consent to choose it. `granters` names the
    * alternatives to ask; empty asks them all (catalog batch 8, slice
    * 15). */
  final case class RequestConsent(requester: PlayerId, decisionId: String,
      target: DecisionOptionRef, granters: Vector[PlayerId] = Vector.empty)
      extends ConsentCommand
  /** `granter` answers the request waiting on them. */
  final case class AnswerConsent(granter: PlayerId, choice: ConsentChoice)
      extends ConsentCommand

  /** The player a command's batch is recorded for, in the `CommandAccepted`
    * that opens it (undo design, "Terms"): its actor. Begin has none, so a
    * journal still begins with `GameStarted`; an Undo or a Redo appends its
    * marker alone. */
  def issuer(command: GameCommand): Option[PlayerId] = command match
    case Begin(_, _) => None
    case ResolveWalker(actor, _) => Some(actor)
    case Undo(_) => None
    case Redo(_) => None
    case _: ConsentCommand => None

/** One answer to the currently parked generic walker decision. */
final case class TreeDecision(decisionId: String, answer: DecisionAnswer)

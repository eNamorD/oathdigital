package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{DenizenId, NoteKey, Operation, PowerWindow}

/** A battle plan whose card says "At end, discard": the card goes to a
  * discard pile once the Campaign has resolved, whoever won, and the plan
  * writes `PlanDiscard.discarded` for it. Both the discard and its line come
  * from here, so a plan cannot carry one without the other.
  *
  * A plan states only what differs from the standard: its own keys in
  * `planNoteKeys`, any window other than the end discard in `otherWindows`,
  * and a narrower discard in `endDiscard`.
  */
private[campaign] trait DiscardedAtEnd extends BattlePlan:
  def catalog: ExecutableCatalog
  def cardId: DenizenId

  /** This plan's own keys, beside the end-of-Campaign discard line. */
  def planNoteKeys: Vector[NoteKey] = Vector.empty

  final override def noteKeys: Vector[NoteKey] =
    planNoteKeys :+ PlanDiscard.discarded

  /** The windows this plan acts in other than its end discard. */
  def otherWindows: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty

  /** The end discard. A plan that discards only on some outcome narrows
    * this; it never drops the line, because the line is the discard's. */
  def endDiscard: PlanUse => Vector[Operation] = use =>
    PlanDiscard.afterCampaign(catalog, id, use, cardId)

  final override def later: Map[PowerWindow, PlanUse => Vector[Operation]] =
    otherWindows + (PowerWindow.CampaignActionEligibility -> endDiscard)

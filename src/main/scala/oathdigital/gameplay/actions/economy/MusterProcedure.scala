package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Muster on the walker: pay one favor on a card at the pawn site and one
  * Supply, and gain a warband for each matching adviser plus one. Takes no
  * start selection; the modifiers are the `muster.modifiers` decision and the
  * card is the `muster.source` decision.
  */
object MusterProcedure:
  val decisionId: String = "muster.source"

  private val kind = EconomyTree.Kind(decisionId,
    "Choose a card to Muster from", PowerWindow.MusterModifierSelection,
    "Start Muster", PowerWindow.MusterActionEligibility,
    PowerWindow.MusterSourceSelection, PowerWindow.MusterCost,
    PowerWindow.MusterGain, Cost(favor = 1),
    (actor, _, matching, force) => Some(Gain.Warbands(actor, force, 1 + matching)))

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] =
    EconomyTree.build(catalog, state, actor, kind)

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] =
    Right(EconomyTree.tree(catalog, state, actor, kind))

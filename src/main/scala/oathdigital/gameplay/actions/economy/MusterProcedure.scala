package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._

/** Muster on the walker: pay one favor on a card at the pawn site and one
  * Supply, and gain a warband for each matching adviser plus one. Takes no
  * start selection; the card is the `muster.source` decision.
  */
object MusterProcedure {
  val decisionId: String = "muster.source"

  /** Every decision a Muster or a power inside it asks starts with this. */
  val decisionPrefix: String = "muster."

  private val kind = EconomyTree.Kind(decisionId,
    "Choose a card to Muster from", PowerWindow.MusterActionEligibility,
    PowerWindow.MusterSourceSelection, PowerWindow.MusterCost,
    PowerWindow.MusterGain, Cost(favor = 1),
    (actor, _, matching, force) => Some(Gain.Warbands(actor, force, 1 + matching)))

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] =
    EconomyTree.build(catalog, state, actor, kind)

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] =
    Right(EconomyTree.tree(catalog, state, actor, kind))

  /** Every source the actor could Muster from now, each previewed through the
    * same tree a start walks. Empty when the action cannot start at all. This
    * is the single definition the start control and the parked decision read.
    */
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, powers: WalkerPowers): Vector[PreviewedOption] =
    build(catalog, state, actor)
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .getOrElse(Vector.empty)
}

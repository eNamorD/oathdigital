package oathdigital.gameplay.powerresolver

import oathdigital.model.{ConsentKey, Cost, DecisionOptionRef, OathViolation,
  Operation, PlayerId, PowerId, LogNote, PowerTiming, QueuedProcedure,
  ReadyGame}

/** Whether a procedure a phase power would queue starts now, and why not
  * (catalog batch 8, slice 13): the start check the driver runs, which the
  * engine fills in. A power cannot reach the walker, so it is handed this.
  */
trait QueuedStarts:
  def refusal(ready: ReadyGame, entry: QueuedProcedure)
      : Option[OathViolation]

  /** Whether the entry would start, and when only waivable restrictions
    * refuse it, the grants that would let it start (catalog batch 8, slice
    * 15). By default what `refusal` says, with nothing to ask. */
  def consent(ready: ReadyGame, entry: QueuedProcedure)
      : QueuedStarts.Verdict =
    refusal(ready, entry).fold(QueuedStarts.Verdict.Starts)(
      QueuedStarts.Verdict.Refused(_))

object QueuedStarts:
  /** No engine behind it: every start is taken to be accepted. */
  val unchecked: QueuedStarts = (_, _) => None

  /** One grant a refused start waits on: `granter` consenting to `power`,
    * remembered under `key`, the key the started walk looks it up under.
    * `asked` is the reason while it may be asked; `plain` the reason once
    * it was refused. */
  final case class Needed(power: PowerId, granter: PlayerId,
      key: ConsentKey, asked: LogNote, plain: Option[LogNote])

  enum Verdict:
    case Starts
    case Waiting(needed: Vector[Needed])
    case Refused(violation: OathViolation)

/** A WAKE, ACTION or REST power a player uses as an action.
  *
  * The engine finds its sources, checks access and once-per-turn use, and
  * records the use after `build`'s tree. `build` must be a pure function of
  * state: the tree is rebuilt on every resume.
  */
trait PhasePower extends NotingPower:
  def id: PowerId
  def timing: PowerTiming
  /** A power its holder must use (catalog batch 2, N8): it runs on its own
    * at the start of its phase and is never offered or accepted as an
    * optional use. Only WAKE powers are forced today. */
  def forced: Boolean = false
  /** The power's own name, for a menu button when its source carries two
    * powers of one phase. */
  def buttonName: Option[String] = None
  /** Power-specific preconditions beyond access and once-per-turn. */
  def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef): Boolean
  /** What using the power costs, placed onto its source card. The engine
    * prepends the payment and reads "cost payable, including the empty-card
    * rule" as part of `usable`. Free by default. A cost is not accepted from
    * a banner source: banners have no costs.
    */
  def cost: Cost = Cost.free
  /** Whether using the power costs a secret (catalog batch 8, slice 10):
    * its cost has a secret or a burnt secret. A power that pays X secrets
    * from its tree overrides it. */
  def costsSecret: Boolean = cost.secret > 0 || cost.secretBurnt > 0
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation]

  /** `build`, for a power that queues a procedure and must know whether
    * it would start. Every other power builds as `build` does. */
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef,
      starts: QueuedStarts): Either[OathViolation, Operation] =
    build(ready, player, source)

  /** Why the power cannot be used now, in the player's words, beyond
    * `usable`: for a power whose use only queues a procedure that would
    * not start. `None` by default. */
  def refusal(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef,
      starts: QueuedStarts): Option[String] = None

/** `useRestrictions` are the power-use restrictions the walker powers
  * declare (catalog batch 8, slice 10), so a phase power is judged by the
  * same rules a walker power is. */
final case class PhasePowers(powers: Vector[PhasePower],
    useRestrictions: Vector[PowerUseRestriction] = Vector.empty):
  def find(id: PowerId): Option[PhasePower] = powers.find(_.id == id)
object PhasePowers:
  val empty: PhasePowers = PhasePowers(Vector.empty)

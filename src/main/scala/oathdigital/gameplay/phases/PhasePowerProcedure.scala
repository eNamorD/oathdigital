package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{IndexedRuleSource, PowerAccess, RuleSourceIndex}
import oathdigital.gameplay.operations.Costs
import oathdigital.model.OathViolation._
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.model._

/** Use Power (rest-walker spec, Phase powers).
  *
  * {{{
  * Sequence(PayCost(cost, on the source card),   // when the cost is not free
  *   power.build(...),
  *   RecordPowerUse(timing, source, id))         // Wake and Rest only
  * }}}
  *
  * `usable` is the single usability function: the start gate, legal
  * controls, the `phasePowers` projection and the Rest auto-skip all ask it.
  * A power is usable when its source is accessible, its once-per-turn limit
  * (Wake and Rest) is unspent, its cost is payable (including the empty-card
  * rule) and its own `usable` agrees.
  *
  * Act powers have no once-each limit. A costed one is limited only because
  * its card holds the cost afterwards.
  */
object PhasePowerProcedure {
  final case class PowerSource(power: PhasePower, source: PowerSourceRef,
      ref: DecisionOptionRef)

  def timingOf(phase: Phase): Option[PowerTiming] = phase match {
    case Phase.Wake => Some(PowerTiming.Wake)
    case Phase.Act => Some(PowerTiming.Act)
    case Phase.Rest => Some(PowerTiming.Rest)
    case _ => None
  }

  def useRef(power: PhasePower, source: PowerSourceRef): PowerUseRef =
    PowerUseRef(power.timing, source, power.id)

  private def limited(power: PhasePower): Boolean =
    power.timing != PowerTiming.Act

  /** Sources of `power` that `player` can access, in index order. */
  def sources(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      power: PhasePower): Vector[(PowerSourceRef, DecisionOptionRef)] =
    sourcesFrom(RuleSourceIndex.enumerate(catalog, ready), ready, player, power)

  private def sourcesFrom(index: Vector[IndexedRuleSource], ready: ReadyGame,
      player: PlayerId, power: PhasePower)
      : Vector[(PowerSourceRef, DecisionOptionRef)] =
    index.filter(source => source.powerIds.contains(power.id) &&
      PowerAccess.accessible(source.source, source.face, ready, player))
      .flatMap(source => sourceRef(source.source))

  /** The cost is payable from `source`. A free cost is always payable. */
  private def payable(ready: ReadyGame, player: PlayerId, power: PhasePower,
      source: PowerSourceRef): Either[OathViolation, Unit] =
    if (power.cost == Cost.free) Right(())
    else source match {
      case PowerSourceRef.Card(card) => Costs.plan(ready, player,
        Location.OnCard(card), power.cost).map(_ => ())
      case other => Left(InvalidEventOrder(
        s"${power.id.value} cannot charge a cost to $other"))
    }

  def check(catalog: ExecutableCatalog, ready: ReadyGame, requester: PlayerId,
      power: PhasePower, source: DecisionOptionRef)
      : Either[OathViolation, PowerSourceRef] = {
    val current = ready.game.current
    val active = current.turn.activePlayer
    val phase = current.turn.phase
    for {
      _ <- Either.cond(current.result.isEmpty, (), GameEnded)
      _ <- Either.cond(requester == active, (), WrongPlayer(active, requester))
      _ <- Either.cond(current.walkerPending.isEmpty &&
        current.walkerProcedure.isEmpty, (),
        InvalidEventOrder("a procedure is already pending"))
      _ <- Either.cond(timingOf(phase).contains(power.timing), (),
        InvalidEventOrder(s"${power.id.value} is a ${power.timing} power " +
          s"and cannot be used in the ${phase.productPrefix} phase"))
      found <- sources(catalog, ready, active, power).collectFirst {
        case (found, `source`) => found
      }.toRight(InvalidEventOrder(s"${source.kind}/${source.wireId} is not " +
        s"an accessible source of ${power.id.value}"))
      _ <- Either.cond(!limited(power) ||
        !current.turn.usedPowers.contains(useRef(power, found)), (),
        PowerAlreadyUsed(useRef(power, found)))
      _ <- payable(ready, active, power, found)
      _ <- Either.cond(power.usable(ready, active, source), (),
        InvalidEventOrder(s"${power.id.value} has nothing to do from " +
          s"${source.kind}/${source.wireId}"))
    } yield found
  }

  def usable(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      powers: PhasePowers): Vector[PowerSource] = {
    val current = ready.game.current
    val available = current.result.isEmpty &&
      player == current.turn.activePlayer &&
      current.walkerPending.isEmpty && current.walkerProcedure.isEmpty
    if (!available) Vector.empty
    else {
      val index = RuleSourceIndex.enumerate(catalog, ready)
      powers.powers.flatMap { power =>
        if (!timingOf(current.turn.phase).contains(power.timing)) Vector.empty
        else sourcesFrom(index, ready, player, power).collect {
          case (found, ref)
              if (!limited(power) ||
                !current.turn.usedPowers.contains(useRef(power, found))) &&
                payable(ready, player, power, found).isRight &&
                power.usable(ready, player, ref) =>
            PowerSource(power, found, ref)
        }
      }
    }
  }

  def build(id: PowerId, powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    power <- find(id, powers)
    source <- single(id, args)
    found <- check(catalog, ready, player, power, source)
    tree <- power.build(ready, player, source)
  } yield assemble(catalog, power, player, found, tree)

  /** Resume skips the gate: the walker is pending and the use is not yet
    * recorded, so only the tree is rebuilt.
    */
  def rebuild(id: PowerId, powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    power <- find(id, powers)
    source <- single(id, args)
    found <- sourceOf(source)
    tree <- power.build(ready, player, source)
  } yield assemble(catalog, power, player, found, tree)

  private def assemble(catalog: ExecutableCatalog, power: PhasePower,
      player: PlayerId, source: PowerSourceRef, tree: Operation): Operation = {
    val payment: Vector[Operation] = source match {
      case PowerSourceRef.Card(card) if power.cost != Cost.free =>
        Vector(Costs.onCard(player, card, power.cost, catalog))
      case _ => Vector.empty
    }
    val record: Vector[Operation] =
      if (limited(power)) Vector(RecordPowerUse(useRef(power, source)))
      else Vector.empty
    Sequence(payment ++ Vector(tree) ++ record)
  }

  private def find(id: PowerId, powers: PhasePowers) = powers.find(id)
    .toRight(InvalidEventOrder(s"no phase power is registered for ${id.value}"))

  private def single(id: PowerId, args: Vector[DecisionOptionRef]) = args match {
    case Vector(source) => Right(source)
    case other => Left(InvalidEventOrder(s"using ${id.value} names exactly " +
      s"one source, got ${other.size}"))
  }

  private def sourceRef(source: RuleSourceRef)
      : Option[(PowerSourceRef, DecisionOptionRef)] = source match {
    case RuleSourceRef.SiteCard(_, id: DenizenId) =>
      Some(PowerSourceRef.Card(id) -> DecisionOptionRef.Denizen(id))
    case RuleSourceRef.Adviser(_, id: DenizenId) =>
      Some(PowerSourceRef.Card(id) -> DecisionOptionRef.Denizen(id))
    case RuleSourceRef.Relic(_, id) =>
      Some(PowerSourceRef.Card(id) -> DecisionOptionRef.Relic(id))
    case RuleSourceRef.Edifice(_, id) =>
      Some(PowerSourceRef.Card(id) -> DecisionOptionRef.Edifice(id))
    case RuleSourceRef.Banner(key) => Banner.fromKey(key).map(banner =>
      PowerSourceRef.Banner(banner) -> DecisionOptionRef.Banner(banner))
    case _ => None
  }

  private def sourceOf(source: DecisionOptionRef)
      : Either[OathViolation, PowerSourceRef] = source match {
    case DecisionOptionRef.Denizen(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Relic(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Edifice(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Banner(banner) => Right(PowerSourceRef.Banner(banner))
    case other => Left(InvalidEventOrder(
      s"${other.kind}/${other.wireId} is not a power source"))
  }
}

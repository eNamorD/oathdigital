package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{IndexedRuleSource, PowerAccess, RuleSourceIndex}
import oathdigital.gameplay.operations.{Costs, PowerUseRules}
import oathdigital.model.OathViolation._
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers, PowerUse,
  PowerUseRefusal, PowerUseRestriction, QueuedStarts}
import oathdigital.gameplay.walker.{QueuedStart, WalkerPowers}
import oathdigital.model._

/** Use Power (rest-walker spec, Phase powers).
  *
  * {{{
  * Sequence(PayCost(cost, on the source card),   // when the cost is not free
  *   power.build(...),
  *   RecordPowerUse(timing, source, id))         // Wake and Rest only
  * }}}
  *
  * The phase menus offer these powers, and each menu runs the start's own
  * check. A power is usable when its source is accessible, its once-per-turn
  * limit (Wake and Rest) is unspent, its cost is payable (including the
  * empty-card rule) and its own `usable` agrees.
  *
  * Act powers have no once-each limit. A costed one is limited only because
  * its card holds the cost afterwards.
  */
object PhasePowerProcedure:
  def timingOf(phase: Phase): Option[PowerTiming] = phase match
    case Phase.Wake => Some(PowerTiming.Wake)
    case Phase.Act => Some(PowerTiming.Act)
    case Phase.Rest => Some(PowerTiming.Rest)
    case _ => None

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
    if power.cost == Cost.free then Right(())
    else source match
      case PowerSourceRef.Card(card) => Costs.plan(ready, player,
        Location.OnCard(card), power.cost).map(_ => ())
      case other => Left(InvalidEventOrder(
        s"${power.id.value} cannot charge a cost to $other"))

  /** The power-use refusals `player`'s use of `power` from `source` meets
    * (catalog batch 8, slice 10). The phase menu is never inside a
    * Campaign, so every refusal may be asked of its ruler. */
  def refusals(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId, power: PhasePower, source: PowerSourceRef,
      restrictions: Vector[PowerUseRestriction])
      : Vector[PowerUseRefusal] =
    if restrictions.isEmpty then Vector.empty
    else
      val card = source match
        case PowerSourceRef.Card(card) => Some(card)
        case _ => None
      val persistent = catalog.printedPower(power.id).exists(_.persistent)
      PowerUseRules.refusals(ready, PowerUse(player, power.id, card,
        persistent, power.costsSecret,
        places = power.cost.favor > 0 || power.cost.secret > 0),
        restrictions)

  def check(catalog: ExecutableCatalog, ready: ReadyGame, requester: PlayerId,
      power: PhasePower, source: DecisionOptionRef,
      restrictions: Vector[PowerUseRestriction] = Vector.empty,
      starts: QueuedStarts = QueuedStarts.unchecked)
      : Either[OathViolation, PowerSourceRef] =
    val current = ready.game.current
    val active = current.turn.activePlayer
    val phase = current.turn.phase
    for
      _ <- Either.cond(current.result.isEmpty, (), GameEnded)
      _ <- Either.cond(requester == active, (), WrongPlayer(active, requester))
      _ <- Either.cond(current.walkerPending.isEmpty &&
        current.walkerProcedure.isEmpty, (),
        InvalidEventOrder("a procedure is already pending"))
      _ <- Either.cond(timingOf(phase).contains(power.timing), (),
        InvalidEventOrder(s"${power.id.value} is a ${power.timing} power " +
          s"and cannot be used in the ${phase.productPrefix} phase"))
      _ <- Either.cond(!power.forced, (), InvalidEventOrder(
        s"${power.id.value} runs on its own at the start of its phase"))
      found <- sources(catalog, ready, active, power).collectFirst {
        case (found, `source`) => found
      }.toRight(InvalidEventOrder(s"${source.kind}/${source.wireId} is not " +
        s"an accessible source of ${power.id.value}"))
      _ <- refusals(catalog, ready, active, power, found, restrictions)
        .headOption.map(refused => OathViolation.StartUnavailable(
          refused.reason.detail)).toLeft(())
      _ <- Either.cond(!limited(power) ||
        !current.turn.usedPowers.contains(useRef(power, found)), (),
        PowerAlreadyUsed(useRef(power, found)))
      _ <- payable(ready, active, power, found)
      _ <- Either.cond(power.usable(ready, active, source), (),
        InvalidEventOrder(s"${power.id.value} has nothing to do from " +
          s"${source.kind}/${source.wireId}"))
      _ <- power.refusal(ready, active, source, starts)
        .toLeft(()).left.map(OathViolation.StartUnavailable(_))
    yield found

  def build(id: PowerId, powers: PhasePowers, walkerPowers: WalkerPowers)(
      catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    val starts = startsFor(catalog, powers, walkerPowers)
    for
      power <- find(id, powers)
      source <- single(id, args)
      found <- check(catalog, ready, player, power, source,
        powers.useRestrictions, starts)
      tree <- power.build(ready, player, source, starts)
    yield assemble(catalog, power, player, found, tree)

  /** Resume skips the gate: the walker is pending and the use is not yet
    * recorded, so only the tree is rebuilt.
    */
  def rebuild(id: PowerId, powers: PhasePowers, walkerPowers: WalkerPowers)(
      catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    val starts = startsFor(catalog, powers, walkerPowers)
    for
      power <- find(id, powers)
      source <- single(id, args)
      found <- sourceOf(source)
      tree <- power.build(ready, player, source, starts)
    yield assemble(catalog, power, player, found, tree)

  /** The driver's start check, for a power that queues a procedure. One
    * build asks it of the same state again and again: the use's check, then
    * every walk of the tree, which resolves the power's question once for
    * the restriction pass, once as it walks and again on each read of the
    * park. A start check is a pure function of the state its start runs
    * on, which names the entry, so each answer is kept for the life of the
    * tree, keyed by that state by value, as `SearchMemo` keys a state. Two
    * states that differ only in the open walk share it. */
  private def startsFor(catalog: ExecutableCatalog, phasePowers: PhasePowers,
      walkerPowers: WalkerPowers): QueuedStarts =
    val known = new java.util.concurrent.ConcurrentHashMap[ReadyGame,
      Option[OathViolation]]
    (ready, entry) =>
      val key = QueuedStart.startingFrom(ready, entry)
      Option(known.get(key)).getOrElse:
        val found = QueuedStart.refusal(catalog, ready, entry, walkerPowers,
          phasePowers)
        known.put(key, found)
        found

  private def assemble(catalog: ExecutableCatalog, power: PhasePower,
      player: PlayerId, source: PowerSourceRef, tree: Operation): Operation =
    val payment: Vector[Operation] = source match
      case PowerSourceRef.Card(card) if power.cost != Cost.free =>
        Vector(Costs.onCard(player, card, power.cost, catalog))
      case _ => Vector.empty
    val record: Vector[Operation] =
      if limited(power) then Vector(RecordPowerUse(useRef(power, source)))
      else Vector.empty
    Sequence(payment ++ Vector(tree) ++ record)

  private def find(id: PowerId, powers: PhasePowers) = powers.find(id)
    .toRight(InvalidEventOrder(s"no phase power is registered for ${id.value}"))

  private def single(id: PowerId, args: Vector[DecisionOptionRef]) = args match
    case Vector(source) => Right(source)
    case other => Left(InvalidEventOrder(s"using ${id.value} names exactly " +
      s"one source, got ${other.size}"))

  private def sourceRef(source: RuleSourceRef)
      : Option[(PowerSourceRef, DecisionOptionRef)] = source match
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
    case RuleSourceRef.Site(id) =>
      Some(PowerSourceRef.Site(id) -> DecisionOptionRef.Site(id))
    case _ => None

  private def sourceOf(source: DecisionOptionRef)
      : Either[OathViolation, PowerSourceRef] = source match
    case DecisionOptionRef.Denizen(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Relic(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Edifice(id) => Right(PowerSourceRef.Card(id))
    case DecisionOptionRef.Banner(banner) => Right(PowerSourceRef.Banner(banner))
    case DecisionOptionRef.Site(id) => Right(PowerSourceRef.Site(id))
    case other => Left(InvalidEventOrder(
      s"${other.kind}/${other.wireId} is not a power source"))

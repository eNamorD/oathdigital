package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{IndexedRuleSource, OathViolation, ReadyGame,
  RuleSourceAccess, RuleSourceFace, RuleSourceIndex, RuleSourceRef}
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations.{Operation, RecordPowerUse, Sequence}
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.model._

/** Use Power (rest-walker spec, Phase powers).
  *
  * {{{
  * Sequence(power.build(...), RecordPowerUse(timing, Card(source), id))
  * }}}
  *
  * `usable` is the single usability function: the start gate, legal
  * controls, the `phasePowers` projection and the Rest auto-skip all ask it.
  */
object PhasePowerProcedure {
  final case class PowerSource(power: PhasePower, card: CardId,
      ref: DecisionOptionRef)

  def timingOf(phase: Phase): Option[PowerTiming] = phase match {
    case Phase.Wake => Some(PowerTiming.Wake)
    case Phase.Act => Some(PowerTiming.Act)
    case Phase.Rest => Some(PowerTiming.Rest)
    case _ => None
  }

  def useRef(power: PhasePower, card: CardId): PowerUseRef =
    PowerUseRef(power.timing, PowerSourceRef.Card(card), power.id)

  /** Cards `player` can access that print `power`, in index order. */
  def sources(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      power: PhasePower): Vector[(CardId, DecisionOptionRef)] =
    RuleSourceIndex.enumerate(catalog, ready).filter(source =>
      source.powerIds.contains(power.id) && accessible(source, ready, player))
      .flatMap(source => sourceRef(source.source))

  /** The reviewed access rule, plus faceup site cards and site relics at
    * sites `player` rules (Corrections 6).
    */
  private def accessible(source: IndexedRuleSource, ready: ReadyGame,
      player: PlayerId): Boolean =
    RuleSourceAccess.accessible(source.source, source.face, ready, player,
      facedownAdviser = false) || (source.source match {
      case RuleSourceRef.SiteCard(site, _) =>
        source.face == RuleSourceFace.FaceUp && rules(ready, player, site)
      case RuleSourceRef.SiteRelic(site, _) =>
        source.face == RuleSourceFace.FaceUp && rules(ready, player, site)
      case _ => false
    })

  private def rules(ready: ReadyGame, player: PlayerId, site: SiteId): Boolean = {
    val current = ready.game.current
    current.map.sites.get(site).exists(state => SiteRule.ruler(state.forces,
      current.players).toOption.contains(SiteRuler.Player(player)))
  }

  def check(catalog: ExecutableCatalog, ready: ReadyGame, requester: PlayerId,
      power: PhasePower, source: DecisionOptionRef)
      : Either[OathViolation, CardId] = {
    val current = ready.game.current
    val active = current.turn.activePlayer
    val phase = current.turn.phase
    for {
      _ <- Either.cond(current.result.isEmpty, (), GameEnded)
      _ <- Either.cond(requester == active, (), WrongPlayer(active, requester))
      _ <- Either.cond(current.walkerPending.isEmpty &&
        current.walkerProcedure.isEmpty && current.pending.isEmpty, (),
        InvalidEventOrder("a procedure is already pending"))
      _ <- Either.cond(timingOf(phase).contains(power.timing), (),
        InvalidEventOrder(s"${power.id.value} is a ${power.timing} power " +
          s"and cannot be used in the ${phase.productPrefix} phase"))
      card <- sources(catalog, ready, active, power).collectFirst {
        case (card, `source`) => card
      }.toRight(InvalidEventOrder(s"${source.kind}/${source.wireId} is not " +
        s"an accessible source of ${power.id.value}"))
      _ <- Either.cond(!current.turn.usedPowers.contains(useRef(power, card)),
        (), PowerAlreadyUsed(useRef(power, card)))
      _ <- Either.cond(power.usable(ready, active, source), (),
        InvalidEventOrder(s"${power.id.value} has nothing to do from " +
          s"${source.kind}/${source.wireId}"))
    } yield card
  }

  def usable(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      powers: PhasePowers): Vector[PowerSource] =
    powers.powers.flatMap(power => sources(catalog, ready, player, power)
      .collect { case (card, ref)
          if check(catalog, ready, player, power, ref).isRight =>
        PowerSource(power, card, ref) })

  def build(id: PowerId, powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    power <- find(id, powers)
    source <- single(id, args)
    card <- check(catalog, ready, player, power, source)
    tree <- power.build(ready, player, source)
  } yield Sequence(Vector(tree, RecordPowerUse(useRef(power, card))))

  /** Resume skips the gate: the walker is pending and the use is not yet
    * recorded, so only the tree is rebuilt.
    */
  def rebuild(id: PowerId, powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    power <- find(id, powers)
    source <- single(id, args)
    card <- sourceCard(source)
    tree <- power.build(ready, player, source)
  } yield Sequence(Vector(tree, RecordPowerUse(useRef(power, card))))

  private def find(id: PowerId, powers: PhasePowers) = powers.find(id)
    .toRight(InvalidEventOrder(s"no phase power is registered for ${id.value}"))

  private def single(id: PowerId, args: Vector[DecisionOptionRef]) = args match {
    case Vector(source) => Right(source)
    case other => Left(InvalidEventOrder(s"using ${id.value} names exactly " +
      s"one source, got ${other.size}"))
  }

  private def sourceRef(source: RuleSourceRef): Option[(CardId, DecisionOptionRef)] =
    source match {
      case RuleSourceRef.SiteCard(_, id: DenizenId) =>
        Some(id -> DecisionOptionRef.Denizen(id))
      case RuleSourceRef.Adviser(_, id: DenizenId) =>
        Some(id -> DecisionOptionRef.Denizen(id))
      case RuleSourceRef.Relic(_, id) => Some(id -> DecisionOptionRef.Relic(id))
      case RuleSourceRef.SiteRelic(_, id) => Some(id -> DecisionOptionRef.Relic(id))
      case _ => None
    }

  private def sourceCard(source: DecisionOptionRef): Either[OathViolation, CardId] =
    source match {
      case DecisionOptionRef.Denizen(id) => Right(id)
      case DecisionOptionRef.Relic(id) => Right(id)
      case other => Left(InvalidEventOrder(
        s"${other.kind}/${other.wireId} is not a power source card"))
    }
}

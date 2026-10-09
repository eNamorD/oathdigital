package oathdigital.gameplay.powerresolver

import oathdigital.model._

/** The modifier decision's vocabulary (modifier walker design): the decision
  * an action asks first, one modifier per pass, and the picks read back out
  * of its answers. The picks are the selection: nothing else records them.
  *
  * A pick answers with the modifier's card. "Start" answers with the
  * [[start]] button. The decision is named for its action, so every
  * procedure that shares a modifier window shares its id.
  */
object ModifierPicks:
  /** The answer that ends the decision and starts the action. */
  val start: DecisionOptionRef.Button = DecisionOptionRef.Button("start")

  private val suffix = ".modifiers"

  /** `search.modifiers` for `SearchModifierSelection`. */
  def decisionId(window: PowerWindow): String =
    window.associatedMajorAction.fold(window.key)(_.key) + suffix

  def isDecision(decisionId: String): Boolean = decisionId.endsWith(suffix)

  /** What a modifier's option names it by: its card. */
  def cardRef(card: CardId): Option[DecisionOptionRef] = card match
    case id: DenizenId => Some(DecisionOptionRef.Denizen(id))
    case id: RelicId => Some(DecisionOptionRef.Relic(id))
    case id: EdificeId => Some(DecisionOptionRef.Edifice(id))
    case _ => None

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Relic(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

  /** Each pick as its decision id and the card it names, in pick order. */
  private def answers(answered: Vector[Answered])
      : Vector[(String, DecisionOptionRef)] = answered.collect {
    case Answered(id, DecisionAnswer.ChooseOneAnswer(ref), _)
        if isDecision(id) && ref != start => id -> ref
  }

  /** The cards picked, in pick order. */
  def cards(answered: Vector[Answered]): Vector[CardId] =
    answers(answered).flatMap((_, ref) => cardOf(ref))

  private def pickable(power: ContributingPower, ref: DecisionOptionRef)
      : Boolean = power.resolution == PowerResolution.PlayerSelected &&
    power.selectionCard.flatMap(cardRef).contains(ref)

  /** The power in `pool` an answer naming `ref` picks at `window`. */
  def powerFor(pool: Vector[ContributingPower], window: PowerWindow,
      ref: DecisionOptionRef): Option[ContributingPower] =
    pool.find(power => pickable(power, ref) &&
      power.selectionWindows(window))

  /** The powers of `pool` the answers pick, in pick order. */
  def picks(pool: Vector[ContributingPower], answered: Vector[Answered])
      : Vector[PowerId] = answers(answered).flatMap((id, ref) =>
    pool.find(power => pickable(power, ref) &&
      power.selectionWindows.exists(decisionId(_) == id)).map(_.id))

  /** Whether "Start" has been answered at `decisionId`. */
  def started(answered: Vector[Answered], decisionId: String): Boolean =
    answered.exists(answer => answer.decisionId == decisionId &&
      answer.answer == DecisionAnswer.ChooseOneAnswer(start))

  /** Whether no more picks can follow: no modifier decision was answered,
    * "Start" was, or a later decision was. A walk whose every modifier is
    * picked starts by itself, so "Start" is not always answered. */
  def settled(answered: Vector[Answered]): Boolean =
    val asked = answered.filter(answer => isDecision(answer.decisionId))
    asked.isEmpty ||
      asked.exists(_.answer == DecisionAnswer.ChooseOneAnswer(start)) ||
      answered.lastOption.exists(answer => !isDecision(answer.decisionId))

  /** Whether `card` has been picked at `window`. */
  def isPicked(answered: Vector[Answered], window: PowerWindow,
      card: CardId): Boolean = cardRef(card).exists(ref =>
    answered.exists(answer => answer.decisionId == decisionId(window) &&
      answer.answer == DecisionAnswer.ChooseOneAnswer(ref)))

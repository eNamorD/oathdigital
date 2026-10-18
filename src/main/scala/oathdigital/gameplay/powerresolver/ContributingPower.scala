package oathdigital.gameplay.powerresolver

import oathdigital.model.{Answered, CampaignPlanOffer, CardId, CoreOperation, Decide, DecisionOptionRef, NoteKey, OathViolation, OfferedPlan, OfferedReroll, Operation, OperationRestriction, OptionReason, PlayerId, PowerId, LogNote, PowerResolution, PowerSourceRef, PowerWindow, ProcedureRef, ReadyGame, RerollOffer, RuleSourceRef}

/** Everything a contribution may read at the node it hooks. Carries no
  * mutable state and no catalog -- a power looks up whatever else it needs
  * (catalog data, other game state) through `state`.
  */
final case class PowerCtx(
    state: ReadyGame,
    /** The player whose procedure is running: the walk's actor
      * (`CurrentGameState.walkActor`), the traveller, the recoverer, the
      * attacker. Off turn for a queued walk whose entry names another
      * player. Not a parked decision's owner, which a contribution hooked
      * on a `Decide` reads from `operation`.
      */
    actor: PlayerId,
    source: RuleSourceRef,
    window: PowerWindow,
    nodePath: Vector[String],
    /** Exact windowed operation a contribution is being collected for. */
    operation: Operation,
    /** The procedure the window is walked for, when it is known: the
      * procedure of the parked position a command resumes, or the one a
      * modifier is being selected for. `None` for the command that starts a
      * procedure, which has not recorded one yet.
      */
    procedure: Option[ProcedureRef] = None,
    /** The decisions answered so far in the running action, oldest first. A
      * contribution that must know what the player chose earlier (which battle
      * plans were used) reads them here. Empty when the window is gathered
      * outside a walk.
      */
    answered: Vector[Answered] = Vector.empty
)

/** The three ways a power may speak at a hooked node (spec decision 9). A
  * `Transform` rewrites the hooked node's children vector -- never the whole
  * tree, whole-action restructure is out of scope. A `Restriction` checks the
  * whole action tree, since a Vow-of-Peace-style power rejects an action
  * wholesale rather than editing it. An `OptionRestriction` forbids single
  * options of the `Decide` it hooks.
  */
sealed trait Contribution extends Product with Serializable
object Contribution:
  /** The hide hook of a restriction that writes no Game Log line. */
  val silent: (PowerCtx, DecisionOptionRef) => Option[LogNote] = (_, _) => None

/** Rewrites the hooked node's children. Covers must-effects (insert ops),
  * cost changes (modify the pay ops), and reordering (roll order).
  *
  * `fn` MUST be a pure function of the state it is handed: the walker derives
  * the tree and re-folds every window on every command (S1), so a fold that
  * differed between two commands would make a recorded park position address
  * a different node on resume. This is the same invariant `Branch.select`
  * already carries.
  *
  * `phase` says when it folds among the window's transforms.
  */
final case class Transform(
    fn: (PowerCtx, Vector[Operation]) => Vector[Operation],
    phase: FoldPhase = FoldPhase.Normal
) extends Contribution

/** When a transform folds at its window (operation rewrite design, ruling
  * 9). Every `Late` transform folds after every `Normal` one, and power
  * priority orders the transforms within a phase. A transform that must see
  * what the window's other transforms added folds `Late`.
  */
enum FoldPhase:
  case Normal, Late

/** Validates the whole action tree root, returning a violation to reject the
  * action wholesale. Covers cannot-effects. `note` is the hover reason of an
  * option the restriction look-ahead hides because of it; it writes no Game
  * Log line (travel destination design, ruling 10). `waiver` is set when the
  * power restricts its ruler's enemies, whose ruler may consent to ignore
  * it (consent design, "Powers declare waivability").
  */
final case class Restriction(
    fn: (PowerCtx, Operation) => Option[OathViolation],
    note: (PowerCtx, DecisionOptionRef) => Option[LogNote] = Contribution.silent,
    waiver: Option[Waiver] = None
) extends Contribution

/** How a restriction on enemies may be waived (consent design, rulings 1 and
  * 4). `granter` is the player who may consent: the power's ruler, or `None`
  * when bandits, the Empire or nobody rules it, since none of them consents.
  * `source` names the power in the log. `note` is the hover reason of an
  * option the player may ask consent for.
  */
final case class Waiver(
    granter: ReadyGame => Option[PlayerId],
    source: PowerSourceRef,
    note: (PowerCtx, DecisionOptionRef) => LogNote)

/** Forbids one option of the `Decide` the window hooks. Called once per offered
  * option in the window fold, before the query is parked, so a forbidden
  * option is absent from what the projector offers, from what `accepts`
  * validates and from what a simulation answers. Covers cannot-effects that
  * name a choice rather than the whole action. `note` is the hover reason of
  * each option it hides; it writes no Game Log line (travel destination
  * design, ruling 10). `waiver` is set when the power restricts its ruler's
  * enemies: a grant remembered for the decision and the option lifts it. An
  * option it alone forbids may be asked about at a choose-many (consent
  * design, slice 2), and at a choose-one hooked by its own window when the
  * search finds a grant would open it (slice 4).
  */
final case class OptionRestriction(
    fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation],
    note: (PowerCtx, DecisionOptionRef) => Option[LogNote] = Contribution.silent,
    waiver: Option[Waiver] = None
) extends Contribution

/** Offers one option to the node its window hooks, when that node is an
  * [[OfferHost]] (a Campaign's battle-plan window). `plan` says whether the
  * offer stands now and what it is; it reads the context like any contribution
  * and is asked again at every fold, so it must be a pure function of state.
  * Whether the user can pay is not its business: the host dry-runs the plan.
  */
final case class Offer(
    plan: PowerCtx => Option[CampaignPlanOffer]
) extends Contribution

/** Offers one reroll to the node its window hooks, when that node is a
  * [[RerollHost]] (a roll step's reroll choice). Like [[Offer]], it is asked
  * again at every fold, so it must be a pure function of state. Whether the
  * roller can pay is not its business: the host dry-runs the payment.
  */
final case class OfferReroll(
    offer: PowerCtx => Option[RerollOffer]
) extends Contribution

/** A modifier's own verdict on its option in the modifier decision, given
  * the picks so far (modifier walker design, ruling 5): unavailable with a
  * reason, or available with a warning. */
enum OfferReason:
  case Unavailable(reason: String)
  case Warned(reason: String)

/** A windowed node that turns what the powers offer into the children it walks.
  * The walker gathers the window's [[Offer]]s, asks each for its plan, and hands
  * the plans to `expand` in the deterministic power order.
  *
  * `expand` runs at every fold of the node, so it must be a pure function of its
  * arguments. A walk that resumes inside the node (`Pass.resuming`) is handed
  * the same offers again against a later state, and must give the node the same
  * shape as when it parked, even if nothing is left to offer.
  */
trait OfferHost extends Operation:
  def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass): Vector[Operation]

object OfferHost:
  /** What the walker tells a host about this fold.
    *
    * `applies` dry-runs an operation through the same windows the walk uses.
    * It reports the operations the walk would record, or why it could not run.
    * An operation that parks on a decision is accepted when everything before
    * the decision ran, and reports what ran.
    */
  final case class Pass(state: ReadyGame, answered: Vector[Answered],
      resuming: Boolean,
      applies: Operation => Either[OathViolation, Vector[CoreOperation]])

/** A windowed node that turns the rerolls the powers offer into the
  * children it walks: the reroll counterpart of [[OfferHost]], with the same
  * contract for `expand`.
  */
trait RerollHost extends Operation:
  def expand(offers: Vector[OfferedReroll], pass: OfferHost.Pass)
      : Vector[Operation]

/** One object per power (spec decision 8). No engine code lives in a power --
  * only the windows it hooks and the contributions it offers there.
  *
  * `resolution` (Task 4) reuses the `PowerResolution` vocabulary the legacy
  * `PowerHandler` already carries: `Automatic` powers are offered to every
  * walker command regardless of what the player chose; `PlayerSelected`
  * powers are offered only when their `id` appears in the command's
  * `modifiers` (see `OathRules.walkerPowers`). Defaults to `Automatic` so a
  * power that never expects to be player-chosen (the common case for a
  * "must"/"cannot" rule) declares nothing extra.
  */
trait ContributingPower extends NotingPower:
  def id: PowerId
  def source: RuleSourceRef
  def priority: Int = 0
  /** The windows this power hooks and what it contributes at each. It must
    * be pure and give the same result on every call for a given instance:
    * `PowerWindowIndex` calls it once per command and reuses the result.
    */
  def contributions: Map[PowerWindow, Vector[Contribution]]
  def applicable(ctx: PowerCtx): Boolean = true
  /** Operation restrictions that hold wherever this power is offered: every
    * walker step checks them, and a composite is checked whole before it is
    * split (global operation restrictions design). Unlike a `Restriction`, it
    * hooks no window. None by default. */
  def operationRestrictions: Vector[OperationRestriction] = Vector.empty
  /** The rules this power holds over other powers' uses (catalog batch 8,
    * slice 10). The engine reads them without naming them. None by
    * default. */
  def powerUseRestrictions: Vector[PowerUseRestriction] = Vector.empty
  /** This power's use at `ctx`, as the power-use restrictions judge it.
    * `None` for a power nobody uses: a game rule, or a lasting limit
    * printed under a phase power's id. A player-selected power is judged
    * at its pick. An automatic one is judged where its effect runs, in
    * part 2's `AutomaticUse` node, and Keep where its window folds. */
  def use(ctx: PowerCtx): Option[PowerUse] = None
  /** Whether this selected power discards its own card before its action
    * ends, so the active-modifier rule must not hold that card in play. No by
    * default. */
  def discardsItself: Boolean = false
  /** What selecting this power pays: the operations the modifier decision
    * runs when the player picks it. Free by default.
    */
  def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] = Vector.empty
  /** The card a player-selected power is printed on: its option in the
    * modifier decision names this card. None for any other power. */
  def selectionCard: Option[CardId] = None
  /** The modifier-selection windows at which the player may pick this
    * power. Empty for any power that is not player-selected. */
  def selectionWindows: Set[PowerWindow] = Set.empty
  /** What the modifier decision asks once the player has picked this power
    * and paid for it, in the same pass, before the next pass (catalog
    * batch 8, "An as-if pawn site for Trade"). Nothing by default. */
  def selectionQuestions(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] = Vector.empty
  /** Why this power's option in the modifier decision is unavailable or
    * deserves a warning, given the picks so far in `ctx.answered`. A rule
    * that one modifier states about another lives on the card that states
    * it. None by default. */
  def offerReason(ctx: PowerCtx): Option[OfferReason] = None
  /** Decision 10(b)'s named ignore. Receives the whole candidate rather than
    * its id, because a rule of the shape "ignore other modifiers of this
    * action" needs the candidate's classification, and that lives on the
    * windows it hooks (`PowerWindow.associatedMajorAction`) rather than in
    * its `PowerId`.
    */
  def shouldIgnore(other: ContributingPower): Boolean = false
  /** As `shouldIgnore`, decided with the context this power is gathered in,
    * so it can ignore `other` for the node at hand and not for another.
    * The collector calls this one.
    */
  def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    shouldIgnore(other)
  def resolution: PowerResolution = PowerResolution.Automatic
  /** Why `option` of `decide` deserves a second look while this power is
    * offered, for the projector to show beside it. The option stays
    * choosable, and the walk never reads this. */
  def warning(decide: Decide, option: DecisionOptionRef): Option[OptionReason] =
    None

  /** The follow-up this power adds after a walker leaf in which advisers
    * arrived (catalog batch 5, "The arrival pass"). None by default. */
  def onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp] = None

  /** Who takes `burnt` instead of the bank, when this power claims it
    * (catalog batch 8, "A burn's actor"). None by default. */
  def takesBurn(ready: ReadyGame, burnt: Burnt): Option[BurnTake] = None

object ContributingPower:

  /** The single deterministic ordering used everywhere contributions are
    * chained (spec decision 10c).
    */
  def sortKey(power: ContributingPower): (Int, String, String) =
    (power.priority, power.source.stableKey, power.id.value)

/** The Game Log lines a power can write (power log lines design). A note it
  * builds must come from one of these keys, and a phase power's
  * `NoteKey.Used` line replaces "Used {card}". Contributing and phase powers
  * share this, so a power that is both declares its keys once.
  */
trait NotingPower:
  def noteKeys: Vector[NoteKey] = Vector.empty
  /** The name this power's notes are written under, when it is not the name
    * of its source. A site power four sites share names itself, so its line
    * reads "River: ..." wherever it was used. */
  def noteSource: Option[String] = None
  /** The ids of this power's decisions whose answer its notes tell, so the
    * log posts no "Chose ..." line for them. List a decision when every line
    * the power can write after it names the answer, "nothing to kill"
    * lines included. Leave out a decision whose answer can leave no line
    * (a declined offer) or whose line does not name it (a rearrangement),
    * and one with a per-game id, which this exact-id set cannot match. */
  def narratedDecisions: Set[String] = Set.empty

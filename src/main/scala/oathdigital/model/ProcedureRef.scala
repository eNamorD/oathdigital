package oathdigital.model

/** Stable persisted identity of a procedure whose tree is rebuilt on each
  * walker command. The operation tree itself remains command-local.
  *
  * Three families share this one hierarchy: `key` is unique across all of
  * them, so a bare key resolves to at most one member, and `family` is what
  * the wire and the journal carry alongside it so a decoder can reject a
  * reference read back under the wrong family.
  */
sealed trait ProcedureRef extends Product with Serializable:
  def key: String
  def family: String

/** A procedure a queue entry can start: an action or a phase transition.
  * Both members below extend this; a [[TriggeredProcedureRef]] does not,
  * so no queue entry, and no menu option, can name one.
  */
sealed trait StartableRef extends ProcedureRef

/** What a player spends as their action, in any phase. The action boundary
  * runs after one of these completes, whatever phase it ran in -- see
  * `OathDriver`.
  */
sealed trait ActionRef extends StartableRef { final def family = "action" }

/** Whether an Act action is a major or a minor one. The rules print the two
  * lists separately, so the split is a rules fact and is named here rather
  * than left to the order `ActOffers` happens to list its offers in. The Act
  * menu's bands are read off it (decision-id vocabulary decision, item 1).
  */
enum ActionClass:
  case Major, Minor

/** A player-commanded change of phase. These are procedures because a phase
  * change is a state write, but they are not actions: no action boundary
  * runs after one, whichever phase it lands in.
  */
enum PhaseTransitionRef(val key: String) extends StartableRef:
  final def family: String = "phase-transition"

  /** Batch-1 Task 7 moved End Wake here from `ActionRef`: the action
    * boundary follows only a completed `ActionRef`, in whatever phase it
    * ran. End Wake is a phase transition, not something a player spends a
    * turn on, so it runs none.
    */
  case EndWake extends PhaseTransitionRef("end-wake")
  /** Leaves Act for Rest (rest-walker spec, Rest procedure). */
  case BeginRest extends PhaseTransitionRef("begin-rest")
  /** Cleans up, refreshes Supply and hands the turn over. */
  case FinishRest extends PhaseTransitionRef("finish-rest")

/** A procedure the engine starts on its own. No command can name one --
  * `StartableRef` excludes this family -- and no boundary runs after one.
  * Oathkeeper (Task 7) is the first: the action boundary starts it.
  */
enum TriggeredProcedureRef(val key: String) extends ProcedureRef:
  final def family: String = "triggered"

  /** Every change of the Oathkeeper title holder at an action boundary. */
  case Oathkeeper extends TriggeredProcedureRef("oathkeeper")
  /** Runs once, right after `GameStarted` evolves (2026-09-21 Chronicle
    * design, slice 2, "Setup on the walker"). No client command starts it. */
  case Setup extends TriggeredProcedureRef("setup")
  /** The forced Wake powers of the player whose Wake begins (catalog batch 2,
    * N8). The turn boundary or the end of Setup starts it; nothing else may
    * run until it ends. */
  case ForcedWake extends TriggeredProcedureRef("forced-wake")
  /** Asks a follow-up entry's actor: the action, or Skip (walker-driven
    * game loop design, ruling 6). The driver starts it when it dequeues an
    * `Offer` entry. */
  case FollowUpOffer extends TriggeredProcedureRef("follow-up-offer")
  /** The Wake phase's menu (walker-driven game loop design, "The menus").
    * The driver starts it when the queue is empty in Wake and more than End
    * Wake is available. */
  case WakeMenu extends TriggeredProcedureRef("wake-menu")
  /** The Rest phase's menu, as the Wake menu is Wake's. */
  case RestMenu extends TriggeredProcedureRef("rest-menu")
  /** The Act phase's menu, as the Wake menu is Wake's. The driver starts it
    * whenever the queue is empty in Act; it always asks. */
  case ActMenu extends TriggeredProcedureRef("act-menu")

object ActionRef:
  case object Search extends ActionRef { val key = "search" }
  case object PlayFacedownAdviser extends ActionRef:
    val key = "play-facedown-adviser"
  case object Recover extends ActionRef { val key = "recover" }
  case object Forge extends ActionRef { val key = "forge" }
  case object Travel extends ActionRef { val key = "travel" }
  case object TakeWealth extends ActionRef { val key = "take-wealth" }
  case object Muster extends ActionRef { val key = "muster" }
  case object Trade extends ActionRef { val key = "trade" }
  case object Challenge extends ActionRef { val key = "challenge" }
  case object PlaceBannerResource extends ActionRef:
    val key = "place-banner-resource"
  case object Negotiation extends ActionRef { val key = "negotiation" }
  case object Campaign extends ActionRef { val key = "campaign" }

  /** The three minor actions that were legacy commands (walker-driven game
    * loop design, slice 3). */
  case object PeekSiteRelics extends ActionRef:
    val key = "peek-site-relics"
  case object RevealOwnedRelic extends ActionRef:
    val key = "reveal-owned-relic"
  case object MoveWarbands extends ActionRef { val key = "move-warbands" }

  /** Uses one phase power. Parameterized, so `all` cannot list it; the key
    * parses directly.
    */
  final case class UsePower(power: PowerId) extends ActionRef:
    val key: String = s"${UsePower.Prefix}${power.value}"
  object UsePower { private[model] val Prefix = "use-power:" }

  /** Every finite action reference. `TakeWealth` names the Wake phase's
    * action.
    */
  val all: Vector[ActionRef] = Vector(Search, PlayFacedownAdviser,
    Recover, Forge, Travel, TakeWealth, Muster, Trade, Challenge,
    PlaceBannerResource, Negotiation, Campaign, PeekSiteRelics,
    RevealOwnedRelic, MoveWarbands)

  def usePower(key: String): Option[UsePower] =
    Option.when(key.startsWith(UsePower.Prefix))(key.stripPrefix(UsePower.Prefix))
      .flatMap(PowerId.fromValue).map(UsePower(_))

  def fromKey(key: String): Option[ActionRef] =
    all.find(_.key == key).orElse(usePower(key))

object PhaseTransitionRef:
  val all: Vector[PhaseTransitionRef] = Vector(EndWake, BeginRest, FinishRest)

object TriggeredProcedureRef:
  val all: Vector[TriggeredProcedureRef] = Vector(Oathkeeper, Setup,
    ForcedWake, FollowUpOffer, WakeMenu, RestMenu, ActMenu)

object StartableRef:
  /** The finite references. The parameterized use-power reference is not
    * listed.
    */
  val all: Vector[StartableRef] = ActionRef.all ++ PhaseTransitionRef.all

object ProcedureRef:
  val all: Vector[ProcedureRef] = StartableRef.all ++ TriggeredProcedureRef.all

  /** Resolves a reference from the wire/journal spelling of both its family
    * and its key -- rejecting a reference read back under the wrong family
    * (an End Wake key spelled `"action"`) or an unknown family, rather than
    * resolving on the key alone and trusting the family tag as decoration.
    * Finite references are listed in `all`; parameterized use-power keys
    * parse directly.
    */
  def fromFamilyKey(family: String, key: String): Option[ProcedureRef] =
    all.find(ref => ref.family == family && ref.key == key)
      .orElse(ActionRef.usePower(key).filter(_.family == family))

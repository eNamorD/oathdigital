package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignPlanApplication, CampaignPlans}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.model._

/** A persistent rule of a faceup adviser that adds a cost to every battle plan
  * its holder's enemy chooses (Gleaming Armor, Insect Swarm).
  *
  * While the holder is in a Campaign, as the attacker or as a player defender,
  * every plan the opposing side chooses costs `cost` more, paid like the plan's
  * own cost: onto the plan's source card, and settled at once outside its
  * user's turn. The title has no card, so its plan pays `onTitle` instead. The
  * added cost is part of the plan's application, so a plan the user cannot
  * afford with it is not offered, and the option's price includes it. Bandits
  * are enemies too, but they hold nothing and cannot pay, so while a holder
  * attacks, a bandit defender applies no plan at all.
  *
  * The rule is automatic, so it needs no selection. A facedown copy is not
  * active, and the card is adviser-only, so the holder is found among the
  * players' faceup advisers.
  *
  * Each taxed plan writes `taxed`, naming its user and `amount`, after the
  * plan's own effects, so it never comes before a decision the plan asks. The
  * log drops a line identical to one already posted in the same action, so two
  * taxed plans in one Campaign post one line.
  */
trait PlanSurcharge extends ContributingPower:
  /** The card whose faceup holder taxes the enemy's plans. */
  def cardId: DenizenId
  def catalog: ExecutableCatalog
  /** The added cost of a plan with a source card. */
  protected def cost: Cost
  /** The line each taxed plan writes: its user, then `amount`. */
  protected def taxed: NoteKey
  /** The number the line states. */
  protected def amount: Int
  /** Why a bandit's plan cannot pay the added cost. */
  protected def unpayable: OathViolation

  /** The added cost of the title's plan, which has no card. By default it is
    * paid from the user's board, as the title's own burnt cost is
    * (`CampaignPlanApplication`), which suits a cost with no placed portion. */
  protected def onTitle(ready: ReadyGame, user: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    Right(Vector[CoreOperation](PayCost(user, Location.PlayArea(user), cost)))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(taxed)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignPlanApplication -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case application: CampaignPlanApplication =>
          surcharge(ctx, application).fold(children)(paid =>
            paid +: children :+ note(application))
        case _ => children
      })))

  /** Its line, naming the plan's user; a bandit plan has none and pays
    * nothing. */
  private def note(application: CampaignPlanApplication): Note =
    Note(id, _ => application.user.map(user => taxed(
      PowerSourceRef.Card(cardId), NoteArg.Player(user), NoteArg.Number(amount))))

  private def holder(ctx: PowerCtx): Option[PlayerId] =
    ctx.state.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  /** The added cost, when the plan is the enemy's of a holder in this Campaign. */
  private def surcharge(ctx: PowerCtx, application: CampaignPlanApplication)
      : Option[Operation] = for
    holding <- holder(ctx)
    if enemy(application, holding)
  yield application.user.fold[Operation](
    BuildOps((_, _) => Left(unpayable)))(user =>
    BuildOps((ready, _) => CampaignPlans.cardOf(application.source) match {
      case Some(card) => Right(Vector[CoreOperation](Costs.onCard(user, card,
        cost, catalog, intoOccupied = true)))
      case None => onTitle(ready, user)
    }))

  private def enemy(application: CampaignPlanApplication, holding: PlayerId)
      : Boolean = application.side match
    case CampaignPlanSide.Defender => application.setup.actor == holding
    case CampaignPlanSide.Attacker =>
      application.setup.defender == CampaignDefender.Player(holding)

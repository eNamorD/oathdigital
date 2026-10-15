package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx}
import oathdigital.model._

object MarshSpiritCard extends Denizen(DenizenId("192"), "Marsh Spirit", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.marsh-spirit"),
    persistent = true, cost = Cost.free,
    text = "Players who target this site **cannot** use battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Marsh Spirit (card 192, site-only), a persistent site rule: "Players who
  * target this site cannot use battle plans."
  *
  * While the site it stands faceup at is among a Campaign's targets, every
  * attacker plan is withheld with "Marsh Spirit: Players who target its site
  * cannot use battle plans.", so the attacker is not asked. Who defends does
  * not matter, and the defender's plans are not affected. A Raid targets no
  * site, so it never applies to one.
  */
case object MarshSpirit extends ContributingPower:
  val cardId: DenizenId = MarshSpiritCard.id
  val id: PowerId = MarshSpiritCard.power.id
  private val notes = NoteSet(id)
  /** "Players who target its site cannot use battle plans." */
  val mired: NoteKey = notes.key("mired", Vector(NotePart.Text(
    "Players who target its site cannot use battle plans.")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignAttackerBattlePlans -> Vector(OptionRestriction(
      (ctx, _) => Option.when(targeted(ctx))(OathViolation.CampaignUnavailable(
        "Marsh Spirit keeps players who target its site from battle plans")),
      (ctx, _) => Option.when(targeted(ctx))(
        MarshSpirit.mired(PowerSourceRef.Card(cardId))))))

  /** Whether the Campaign the window `ctx` is gathered for targets the site
    * this card stands faceup at. */
  private def targeted(ctx: PowerCtx): Boolean = PlanContext.of(ctx)
    .exists(context => siteOf(ctx.state).exists(context.targets))

  private def siteOf(ready: ReadyGame): Option[SiteId] =
    ready.game.current.map.sites.collectFirst {
      case (site, state) if state.denizens.exists {
        case card: DenizenState =>
          card.id == cardId && card.orientation == Orientation.FaceUp
        case _ => false
      } => site
    }

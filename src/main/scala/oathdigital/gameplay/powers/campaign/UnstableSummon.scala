package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, PlanAmounts}
import oathdigital.model._

object UnstableSummonCard extends Denizen(DenizenId("225"),
    "Unstable Summon", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.unstable-summon"),
    persistent = false, cost = Cost.free,
    text = "If you hold the Darkest Secret, burn X [secret] from it to " +
      "collect X [attack-die].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Unstable Summon (card 225), an attacker's battle plan: "If you hold the
  * Darkest Secret, burn X [secret] from it to collect X [attack-die]."
  *
  * It is free. While its user holds the Darkest Secret with a secret on it,
  * they choose X, from one to the secrets on the banner: X secrets burn from
  * the banner, and X attack dice join the attack pool. Otherwise it is shown
  * unavailable.
  *
  * It writes "Burned {n secrets} from the Darkest Secret for {n} attack
  * dice."
  */
case object UnstableSummon extends BattlePlan:
  val cardId: DenizenId = UnstableSummonCard.id
  val id: PowerId = UnstableSummonCard.power.id
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix +
    "unstable-summon"
  val heading: String = "Unstable Summon: burn how many secrets from the " +
    "Darkest Secret, for an attack die each?"
  val confirm: String = "Burn"
  val noBanner: String = "Needs the Darkest Secret with a secret on it"
  /** "Burned {n secrets} from the {Darkest Secret} for {n} attack dice." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from the "), NotePart.Arg(1),
    NotePart.Text(" for "), NotePart.Arg(2),
    NotePart.Plural(2, " attack die.", " attack dice.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(burned)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val secrets = context.ready.game.current.banners.darkestSecret.secrets
      val offer = CampaignPlanOffer(source,
        "Unstable Summon: burn secrets for attack dice", Vector.empty,
        Vector.empty)
      if !context.holds(Banner.DarkestSecret) || secrets < 1 then
        offer.copy(withheld = Some(OptionReason.Written(noBanner)))
      else offer.copy(amount = Some(PlanAmounts.range(decisionId, 1,
        secrets, heading, confirm)(summon)))
    }

  /** X secrets burnt from the banner, X attack dice, and the line. */
  private def summon(count: Int): Vector[CampaignPlanEffect] = Vector(
    CampaignPlanEffect.Run(Vector(Burn.secrets(count,
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret))))),
    CampaignPlanEffect.AddAttackDice(count),
    CampaignPlanEffect.Run(Vector(Note(id, _ => Some(burned(
      PowerSourceRef.Card(cardId), NoteArg.Amount(count, NoteUnit.Secret),
      NoteArg.Banner(Banner.DarkestSecret), NoteArg.Number(count)))))))

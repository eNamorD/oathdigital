package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx, Restriction}
import oathdigital.gameplay.powers.{CatalogResolution, RuledCards}
import oathdigital.model._

object DiplomatCard extends Denizen(DenizenId("230"), "Diplomat", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.diplomat"),
    persistent = true, cost = Cost.free,
    text = "Players **cannot** campaign against you unless they rule a " +
      "[suit-hearth] card.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Diplomat (card 230), a persistent rule of a faceup adviser: "Players
  * cannot campaign against you unless they rule a hearth card."
  *
  * An attacker who rules no hearth card (`RuledCards`: their faceup advisers
  * and the faceup cards at the sites they rule) cannot Raid or Conquer the
  * holder. As for the Fortress, three hooks apply it:
  *
  *  - the kind decision loses a kind whose every defender is the holder;
  *  - a Raid's defender decision loses the holder;
  *  - a Campaign whose every legal kind it blocks is refused when it starts,
  *    until the Campaign has answered one of its own decisions
  *    (`FortressRule.underway`).
  *
  * Targets need no filter: every target belongs to the defender. Its hidden
  * options write no line (roadmap, "Explain restricted options where they
  * are offered").
  */
final case class Diplomat private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = DiplomatCard.id
  def id: PowerId = Diplomat.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignKindSelection -> Vector(OptionRestriction(kindGuard)),
    PowerWindow.CampaignDefenderSelection ->
      Vector(OptionRestriction(defenderGuard)),
    PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => startGuard(ctx))))

  /** The holder `attacker` may not campaign against, if any. */
  private def protects(ready: ReadyGame, attacker: PlayerId): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player).filter(holder => holder != attacker &&
      RuledCards.of(catalog, ready, SiteRuler.Player(attacker), Suit.Hearth)
        .isEmpty)

  /** Whether every defender `kind` could have is a holder `attacker` may not
    * campaign against. */
  private def blocked(ready: ReadyGame, attacker: PlayerId,
      kind: CampaignKind): Boolean = protects(ready, attacker) match
    case None => false
    case Some(holder) => kind match
      case CampaignKind.Conquest => CampaignSetup.conquestDefender(ready,
        attacker).contains(CampaignDefender.Player(holder))
      case CampaignKind.Raid =>
        val defenders = CampaignSetup.raidDefenders(ready, attacker)
        defenders.nonEmpty && defenders.forall(_ == holder)

  private def refused(detail: String): OathViolation =
    OathViolation.CampaignUnavailable(detail)

  private def kindGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match
    case DecisionOptionRef.Button("conquest")
        if blocked(ctx.state, ctx.activePlayer, CampaignKind.Conquest) =>
      Some(refused("Diplomat protects the defender of this Conquest"))
    case DecisionOptionRef.Button("raid")
        if blocked(ctx.state, ctx.activePlayer, CampaignKind.Raid) =>
      Some(refused("Diplomat protects every player a Raid could target"))
    case _ => None

  private def defenderGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match
    case DecisionOptionRef.Player(defender)
        if protects(ctx.state, ctx.activePlayer).contains(defender) =>
      Some(refused(s"Diplomat protects ${defender.value} from a Raid"))
    case _ => None

  private def startGuard(ctx: PowerCtx): Option[OathViolation] =
    val kinds = CampaignSetup.legalKinds(ctx.state, ctx.activePlayer)
    Option.when(!FortressRule.underway(ctx.state) && kinds.nonEmpty &&
      kinds.forall(blocked(ctx.state, ctx.activePlayer, _)))(
      refused("Diplomat protects every defender this Campaign could have"))

object Diplomat:
  val id: PowerId = DiplomatCard.power.id

  def forCatalog(catalog: ExecutableCatalog): Diplomat = new Diplomat(catalog)

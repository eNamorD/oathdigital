package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.PlacementLimit
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, OperationRewrite,
  Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object LandWardenCard extends Denizen(DenizenId("130"), "Land Warden", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.land-warden"),
    persistent = false, cost = Cost(favor = 1),
    text = "You may play two cards you draw _(instead of one)_ if you " +
      "play at least one card to a site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Land Warden (card 130), a selected Search modifier costing 1 favor
  * placed on it (catalog batch 6, slice 4):
  *
  *  - The keep decision may hold one or two cards (`SearchKeep`).
  *  - The kept cards are played in the order kept. When the first did not
  *    go to a site, the second may go only to one: its other plays are
  *    unavailable with "Land Warden: one card must go to a site". When no
  *    site has room, its discard stays open (`PlacementLimit`).
  *
  * It writes no line: the Search line names both kept cards, and each
  * play has its own line.
  */
final case class LandWarden private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = LandWardenCard.id
  def id: PowerId = LandWarden.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(favor = LandWarden.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchKeep -> Vector(Transform((ctx, operations) =>
      OperationRewrite.nodes(ctx, operations)((_, _) => {
        case decide: Decide
            if decide.decisionId == SearchProcedure.cardDecisionId =>
          Vector(LandWarden.widened(decide))
      }))),
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match
        case tree: CardPlayProcedure.PlacementTree
            if LandWarden.toSiteOnly(ctx.answered, tree.card) =>
          tree.adjust(children)(_.limitPlacements(LandWarden.siteRule))
        case _ => children)))

object LandWarden:
  val id: PowerId = LandWardenCard.power.id
  val Favor: Int = 1
  /** How many cards the keep decision may hold. */
  val Kept: Int = 2
  val heading: String = "Choose one or two cards to keep"
  val toSite: String = "Land Warden: one card must go to a site"
  /** Only a play to a site stays open. */
  val siteRule: PlacementLimit = PlacementLimit(OptionReason.Written(toSite),
    {
      case SearchPlacement.Site(_, _) => true
      case _ => false
    })

  /** The keep decision `decide`, holding up to [[Kept]] cards. */
  def widened(decide: Decide): Decide = decide.query match
    case partition: DecisionQuery.Partition => decide.copy(query =
      partition.copy(sections = partition.sections.map(section =>
        if section.key == SearchProcedure.keepKey then
          section.copy(maxAllowed = Some(Kept))
        else section), heading = Some(heading)))
    case _ => decide

  /** Whether `card` is the second card kept, and the first did not go to
    * a site. */
  def toSiteOnly(answered: Vector[Answered], card: WorldCardId): Boolean =
    SearchProcedure.kept(answered) match
      case Vector(first, second) if second == card =>
        answered.collectFirst {
          case Answered(decision, DecisionAnswer.ChooseOneAnswer(ref), _)
              if decision ==
                CardPlayProcedure.placementDecisionId(first) => ref
        }.flatMap(CardPlayProcedure.playedAs)
          .exists(_ != PlayedAs.AtSite)
      case _ => false

  def forCatalog(catalog: ExecutableCatalog): LandWarden =
    new LandWarden(catalog)

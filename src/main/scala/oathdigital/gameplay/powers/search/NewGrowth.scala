package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.actions.PlaySites
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object NewGrowthCard extends Denizen(DenizenId("188"), "New Growth",
    Suit.Beast):
  val power = PrintedPower(PowerId("denizen.new-growth"),
    persistent = false, cost = Cost.free,
    text = "You may play [suit-beast] and [suit-hearth] cards to any " +
      "site _(that has space)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** New Growth (card 188), a selected Search modifier with no cost: "You
  * may play [suit-beast] and [suit-hearth] cards to any site (that has
  * space)." (catalog batch 8, slice 12).
  *
  * Through the `SearchPlayAdviser` window it opens every site to a card
  * whose effective suit is Beast or Hearth. "(that has space)" is a
  * reminder: each site follows the pawn site's rules, so a full site
  * takes the card only through a Replace another permission allows. The
  * card's suit is read in hand, where no lens changes it during the
  * action. It writes no line: "Played {card} to {site}" says where.
  */
final case class NewGrowth private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = NewGrowthCard.id
  def id: PowerId = NewGrowth.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match
        case tree: CardPlayProcedure.PlacementTree
            if EffectiveSuit.of(catalog, ctx.state, tree.card)
              .exists(NewGrowth.suits) =>
          tree.adjust(children)(_.openSites(NewGrowth.everySite))
        case _ => children)))

object NewGrowth:
  val id: PowerId = NewGrowthCard.power.id
  /** The suits it lets go to any site. */
  val suits: Set[Suit] = Set(Suit.Beast, Suit.Hearth)
  /** Leave to play to every site. */
  val everySite: PlaySites = PlaySites((_, _) => true)

  def forCatalog(catalog: ExecutableCatalog): NewGrowth =
    new NewGrowth(catalog)

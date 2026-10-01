package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Insect Swarm (card 184), a persistent rule of a faceup adviser: "Your
  * enemy's battle plans each have an added cost of [favor-burnt]."
  *
  * A `PlanSurcharge`: every plan the holder's enemy chooses costs one more
  * favor, burnt. A burnt cost never rests on the card, so it goes to the shared
  * bank even when paid outside its user's turn, and the title's plan burns it
  * from its user's board. Bandits hold no favor and cannot pay it. With
  * Gleaming Armor on the same side, both added costs apply.
  *
  * Each taxed plan writes "{Red}'s battle plans cost 1 extra favor, burnt."
  */
final case class InsectSwarm private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends PlanSurcharge:
  def id: PowerId = InsectSwarm.id
  protected def cost: Cost = Cost(favorBurnt = InsectSwarm.Favor)
  protected def taxed: NoteKey = InsectSwarm.taxed
  protected def amount: Int = InsectSwarm.Favor
  protected def unpayable: OathViolation =
    OathViolation.InsufficientFavor(InsectSwarm.Favor, 0)

object InsectSwarm:
  val id: PowerId = PowerId("denizen.insect-swarm")
  /** The added cost, in favor burnt. */
  val Favor: Int = 1
  /** "{Red}'s battle plans cost {1} extra favor, burnt." */
  val taxed: NoteKey = NoteKey("taxed", Vector(NotePart.Arg(0),
    NotePart.Text("'s battle plans cost "), NotePart.Arg(1),
    NotePart.Text(" extra favor, burnt.")))

  def forCatalog(catalog: ExecutableCatalog): Option[InsectSwarm] =
    CatalogCards.denizen(catalog, id).map(new InsectSwarm(_, catalog))

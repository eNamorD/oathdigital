package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.model._

object InsectSwarmCard extends Denizen(DenizenId("184"), "Insect Swarm", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.insect-swarm"),
    persistent = true, cost = Cost.free,
    text = "Your enemy's battle plans each have an added cost of " +
      "[favor-burnt].")
  val powers: Vector[PrintedPower] = Vector(power)

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
final case class InsectSwarm private (catalog: ExecutableCatalog)
    extends PlanSurcharge:
  val cardId: DenizenId = InsectSwarmCard.id
  def id: PowerId = InsectSwarm.id
  protected def cost: Cost = Cost(favorBurnt = InsectSwarm.Favor)
  protected def taxed: NoteKey = InsectSwarm.taxed
  protected def amount: Int = InsectSwarm.Favor
  protected def unpayable: OathViolation =
    OathViolation.InsufficientFavor(InsectSwarm.Favor, 0)

object InsectSwarm:
  val id: PowerId = InsectSwarmCard.power.id
  /** The added cost, in favor burnt. */
  val Favor: Int = 1
  /** "{Red}'s battle plans cost {1} extra favor, burnt." */
  val taxed: NoteKey = NoteKey("taxed", Vector(NotePart.Arg(0),
    NotePart.Text("'s battle plans cost "), NotePart.Arg(1),
    NotePart.Text(" extra favor, burnt.")))

  def forCatalog(catalog: ExecutableCatalog): InsectSwarm =
    new InsectSwarm(catalog)

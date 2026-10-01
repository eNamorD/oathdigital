package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Gleaming Armor (card 66), a persistent rule of a faceup adviser: "Your enemy's
  * battle plans have an added cost of [secret]."
  *
  * A `PlanSurcharge`: every plan the holder's enemy chooses costs one more
  * secret, placed onto the plan's source card like any plan's cost. The title
  * has no card, so the added cost of the title's plan is turning one of its
  * user's faceup secrets facedown. Bandits hold no secrets and cannot pay it.
  *
  * Each taxed plan writes "{Red}'s battle plans cost 1 extra secret."
  */
final case class GleamingArmor private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends PlanSurcharge:
  def id: PowerId = GleamingArmor.id
  protected def cost: Cost = Cost(secret = GleamingArmor.Secret)
  protected def taxed: NoteKey = GleamingArmor.taxed
  protected def amount: Int = GleamingArmor.Secret
  protected def unpayable: OathViolation =
    OathViolation.InsufficientSecrets(GleamingArmor.Secret, 0)

  // Turning a secret facedown does nothing without one, so the cost of the
  // title's plan is checked here rather than left to a best-effort flip.
  override protected def onTitle(ready: ReadyGame, user: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    val faceUp = ready.game.current.players.find(_.player == user)
      .fold(0)(_.board.faceUpSecrets)
    if faceUp >= GleamingArmor.Secret then Right(Vector[CoreOperation](
      FlipSecrets(user, GleamingArmor.Secret, SecretSide.FaceUp,
        SecretSide.FaceDown)))
    else Left(OathViolation.InsufficientSecrets(GleamingArmor.Secret, faceUp))

object GleamingArmor:
  val id: PowerId = PowerId("denizen.gleaming-armor")
  /** The added cost, in secrets. */
  val Secret: Int = 1
  /** "{Red}'s battle plans cost {1} extra secret." */
  val taxed: NoteKey = NoteKey("taxed", Vector(NotePart.Arg(0),
    NotePart.Text("'s battle plans cost "), NotePart.Arg(1),
    NotePart.Plural(1, " extra secret.", " extra secrets.")))

  def forCatalog(catalog: ExecutableCatalog): Option[GleamingArmor] =
    CatalogCards.denizen(catalog, id).map(new GleamingArmor(_, catalog))

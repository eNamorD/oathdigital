package oathdigital.gameplay.powers.rest

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport}
import oathdigital.model._

object InsomniaCard extends Denizen(DenizenId("97"), "Insomnia", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.insomnia"),
    persistent = false, cost = Cost.free,
    text = "You can only have two advisers. **REST:** Gain [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Insomnia (card 97, adviser-only, locked): "You can only have two advisers.
  * REST: Gain [secret]."
  *
  * The limit is a [[HolderAdviserLimit]], exactly as Silver Tongue's is, so
  * playing Insomnia faceup needs room under the limit, and with Silver Tongue
  * as well the limit stays 2. The limit writes no line, as for Silver Tongue.
  *
  * The REST power is an optional
  * [[oathdigital.gameplay.powerresolver.PhasePower]], once per turn, which the
  * engine enforces. Its line, "{Red} gained 1 secret.", restates the gain in
  * place of the generic gain line, as Tutor's does.
  */
final case class Insomnia private (cardId: DenizenId)
    extends PhasePower with ContributingPower:
  private val limit = HolderAdviserLimit(cardId, Insomnia.HolderLimit,
    "Insomnia")

  def id: PowerId = Insomnia.id
  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(Insomnia.gained)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = true

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Gain.Secrets(player, Insomnia.Secrets),
    Note(id, NoteSupport.gainNote(Insomnia.gained, source, player,
      NoteUnit.Secret, NoteSupport.secrets), covers = true))))

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayAdviser -> Vector(limit.contribution))

  /** The adviser limit Insomnia sets on `player`: its holder, and only while
    * it is faceup.
    */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    limit.limitFor(ready, player)

object Insomnia:
  val id: PowerId = PowerId("denizen.insomnia")
  /** How many advisers the holder may have, in either orientation. */
  val HolderLimit: Int = 2
  val Secrets: Int = 1
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)

  def forCatalog(catalog: ExecutableCatalog): Option[Insomnia] =
    CatalogCards.denizen(catalog, id).map(new Insomnia(_))

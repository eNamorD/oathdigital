package oathdigital.catalog

import oathdigital.model.{Cost, DenizenId, EdificeId, LegacyId, PowerId,
  RelicId, SiteId, Suit, Tokens}

/** A power as its card prints it. `cost` is the run of favor and secret
  * symbols the printed text opens with; `text` is the rest, timing keyword
  * included. Gameplay never reads `text`: behaviour lives in the power.
  */
final case class PrintedPower(id: PowerId, persistent: Boolean, cost: Cost,
    text: String):
  require(text.trim.nonEmpty, "printed power text must not be blank")

  /** The printed line: the cost symbols in printed order, then `text`. */
  def rulesText: String =
    val symbols = Vector("favor" -> cost.favor, "secret" -> cost.secret,
      "favor-burnt" -> cost.favorBurnt, "secret-burnt" -> cost.secretBurnt)
      .flatMap((symbol, count) => Vector.fill(count)(s"[$symbol]"))
    if symbols.isEmpty then text else symbols.mkString("", " ", " ") + text

/** Printed restrictions a card mixes in. A locked adviser-only denizen mixes
  * in both `Locked` and `AdviserOnly`; an edifice's intact face is `Locked`.
  */
trait Locked
trait SiteOnly
trait AdviserOnly

/** A card, or an edifice face, that prints powers. */
trait PrintsPowers:
  def powers: Vector[PrintedPower]
  final def rulesText: String = powers.map(_.rulesText).mkString("\n\n")

abstract class Denizen(val id: DenizenId, val name: String, val suit: Suit)
    extends PrintsPowers

abstract class Relic(val id: RelicId, val name: String, val value: Int,
    val defense: Int) extends PrintsPowers

abstract class EdificeFace(val name: String) extends PrintsPowers

/** An edifice card: one suit, two named faces. A card object implements
  * `intact` and `ruined` with nested face objects. */
abstract class Edifice(val id: EdificeId, val suit: Suit):
  def intact: EdificeFace
  def ruined: EdificeFace

abstract class Legacy(val id: LegacyId, val name: String) extends PrintsPowers

/** A site. It prints no rules text. A site object names each of its
  * power ids as a member and lists them in `handlers`. */
abstract class Site(
    val id: SiteId,
    val name: String,
    val defense: Int,
    val capacity: Int,
    val relicSlots: Int,
    val recoverDifficulty: Option[Int],
    val startingResources: Tokens,
    val forgeRequirements: Option[Tokens],
    val homeland: Option[Suit]
):
  def handlers: Vector[PowerId]

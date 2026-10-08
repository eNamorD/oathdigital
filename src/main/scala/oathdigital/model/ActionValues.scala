package oathdigital.model

enum AttackDieFace extends DieFace { case HollowSword, OneSword, TwoSwordsSkull }
object AttackDieFace:
  def score(faces: Vector[AttackDieFace]): Int =
    faces.count(_ == OneSword) + faces.count(_ == HollowSword) / 2 +
      faces.count(_ == TwoSwordsSkull) * 2

  def skulls(faces: Vector[AttackDieFace]): Int =
    faces.count(_ == TwoSwordsSkull)

enum DefenseDieFace extends DieFace { case Blank, OneShield, TwoShields, Doubler }
object DefenseDieFace:
  def score(faces: Vector[DefenseDieFace]): Int =
    val shields = faces.map {
      case OneShield => 1
      case TwoShields => 2
      case _ => 0
    }.sum
    shields * (1 << faces.count(_ == Doubler))

sealed trait SearchSource extends Product with Serializable
object SearchSource:
  case object WorldDeck extends SearchSource
  final case class RegionalDiscard(region: Region) extends SearchSource

  /** "world deck" or "Cradle discard pile". A log line or a label supplies
    * the article. */
  def name(source: SearchSource): String = source match
    case WorldDeck => "world deck"
    case RegionalDiscard(region) => s"$region discard pile"

  /** The board option that names `source`: the world deck, or a region's
    * discard pile. A Search's source decision and Clay Rattle offer these. */
  def ref(source: SearchSource): DecisionOptionRef = source match
    case WorldDeck => DecisionOptionRef.Deck(CardDeck.World)
    case RegionalDiscard(region) => DecisionOptionRef.DiscardPile(region)

  /** The pile `ref` names, when it names a pile a Search could draw from. */
  def fromRef(ref: DecisionOptionRef): Option[SearchSource] = ref match
    case DecisionOptionRef.Deck(CardDeck.World) => Some(WorldDeck)
    case DecisionOptionRef.DiscardPile(region) => Some(RegionalDiscard(region))
    case _ => None

  /** `source` as a decision option. */
  def option(source: SearchSource): DecisionOption = source match
    case WorldDeck =>
      DecisionOption.Deck(DecisionOptionRef.Deck(CardDeck.World))
    case RegionalDiscard(region) =>
      DecisionOption.DiscardPile(DecisionOptionRef.DiscardPile(region))

sealed trait SearchPlacement extends Product with Serializable
object SearchPlacement:
  case object Discard extends SearchPlacement
  final case class Site(replace: Option[CardId]) extends SearchPlacement
  final case class Adviser(
      orientation: Orientation,
      replace: Option[CardId]
  ) extends SearchPlacement


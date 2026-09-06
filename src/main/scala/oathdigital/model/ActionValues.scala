package oathdigital.model

sealed trait AttackDieFace extends DieFace with Product with Serializable
object AttackDieFace {
  case object HollowSword extends AttackDieFace
  case object OneSword extends AttackDieFace
  case object TwoSwordsSkull extends AttackDieFace

  def score(faces: Vector[AttackDieFace]): Int =
    faces.count(_ == OneSword) + faces.count(_ == HollowSword) / 2 +
      faces.count(_ == TwoSwordsSkull) * 2

  def skulls(faces: Vector[AttackDieFace]): Int =
    faces.count(_ == TwoSwordsSkull)
}

sealed trait DefenseDieFace extends DieFace with Product with Serializable
object DefenseDieFace {
  case object Blank extends DefenseDieFace
  case object OneShield extends DefenseDieFace
  case object TwoShields extends DefenseDieFace
  case object Doubler extends DefenseDieFace

  def score(faces: Vector[DefenseDieFace]): Int = {
    val shields = faces.map {
      case OneShield => 1
      case TwoShields => 2
      case _ => 0
    }.sum
    shields * (1 << faces.count(_ == Doubler))
  }
}

sealed trait SearchSource extends Product with Serializable
object SearchSource {
  case object WorldDeck extends SearchSource
  final case class RegionalDiscard(region: Region) extends SearchSource
}

sealed trait SearchPlacement extends Product with Serializable
object SearchPlacement {
  case object Discard extends SearchPlacement
  final case class Site(replace: Option[CardId]) extends SearchPlacement
  final case class Adviser(
      orientation: Orientation,
      replace: Option[CardId]
  ) extends SearchPlacement
}


package oathdigital.application

import oathdigital.model.{AttackDieFace, DefenseDieFace, DieFace}
import oathdigital.protocol.projection.{DieFace => RolledFace}

/** The shared face vocabulary for a face the engine rolled: one table from
  * the engine's open `DieFace` to the seven faces the two dice print, which
  * carry their own wire key and the word each is read by
  * (`protocol.projection.DieFace`).
  *
  * One table, read by both wires that show a roll -- the Game Log's typed
  * spans (`LogWords.dice`) and the decision wire's roll outcome
  * (`WalkerDecisionProjector`). The decision wire used to spell the keys
  * again for itself, held to these by a test; it reads this instead
  * (decision-id vocabulary decision, item 3).
  *
  * `oathdigital.model.DieFace` is open, so a face family neither pool rolls
  * maps to nothing rather than to a guess, and a wire leaves it out rather
  * than inventing a key for it.
  */
private[application] object RolledFaces:
  def of(value: DieFace): Option[RolledFace] = value match
    case AttackDieFace.HollowSword => Some(RolledFace.HollowSword)
    case AttackDieFace.OneSword => Some(RolledFace.OneSword)
    case AttackDieFace.TwoSwordsSkull => Some(RolledFace.TwoSwordsSkull)
    case DefenseDieFace.Blank => Some(RolledFace.Blank)
    case DefenseDieFace.OneShield => Some(RolledFace.OneShield)
    case DefenseDieFace.TwoShields => Some(RolledFace.TwoShields)
    case DefenseDieFace.Doubler => Some(RolledFace.Doubler)
    case _ => None

package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, EconomyActions, PowerRules, Table}
import oathdigital.testkit.Table.p1

/** What the Trade's own line says when a `TradeGain` power rewrites the gain
  * in place to record it. All three such powers return the record and the
  * gain where the gain stood, so the gain's position holds two leaves, and
  * `TradeProcedure.tradedNote` restates the leaf before it. A record placed
  * after the gain is therefore what the note reads, and it moves no
  * resource, so the line says the Trade gained nothing: the three hold
  * their `Defer` before the gain for that reason, and these tests are what
  * says so (procedures journal their own notes, slice 3).
  */
class TradeGainNoteSuite extends munit.FunSuite:
  private val rules = PowerRules.withWalkerPowers
  private val home = CatalogNames.site("Ancient City")
  private val oak = CatalogNames.denizen("The Old Oak")
  private val library = CatalogNames.denizen("Mounted Library")
  private val signal = CatalogNames.denizen("Secret Signal")
  /** A Beast adviser, so a Trade with The Old Oak gains 1 secret. */
  private val bracken = CatalogNames.denizen("Bracken")
  /** A Nomad adviser, so a Trade with Mounted Library gains 1 secret. */
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  /** A plain Order denizen to Trade with. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")

  /** The Trade's own lines in `events`, as plain sentences. */
  private def traded(events: Vector[OathEvent]): Vector[String] =
    NoteText.said(ProcedureNotes.id(ActionRef.Trade),
      TradeProcedure.noteKeys, events).map(_.text)

  test("The Old Oak's bonus does not cost the Trade its own line"):
    val ready = Table.start.pawn(p1, at = home).denizen(oak, at = home)
      .adviser(p1, bracken).favor(p1, 2).ready
    val done = EconomyActions.trade(rules, ready, p1,
      Vector(TheOldOak.id), "secret", DecisionOptionRef.Denizen(oak))
    assertEquals(traded(done.journal.events),
      Vector(s"Traded with ${oak.value} for 1 secret"))

  test("Mounted Library's doubling does not cost the Trade its own line"):
    val ready = Table.start.pawn(p1, at = home).denizen(library, at = home)
      .adviser(p1, horseArchers).favor(p1, 2).secrets(p1, faceUp = 1).ready
    val done = EconomyActions.trade(rules, ready, p1,
      Vector(MountedLibrary.id), "secret",
      DecisionOptionRef.Denizen(library))
    assertEquals(traded(done.journal.events),
      Vector(s"Traded with ${library.value} for 1 secret"))

  test("Secret Signal's extra favor does not cost the Trade its own line"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, signal)
      .denizen(wrestlers, at = home).bankFavor(Suit.Order, 3).ready
    val done = EconomyActions.trade(rules, ready, p1,
      Vector(SecretSignal.id), "favor",
      DecisionOptionRef.Denizen(wrestlers))
    assertEquals(traded(done.journal.events),
      Vector(s"Traded with ${wrestlers.value} for 1 favor"))

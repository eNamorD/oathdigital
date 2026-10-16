package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.{PowerUse, PowerUseRefusal,
  PowerUseRestriction}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The power-use filter's consent rules (catalog batch 8, slice 10): a
  * remembered grant lifts a refusal, and a refused option is askable only
  * when consent may lift every refusal on it. */
class PowerUseRulesSuite extends munit.FunSuite:
  private val ruled = PowerId("test.ruled-ban")
  private val banned = PowerId("test.banned-power")
  private val card = CatalogNames.denizen("Roving Terror")
  private val use = PowerUse(p1, banned, Some(card), persistent = false,
    costsSecret = true)
  private val covers = ConsentKey(ruled, p1, PowerUseRules.useKey,
    DecisionOptionRef.Denizen(card))
  private val notes = NoteSet(ruled)
  private val plain = notes.key("test-ban",
    Vector(NotePart.Text("Banned.")))
  private val asked = notes.key("test-ban-consent",
    Vector(NotePart.Text("Banned without ruler's consent.")))
  private val source = PowerSourceRef.Card(card)

  /** A ban on every use, waivable by p2 when `granter` names them. */
  private def banGrantedBy(granter: Option[PlayerId]): PowerUseRestriction =
    new PowerUseRestriction:
      def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
        Some(PowerUseRefusal(ruled, plain(source), OperationReason(
          "test-ban", "banned", OperationReasonKind.Impossible,
          granter.map(player => OperationWaiver(ruled, Some(player),
            covers, asked(source), plain(source))))))

  private val quiet: ReadyGame = Table.start.ready

  private def remembering(answer: ConsentAnswer): ReadyGame =
    quiet.copy(consent = ConsentState(answers = Map(covers -> answer),
      turn = Some(ConsentTurn.of(quiet))))

  test("the key names the restriction's slot and the card"):
    assertEquals(PowerUseRules.target(use), DecisionOptionRef.Denizen(card))

  test("a grant remembered this turn lifts the refusal; a refusal does " +
      "not"):
    val ban = Vector(banGrantedBy(Some(p2)))
    assertEquals(PowerUseRules.refusals(remembering(ConsentAnswer.Granted),
      use, ban), Vector.empty)
    assertEquals(PowerUseRules.refusals(remembering(ConsentAnswer.Refused),
      use, ban).map(_.power), Vector(ruled))

  test("a refusal its ruler may waive is askable of them"):
    val refused = PowerUseRules.refusals(quiet, use,
      Vector(banGrantedBy(Some(p2))))
    assertEquals(PowerUseRules.shown(quiet, p1, refused, consent = true),
      OptionReason.Askable(ruled, asked(source),
        Vector(ConsentAsk(p2, Vector(ruled))), Vector(covers)))

  test("without consent, or with nobody to grant it, the plain reason is " +
      "shown"):
    val waivable = PowerUseRules.refusals(quiet, use,
      Vector(banGrantedBy(Some(p2))))
    assertEquals(PowerUseRules.shown(quiet, p1, waivable, consent = false),
      OptionReason.Noted(ruled, plain(source)))
    val bandits = PowerUseRules.refusals(quiet, use,
      Vector(banGrantedBy(None)))
    assertEquals(PowerUseRules.shown(quiet, p1, bandits, consent = true),
      OptionReason.Noted(ruled, plain(source)))

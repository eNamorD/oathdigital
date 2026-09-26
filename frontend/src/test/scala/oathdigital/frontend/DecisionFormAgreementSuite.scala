package oathdigital.frontend

import oathdigital.protocol.DecisionFormVocabulary
import ParkedDecision.DecisionForm

/** The client half of the vocabulary pin: the set of forms this client
  * recognises is exactly the set the projector emits. One set equality, so
  * it fails in both directions -- a form the projector emits that this
  * client parses as `Unknown`, and a case of `DecisionForm` no projected
  * form reaches.
  *
  * Deleted with `DecisionForm` in the next commit.
  */
class DecisionFormAgreementSuite extends munit.FunSuite:
  test("this client recognises exactly the projector's forms"):
    assertEquals(DecisionFormVocabulary.All.map(DecisionForm.parse),
      Set[DecisionForm](DecisionForm.ChooseOne, DecisionForm.ChooseMany,
        DecisionForm.ChooseAmount, DecisionForm.Partition,
        DecisionForm.Distribute, DecisionForm.Negotiate))

  test("a form outside the vocabulary keeps its spelling as unknown"):
    assertEquals(DecisionForm.parse("choose-two"),
      DecisionForm.Unknown("choose-two"))

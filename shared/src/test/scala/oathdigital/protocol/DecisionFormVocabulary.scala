package oathdigital.protocol

/** The projection wire's form vocabulary, as data, for the one commit in
  * which it is not a type.
  *
  * Scaffolding. The typed `DecisionQueryProjection` replaces it in the next
  * commit, where agreement between the projector and the client is
  * structural, and this table is deleted with the last reader.
  *
  * It lives here because no single compilation unit sees both sides:
  * `src/test` cannot see `oathdigital.frontend`, `frontend/src/test` cannot
  * see `oathdigital.application`, and `shared/src/test/scala` -- a test
  * source directory of both projects -- must compile against neither. That
  * is the defect: today the two vocabularies can only agree by convention.
  * Until the type exists they agree through this table, and adding a seventh
  * form means adding it here, which fails whichever side has not learnt it.
  *
  * Deliberately NOT the journal's answer tags. `DecisionAnswerCodec` spells
  * five of these strings for durable rows and must keep spelling them
  * independently; see
  * `docs/superpowers/specs/2026-09-25-decision-form-tag-separation-decision.md`.
  */
object DecisionFormVocabulary:
  val ChooseOne = "choose-one"
  val ChooseMany = "choose-many"
  val ChooseAmount = "choose-amount"
  val Partition = "partition"
  val Distribute = "distribute"
  val Negotiate = "negotiate"

  val All: Set[String] = Set(ChooseOne, ChooseMany, ChooseAmount, Partition,
    Distribute, Negotiate)

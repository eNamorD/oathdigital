# Prune low-value tests: ledger

The survey behind [the prune design](2026-09-27-prune-low-value-tests-design.md).
Seven read-only agents read every test in their area on 2026-09-27, at main
`4fa6c568`. Line numbers refer to that commit.

Each finding is a checkbox. When a finding is handled, tick it and append the
outcome after the evidence: **done**, **strengthened**, **merged into X**, or
**kept: reason**. A finding whose proof does not hold is kept, with the reason.
The design's rules decide each outcome; where a finding's suggested action
disagrees with a rule (for example a data pin that is on the wire), the rule
wins.

Categories: TAUTOLOGY, WEAK, DUPLICATE, COVERED-PLUMBING, TYPE-PREVENTED,
SOURCE-TEXT, OBSOLETE, OTHER (defined in the design).

## Progress

Test counts come from `./sbtw root/test` and `./sbtw frontend/test` (the
frontend run includes the 49 shared-project tests). CPU is the sum of munit's
per-test times; it varies with machine load by tens of percent, so compare
counts first.

| After | Root tests | Frontend tests | Root CPU | Frontend CPU |
|---|---|---|---|---|
| Baseline | 2331 | 508 | 131.9s | 3.1s |

## Found during execution

Dead code found beside a listed unit, removed under the same rule.

- [x] `catalog/CatalogModel.scala`: `SetupCardDefinition`,
  `SupplyBoardDefinition` and `VisionDefinition`, "temporary
  source-compatible shells" with no user anywhere. **done** (with
  `CatalogSelection`)

## Gameplay suites A–M

Scope: src/test/scala/oathdigital/gameplay/*.scala, A–M.

### Survey summary

- Suites read: 31 suites plus 2 fixtures (CampaignFixture, EconomyFixture). Tests read: about 288.
- Findings by category:
  - WEAK: 27
  - DUPLICATE: 13
  - TAUTOLOGY: 8
  - OBSOLETE: 6
  - TYPE-PREVENTED: 3
  - COVERED-PLUMBING: 2
  - OTHER: 2
  - SOURCE-TEXT: 2 groups covering 21 tests
- Whole-suite candidates:
  - `ForgeRulesSuite`: test 1 is a weaker copy of ForgeProcedureSuite's P1 gates. Move tests 2 and 3 (favor/secret funding, unaudited handler) into ForgeProcedureSuite P1 and drop the suite.
  - `ContributingPowerSuite`: one test restates trait defaults. The other (sortKey) overlaps ContributionCollectorSuite:151. Fold the sortKey test into the collector suite.
  - `DiscardRestrictionsCoverageSuite`: only source-text guards, and test 2 is inside test 1.
  - `BackendArchitectureSuite`: 19 of 28 tests are source-text guards; several duplicate each other or are obsolete. Its first 9 tests are real behavior tests of RuleSourceIndex and PowerResolver in the wrong home.

### Findings

- [ ] `src/test/scala/oathdigital/gameplay/ForgeRulesSuite.scala:38` "rule, Forge icon, exact empty denizens, Supply, and relic deck are authoritative" — DUPLICATE — delete — high. `ForgeProcedure.build` calls `ForgeRules.validate`. ForgeProcedureSuite:176/206/225/231 check the same four gates with exact violations; this test only checks `isLeft` (one `isInstanceOf`).
- [ ] `ForgeRulesSuite.scala:54` "the actor must be able to fund the printed cost…" — WEAK — strengthen — low. The `if cost.favor > 0` / `if cost.secrets > 0` branches can silently do nothing, depending on which site `catalog.sites.find(_.forgeRequirements.nonEmpty)` returns. The facedown-secrets check is only `isLeft`.
- [ ] `ForgeProcedureSuite.scala:299` "the shipped catalog really does print four single-resource Forge costs…" — TAUTOLOGY — delete — medium. It restates catalog data. Its only production call (`parks`) is already covered by :307.
- [ ] `ForgeProcedureSuite.scala:243` "P2: a successful build roots at ForgeActionEligibility…" — DUPLICATE (partial) — merge into :593 — low. The root/child window asserts repeat the :593 window inventory. The step/supply part is unique.
- [ ] `ContributingPowerSuite.scala:41` "a power declaring only id/source/contributions gets the trait defaults" — TAUTOLOGY — delete — medium. It asserts the literal defaults `priority = 0`, `applicable = true`, `shouldIgnore = false`.
- [ ] `ContributingPowerSuite.scala:23` "sortKey orders equal-priority powers…" — DUPLICATE — merge into ContributionCollectorSuite:151 — low. Same interleaved-key setup; one tests `sortKey` directly, the other through `gather`.
- [ ] `EnclosingProcedureSuite.scala:57` "a context built without one names none" — TAUTOLOGY — delete — high. It only checks the `procedure: Option[ProcedureRef] = None` default argument of PowerCtx.
- [ ] `ContributionCollectorSuite.scala:115` "A ignores B: B's transform is absent, A's is present" — DUPLICATE — merge with ContributionIgnoresSuite:37 — medium. Same entry point (`gather`), same shape: a `shouldIgnore` power drops another.
- [ ] `ContributionCollectorSuite.scala:129` "A ignores B and B ignores C … (no transitivity)" — DUPLICATE — merge with ContributionIgnoresSuite:54 "one pass…" — high. Same one-pass three-power chain through `gather`. The only difference is an `ignores` versus a `shouldIgnore` override, and `ignores` defaults to `shouldIgnore`.
- [ ] `ContributionCollectorSuite.scala:69` "a power may ignore candidates by classification, not only by name" — DUPLICATE — merge into :115 — low. Classification is done by the test's own `shouldIgnore` predicate; the collector path is the same as :115.
- [ ] `CampaignProcedureSuite.scala:271` "with no plan available the attacker window is skipped" — DUPLICATE — delete — high. Same `board()`, same start and force. :126 already ends with `assertParked(..., CampaignIds.sacrifice, ...)`. CampaignPlanWindowSuite:160 also covers the skip.
- [ ] `CampaignProcedureSuite.scala:83` "the empty selection is a valid targets answer" — WEAK — strengthen — medium. Only `isRight`; assert it parks on `CampaignIds.force`.
- [ ] `CampaignProcedureSuite.scala:120` "a Campaign the actor cannot pay for is not offered and does not start" — WEAK — strengthen — medium. `start(b).isLeft`; assert the exact supply violation.
- [ ] `CampaignProcedureSuite.scala:152` "more force than the board holds is rejected" — WEAK — strengthen — medium. Only `isLeft`.
- [ ] `CampaignProcedureSuite.scala:254` "a plan already chosen is rejected when chosen again" — WEAK — strengthen — low. Only `isLeft`; :235 already shows the option is gone after the pick.
- [ ] `CampaignProcedureSuite.scala:174` "a held battle-plan relic does not block the start" — WEAK/OBSOLETE — strengthen or delete — low. Only `isRight`. `bag-of-siegeworks` has no handler anywhere in src/main, so "its plan is chosen at the plan step" is not true today. It guards against a removed gate.
- [ ] `CampaignProcedureSuite.scala:158` "no first-game gate: an altered Foundation or a Citizen still campaigns" — OBSOLETE — delete or keep as a cheap guard — low. No first-game gate exists in the campaign code. It only checks `isRight` against the return of a removed gate.
- [ ] `CampaignProcedureSuite.scala:461` (second half, the card at the origin is not the attacker's) — DUPLICATE — low. Same behavior as CampaignPlansSuite:48, reached through the walk instead of `plan()`.
- [ ] `CampaignPlanWindowSuite.scala:436` "Finish ends the window and a chosen source is not offered again" — WEAK — strengthen — medium. With a single plan, the window closes after the pick, so the final `resolveWalker(... attackerPlan ...).isLeft` passes because the decision is no longer open. It does not show that the source is not re-offered. Use two plans and check the rebuilt options.
- [ ] `CampaignRaidSuite.scala:87` "the pawn cannot be relocated to its own site" — WEAK — strengthen — medium. Only `isLeft`.
- [ ] `CampaignRaidSuite.scala:102` "several enemy pawns at the site ask which to Raid" — WEAK — strengthen — medium. The answer is only `isRight`; assert the chosen defender is used (for example, the next park or the setup defender).
- [ ] `CampaignPowersSuite.scala:111` "a Pass that is not in play forbids nothing" — WEAK — strengthen — low. `.size == 2` only; compare the refs.
- [ ] `AutomaticRollSuite.scala:91` "a simulated tree rolls placeholder faces instead of failing" — WEAK — strengthen — medium. `WalkerSimulation.run(...).isRight` passes even if no roll happens; assert the placeholder faces or the roll outcome.
- [ ] `DecisionQueriesSuite.scala:12`, `:21`, `:27` (partition rejects over-max / max<min / one-section) — WEAK — strengthen — medium. Each test is only `isLeft`, so any rejection passes all three; assert the specific violation text.
- [ ] `CoOwnedDecideSuite.scala:81` "an answer for a decision that is not open is rejected" — WEAK — strengthen — medium. Only `isLeft`.
- [ ] `CardPlayProcedureSuite.scala:55` "card absent from temporary hand cannot build card play" — WEAK — strengthen — medium. Only `isLeft`.
- [ ] `CardPlayProcedureSuite.scala:279` "Search Vision can replace an existing revealed Vision" — WEAK — strengthen — medium. It only checks that the "adviser-faceup" option exists. The replacement of the old Vision is never checked.
- [ ] `CardPlayProcedureSuite.scala:210` "full site without Homeland permission offers no site placement" — DUPLICATE — low. Overlaps HomelandRuleSuite:62 (a full non-Homeland Ancient City offers no site), through `build` instead of `legalChoices`.
- [ ] `CardPlayProcedureSuite.scala:297` "facedown adviser starts the shared walker placement tree" — COVERED-PLUMBING (projection half) — trim — low. The GameProjector `walkerDecision`/`walkerWaiting` hiding is application projection. It is likely covered by `application/WalkerDecisionProjectionSuite`. `assert(started.isRight)` is redundant with the next line.
- [ ] `CardPlayHooksSuite.scala:128` "the facedown hook names the card and the player who played it" — TAUTOLOGY — delete — medium-high. It reads constructor fields back, plus `children == empty` and the window constant.
- [ ] `CardPlayHooksSuite.scala:69` "the faceup window keeps the persisted key of the single window" — TAUTOLOGY — keep only if no serialization golden test pins window keys — low. It restates `PowerWindow.scala:57/60` literals, but as a guard for a persisted key.
- [ ] `CatacombsContributionSuite.scala:139` "a Restriction-only power does not make relic-less Recover illegal" — WEAK — strengthen or merge into :82 — low. Only `isRight`, with a restriction that always returns None.
- [ ] `ChallengeProcedureSuite.scala:84` "a start with no Supply is rejected" — WEAK — strengthen — medium. Only `isLeft`; the neighbouring tests assert exact violations.
- [ ] `CoreOperationsSuite.scala:12` "operation requiredness distinguishes costs, draws, and optional effects" — TAUTOLOGY (partial) — trim — medium. `SpendSupply(..., required = false).required`, `Discard.Denizen(..., required = true).required`, `Play(..., required = true).required` and `Replace(..., required = true).required` read back explicit arguments. Only the default-valued lines test anything.
- [ ] `EconomyWalkerSuite.scala:102` "a start with no token-free card, or a wrong selection, is rejected" — WEAK — strengthen — medium. Four `isLeft` checks; :94 in the same suite shows the exact-violation style.
- [ ] `EconomyWalkerSuite.scala:109` "only the actor can answer, and only with an offered card" — WEAK — strengthen — low. `WrongPlayer` and `EconomyCardUnavailable` are available to assert.
- [ ] `EconomyWalkerSuite.scala:116` "a lineage with no warband supply cannot Muster" — TYPE-PREVENTED (unreachable state) — delete or keep as a defensive check — low. The comment admits no step produces this state; it only checks `isLeft`.
- [ ] `EconomyWalkerSuite.scala:123` "an unimplemented optional Economy power does not block a base Trade" — WEAK — strengthen — low. Only `isRight`; assert the Trade's resource change.
- [ ] `MusterProcedureSuite.scala:104` "an option the actor cannot pay for is previewed as dropped" — DUPLICATE — merge into :89 — high. :89's first assertion makes the same call on the same state (`atAlchemist.favor(p1, 0)`) with the same `forall(_.outcome.isLeft)`.
- [ ] `MusterProcedureSuite.scala:49` "Muster costs one Supply and one favor and gains one warband per matching adviser plus one" — DUPLICATE (partial) — low. Same board and numbers (favor 3, supply 6, warbands 5) as EconomyWalkerSuite:56, through ProcedureWalker instead of OathRules. Each suite has one unique assert (tokens here, completion there).
- [ ] `MusterProcedureSuite.scala:128` "a board whose site forces name an unknown lineage cannot start" — TYPE-PREVENTED (unreachable state) — delete — low-medium. The comment says `ready` refuses this state. Only `isLeft`.
- [ ] `MusterProcedureSuite.scala:137` "Muster cannot start outside the Act phase" — WEAK — strengthen — medium. Only `isLeft`; assert `WrongPhase`.
- [ ] `EndWakeProcedureSuite.scala:128` "the declared tree is one phase change under no window" — TAUTOLOGY — delete — medium. It compares against the literal constant tree; :45 already asserts the single recorded op `EnterPhase(Act)`.
- [ ] `EndWakeProcedureSuite.scala:89` "ending Wake remains legal while another player has a revealed Vision" and `:110` (second half, the titled Wake) — OBSOLETE — delete — low. Both were ported from the deleted WakeSuite's gates. :128 shows the EndWake tree has no conditions at all, so they guard gates that no longer exist.
- [ ] `EndWakeProcedureSuite.scala:121` "ending Wake selects nothing" — WEAK — strengthen — low. Only `isLeft`.
- [ ] `MinorActionsSuite.scala:206` "source-scoped fallback and replay use the recorded off-turn actor" — WEAK — strengthen — medium. The final `evolve(...).isRight` does not show replay uses the off-turn actor; assert the resulting state.
- [ ] `MinorActionsSuite.scala:154` "owned relic reveal rejects unheld and already-faceup relics" and `:164` "minor-action operation policy…" — WEAK — strengthen — low. Only `isLeft` and `isRight`.
- [ ] `MinorActionsSuite.scala:225` "locked restriction applies only faceup…" — DUPLICATE — low. Same behavior as DiscardRestrictionsSuite:45, through the walker; both asserts are only `isRight`/`isLeft`.
- [ ] `MinorActionsSuite.scala:92` (projector half) — COVERED-PLUMBING — trim — low. Owner-only `knownRelics` projection is application code in a gameplay suite. Check the application projector suites.
- [ ] `BackendArchitectureSuite.scala:409` "application projection collaborators stay bounded and layer-independent" — DUPLICATE — delete (keep the ScopedProjectionContext existence check if wanted) — high. The imports check is inside :400 (same forbidden imports, all of `application/`). The 800-line check is inside :480.
- [ ] `BackendArchitectureSuite.scala:464` "frontend production sources stay bounded and renderers remain isolated" — TYPE-PREVENTED + DUPLICATE — delete — high. The `frontend` sbt project does not `dependsOn` root, and frontend holds only `oathdigital.frontend`, so an application/gameplay/server import cannot compile. The 800-line part is inside :480.
- [ ] `BackendArchitectureSuite.scala:174` "legacy central power shell cannot return", `:330` "generic power operations are not independently replayable events", `:505` "retired setup and browser-memory symbols do not return" — OBSOLETE — delete — medium. They guard names from finished migrations. A grep of src/main, frontend/src and shared/src finds no trace of MajorActionPowerShell, CostsPaid, RelicPlacedAtSite, SetupEventWire or BrowserMemory.
- [ ] `BackendArchitectureSuite.scala:153` "central handler inventory equals the audited catalog vocabulary" — TAUTOLOGY (hash half) — keep as a tripwire or drop — low. It pins a literal sha256.
- [ ] `BackendArchitectureSuite.scala:162` "Recover registry uses exact power-ID data" — WEAK — strengthen — low. Only `nonEmpty`/`isEmpty` on a lookup.
- [ ] `LimitedResourceSuite.scala:4-13` (5 tests) — OTHER — merge into one table-driven test — low. Five tests for a one-line clamp with one caller (CardPlay.scala:193).
- [ ] `BackendArchitectureSuite.scala:24-162` (first 9 tests) — OTHER — move — low. These are real behavior tests of GameplayTransition, RuleSourceIndex and PowerResolver in an architecture suite. Not low value; moving them makes the source-text suite prunable on its own.

SOURCE-TEXT groups:
- [ ] `BackendArchitectureSuite.scala` has 19 source-text guards: :167 (no `rulesText` in gameplay), :174 (no MajorActionPowerShell), :183 (only CatacombsContribution names Catacombs), :203 (powers do not import the walker; the engine never names a power), :286 (no raw `.str` in protocol decoders), :330 (no CostsPaid/RelicPlacedAtSite events), :352 (power factories, not handler subclasses), :364 (RestPowers forbidden strings), :373 (no raw id tables in `*Powers.scala`), :400 (application layer imports), :409 (projection files), :429 (build.sbt wires shared sources), :436 (no duplicate intent DTOs), :446 (transport DTOs only in shared), :464 (frontend size and renderer imports), :480 (800-line cap), :490 (model/gameplay layer imports), :505 (retired symbols), :516 (owned-material writes need the `// executor bypass:` sentinel).
  - Guards worth keeping: :203, :286, :400, :490, :516 and :480 protect real boundaries the compiler cannot enforce (same sbt project).
  - Weaker: :364 and :373 are string heuristics. :429 is partly TYPE-PREVENTED; its test-source half catches shared tests silently not running.
  - :174, :330 and :505 are obsolete; :409 and :464 are covered above.
- [ ] `DiscardRestrictionsCoverageSuite.scala:33` and `:43` guard that every `Discard.Denizen/Vision/RuinedEdifice(` builder attaches `DiscardRestrictions`.
  - :43 is inside :33, since both files must contain the string.
  - The `delegated` map at :22 is stale: CardPlay.scala now builds `new DiscardRestrictions` itself (CardPlay.scala:29).
  - Action: drop :43 and the delegation entry. Medium.

### Leads to check in step 4

- [ ] The Forge start gates and the favor/secret funding checks may also be in `application/GameApplicationServiceSuite` (ForgeRulesSuite's doc names it as holding "the rejections").
- [ ] Owner-private walker decision hiding (CardPlayProcedureSuite:297) and known-relic visibility (MinorActionsSuite:92) are probably covered by `application/WalkerDecisionProjectionSuite`, `PhasePowerProjectorSuite` or other GameProjector suites.
- [ ] Catacombs through the walker: CatacombsContributionSuite's fixture is reused by `OathRulesWalkerPowerSuite`, `WalkerReplayDriftSuite`, `SelectionPaymentsSuite` and `powers/recover/RelicWorshipSuite`. Check those for the same placement and capacity assertions.
- [ ] `RecoverProcedureSuite` (N–Z) likely repeats the no-modifier relic-less start from CatacombsContributionSuite:82 and :91.
- [ ] "Replay equals live walk" tests appear in CampaignProcedure:357, CampaignRaid:120, Challenge:230, EndWake:151 and MinorActions:246 (plus PaidActionHarness in CampaignPlanWindow:133). `WalkerReplayDriftSuite` or an engine replay suite may already cover this generically.
- [ ] The Vow of Peace restriction is tested in CampaignPowersSuite:34 and CampaignProcedureSuite:191. There may be a third copy in a `powers/campaign` suite.
- [ ] Narrow Pass filtering (CampaignPowersSuite:90–132) may be duplicated in a `powers/travel` suite, since NarrowPassSitePower lives in TravelSitePowers.
- [ ] The Homeland discard rules are spread over HomelandRuleSuite, HomelandLineSuite and DiscardRestrictionsSuite:115/120. `PlacementFixture` users in N–Z (for example a PlacementRules suite) may repeat them.
- [ ] Stale doc comments ("Task 1", "Task 2", "not a registered walker action until Task 3") head ContributingPowerSuite, ContributionCollectorSuite, ForgeProcedureSuite and CatacombsContributionSuite. This is not a test-value issue, but it is a sign of migration leftovers.


## Gameplay suites N–Z

Scope: src/test/scala/oathdigital/gameplay/*.scala, N–Z.

### Survey summary

- Base path: `/Users/roman/projects/oathdigital/src/test/scala/oathdigital/gameplay/`. Every path below is relative to it.
- Suites read: 51, plus 4 fixture/helper files (PhasePowerFixture, PlacementFixture, WalkerDiceFixture, WalkerRecordedOpsReducer). Tests read: 421 `test(` sites. That is about 422 at runtime, because PendingWalkerRulesSuite generates 2 tests from one site.
- Findings by category: OBSOLETE 5, TAUTOLOGY 17 (6 of these are single lines inside otherwise useful tests), WEAK 19, DUPLICATE 22, COVERED-PLUMBING 2, OTHER 2, TYPE-PREVENTED 0, SOURCE-TEXT 0 (no suite in this partition reads source files).
- Whole-suite candidates:
  - `WalkerStateSuite` (3 tests): one sets case-class fields and reads them back, and two check trivial `Operation.flatten` behaviour.
  - `RestWalkerSuite` (5 tests): 3 duplicate RestSuite or WakeAutoEndSuite. Only :40 (phase and owner gates) and :54 (round end wakes the first player) are unique, so merge those two into RestSuite and delete the file.
  - `RuleResolutionSuite` (6 tests): 2 test `RuleRegistry`, which production never uses, 1 restates ReviewedPowerCatalog declarations, and only :75 and :84 carry weight.
  - `PowerKindsCatalogSuite` (6 tests): mostly restates catalog flags. Test :34 already fails for any flipped flag in the same lists.
  - `SupplyAdjustSuite` (4 tests): executor supply ops that OperationApplication, Resolution and Pipeline suites already cover. :60 tests nothing that :41 does not.

### Findings

- [ ] `OperationExecutorSuite.scala:60` "shadow comparison distinguishes parity rejection and mismatch" — OBSOLETE — delete (and delete `operations/OperationShadow.scala`) — high — `OperationShadowEvolution`, `OperationShadowResult` and `OperationShadowComparison` are defined in src/main but nothing in src/main calls them. This test is their only reference.
- [ ] `PowerOperationsSuite.scala:70` "DrawTopRelic and PlaceRelicAtSite preserve top-card and facedown rules" — OBSOLETE — delete (and delete the dead code) — high — `PowerOperationPlanner`, `PlaceRelicAtSite`, `DrawTopRelic` and `RelicPlacement` are used only inside `PowerOperations.scala` itself. Nothing else in src/main calls them; CatacombsContribution mentions one only in a comment.
- [ ] `PowerOperationsSuite.scala:91` "power operation plans apply in order and fail without a partial result" — OBSOLETE — delete — high — Built on the dead `PowerOperationPlanner.placement`. Line 117 checks the immutable input `ready`, and the empty-batch check duplicates `OperationPipelineSuite:34` and `OperationExecutorSuite:117`.
- [ ] `RuleResolutionSuite.scala:22` "registry lookup is explicit and unknown relevant handlers are safe" — OBSOLETE — delete — high — `RuleRegistry` and `TypedRuleHandler` have no use in src/main outside `RuleResolution.scala`.
- [ ] `RuleResolutionSuite.scala:31` "resolution ordering is priority source identity then handler ID" — OBSOLETE — delete — high — Same dead `RuleRegistry.resolve`.
- [ ] `WalkerStateSuite.scala:21` "a walker PendingTree with a dice pool slot stores in state and reads back" — TAUTOLOGY — delete — high — It sets `walkerPending` and `rollPools` with `copy` and asserts the same values back. No production code runs.
- [ ] `WalkerStateSuite.scala:48` "a Decide leaf flattens to itself" — TAUTOLOGY — delete — medium — A trivial base case of `Operation.flatten`. Every walker suite exercises it.
- [ ] `PlacementRulesSuite.scala:22` "the default rules are the printed limit of three and no site discard" — TAUTOLOGY — delete — medium — Restates `PlacementRules.default` and `DefaultAdviserLimit` constants. `powers/AdviserLimitSuite:12` pins the same limit of three.
- [ ] `PayCostSuite.scala:63` "Costs.onCard names the card's suit bank" — TAUTOLOGY — delete — medium — The expected value is the implementation expression itself (`PayCost(actor, OnCard(card), cost, …, catalog.suitOf(card))`, PowerOperations.scala:43).
- [ ] `PowerKindsCatalogSuite.scala:28` "every in-scope modifier is catalogued non-persistent" — TAUTOLOGY — merge into :34 — medium — Restates catalog data. `CatalogResolution.of` is exactly `persistent → Automatic else PlayerSelected`, so :34 over the same list fails on any flipped flag. The only gap is a missing id; add `flag(id).isDefined` to :34 to cover it.
- [ ] `PowerKindsCatalogSuite.scala:31` "every in-scope persistent rule is catalogued persistent" — TAUTOLOGY — merge into :34 — medium — Same reasoning as :28.
- [ ] `ProcedureWalkerSuite.scala:850` "a Restriction violation rejects the command with no events appended" — TAUTOLOGY — strengthen (drop the second half, rename) — high — Lines 866-869 build the "command" inside the test (`violations.headOption.toLeft(()).flatMap(…)`), so `Left(violation)` only repeats the first assertion. `OathRulesWalkerPowerSuite:107` drives the real command.
- [ ] `RuleResolutionSuite.scala:47` "reviewed handlers use precise windows resolution and implementations" — TAUTOLOGY — delete or reduce to a behavioural check — medium — Restates the handler declarations in `ReviewedPowerCatalog` entry by entry. Only the last `validateSources(...).isLeft` exercises logic.
- [ ] `PowerResolverSuite.scala:44` "major action vocabulary contains only selectable major actions" — TAUTOLOGY — delete — low — Restates `MajorActionType.all`. Keep it only if the order matters to `PreviewModifierDescriptions`' `find`, in which case rename the test to say so.
- [ ] `PowerResolverSuite.scala:54` "windows expose typed major-action associations independent of keys" — TAUTOLOGY — delete — low — Each value is a `final val` on a sealed trait (PowerWindow.scala:24), so the test re-reads declarations.
- [ ] `TakeWealthPowerSuite.scala:106` "the use ref is the one the procedure records" — TAUTOLOGY — delete, or rename as a persisted-format pin — low — Its own comment says both sides go through `useRef`, so drift is impossible. It only has value if the literal is a journaled wire key.
- [ ] `SupplyAdjustSuite.scala:60` "staged adjustments apply in order" — TAUTOLOGY — delete — medium-high — Two separate `execute` calls with commutative subtraction cannot show ordering or staging. `OperationExecutorSuite:94` is the real staged-batch test.
- [ ] `OperationExecutorSuite.scala:82` "policy receives the semantic root before primitive execution" (line 92) — TAUTOLOGY (one line) — strengthen (drop line 92) — high — It asserts the immutable input `ready` is unchanged.
- [ ] `OperationExecutorSuite.scala:107` "later failure returns no partial execution result" (line 116) — TAUTOLOGY (one line) — strengthen — high — Same immutable-input check. The remaining `.isLeft` is weak, so assert the specific error.
- [ ] `OfferHostSuite.scala:126` "a host can ask whether an operation would run…" (line 146) — TAUTOLOGY (one line) — strengthen — medium — "Asking changed nothing" reads the immutable `state` val, so it cannot fail.
- [ ] `TravelProcedureSuite.scala:148` "zero Supply passes the semantic build and fails at the pay node" (line 162) — TAUTOLOGY (one line) — strengthen — high — Line 162 checks the pawn site of the input `ready`. `rejected.isLeft` should assert `CoreOperationRejected("insufficient-supply", …)`.
- [ ] `RecoverEligibilitySuite.scala:71` "beginRecover is offered without a relic or Catacombs" (line 84) — TAUTOLOGY (one line) — strengthen — low — Line 84 asserts the setup it just built.
- [ ] `SearchProcedureSuite.scala:19` "world Search starts from one generic source argument and parks on card selection" — WEAK — strengthen — high — It only checks `isInstanceOf[Parked]`, not that the park is `SearchProcedure.cardDecisionId`.
- [ ] `SearchProcedureSuite.scala:160` "Search uses its registered modifier-selection window" — WEAK — strengthen — high — With no powers, `offerable == empty` and an unknown modifier id is rejected whatever window Search uses. Use a window-scoped power as in `OathRulesWalkerPowerSuite:405`.
- [ ] `SearchProcedureSuite.scala:96` "a faceup Conspiracy from Search with nothing to take is played and boxed" — WEAK — strengthen — medium-high — It never checks that the card was boxed. Add the `CardIndex` check that `VisionPlaySuite:171` uses.
- [ ] `PlacementRulesSuite.scala:36` "without a contributor the tree is the play under the default rules" — WEAK — strengthen — high — It asserts `children.size == 2` and `head.isInstanceOf[Decide]`, never that the body's rules equal `PlacementRules.default`.
- [ ] `OfferHostSuite.scala:68` "a transform at the host's window runs before the expansion" — WEAK — strengthen — medium — The supply sum is commutative, so it would still be 4 if the transform ran after the expansion. Only "the transform ran" is proven.
- [ ] `NegotiationProcedureSuite.scala:180` "closing a deal runs the action boundary, whether declined or agreed" — WEAK — strengthen — medium-high — The agreed half checks only `walkerPending == None`, which `:92` already covers. Assert `BanditsRefilled` for the agreed path too.
- [ ] `TravelProcedureSuite.scala:174` "role and Foundation changes do not gate Travel" — WEAK — strengthen — medium-high — It changes the role only. Foundations are never altered, although the title claims it.
- [ ] `SelectionPaymentsSuite.scala:64` "Catacombs states its secret as a selection payment" — WEAK — strengthen — medium — `selectionPayments(...).size == 1` is a count with no content. Assert the exact `PayCost`.
- [ ] `OperationStateAdapterSuite.scala:15` "card lookup maps precise containers to semantic locations" — WEAK — strengthen — medium-low — Six `.isRight` checks. It never checks the returned card or its state.
- [ ] `OperationStateAdapterSuite.scala:69` "secret orientation and private knowledge remain separate from location" — WEAK — strengthen — medium — `knows` is only asserted true for ids the setup inserted, so a `knows` that always returned true would pass. Add a negative case. The secrets assertion restates the fixture.
- [ ] `OperationApplicationSuite.scala:108` "counted move into an incompatible destination is invalid" — WEAK — strengthen — low-medium — Checks only that the Impossible and Invalid kinds exist, not the reason codes.
- [ ] `ProcedureWalkerSuite.scala:236` "an optional composite still shrinks best-effort" — WEAK — strengthen — medium — `favor > tooMuchFavor - 1` does not pin the shrunk amount. Assert the exact favor.
- [ ] `OperationPipelineSuite.scala:89` "a card that is not a Vision cannot be moved to the shared bank" — WEAK — strengthen — medium-low — `.isLeft` only; assert the rejection code. `:100` has the same issue.
- [ ] `PayCostSuite.scala:39` "a placed cost onto an occupied card is rejected" — WEAK — strengthen — low — `.isLeft` only. `PayCostSettlementSuite:64` has the same issue.
- [ ] `RevealDiscardSuite.scala:28` and `:32` — WEAK — strengthen — low-medium — `.isLeft` only; assert the error codes.
- [ ] `PhasePowerSuite.scala:90` "another player, the wrong phase and an inaccessible source are refused" — WEAK — strengthen — medium-low — The wrong-phase and unknown-card cases are `.isLeft` only. The same applies to `:161` and `:168`.
- [ ] `TakeWealthProcedureSuite.scala:185` "the start selection must name exactly one known resource" — WEAK — strengthen — low — Four `.isLeft` checks. `:160` and `:176` use `isInstanceOf` only.
- [ ] `OperationExecutorSuite.scala:446` "a held banner cannot be claimed from the shared bank" — WEAK / DUPLICATE — merge into `OperationApplicationSuite:122` — medium — `.isLeft` on the same move that OperationApplicationSuite:122 checks by exact code.
- [ ] `VisionPlaySuite.scala:211` "a Vision answer naming a site is rejected" — WEAK — strengthen — low — `.isLeft` only.
- [ ] `TravelProcedureSuite.scala:94` "a coast route replaces the cost with one" — DUPLICATE — delete (keep :97) — high — Same call and same expected value as `:97`. The `assertNotEquals(…, Some(3))` in :97 adds nothing after `assertEquals(…, Some(1))`.
- [ ] `RestWalkerSuite.scala:72` "the last player of round eight finishes the game by War Exhaustion" — DUPLICATE — delete — high — Same setup and same deterministic port as `RestSuite:333`, which asserts a superset.
- [ ] `RestWalkerSuite.scala:89` "walker Begin Rest records the Rest fallback diagnostics first" — DUPLICATE — delete — high — `RestSuite:249` checks the same `events.head` handlerId for naysayers (and two more handlers).
- [ ] `RestWalkerSuite.scala:29` "Begin Rest with no usable REST power finishes Rest in the same command" — DUPLICATE — delete — medium — The completed vector, next player and phase appear in `RestSuite:69` and `WakeAutoEndSuite:60`. Replay is covered by `RestSuite:198`.
- [ ] `WakeAutoEndSuite.scala:67` "taking the last wealth ends Wake" — DUPLICATE — merge into `TakeWealthProcedureSuite:111` — medium — Same `WalkerCompleted(TakeWealth, EndWake)` and Act phase. Only the replay line is new.
- [ ] `RepeatPassSuite.scala:31` "a pass that records something repeats while its guard holds" — DUPLICATE — delete — medium-high — `ProcedureWalkerSuite:407` tests a state-guarded Repeat over a delta through the same `advance`, and pins the exact ops.
- [ ] `RecoverProcedureSuite.scala:110` "Recover rolls its own dice: the walk parks on the choice, never on the roll" — DUPLICATE — merge into `:214` — medium-high — `:214` walks the same script with the same park paths and asserts each roll's faces (line 264).
- [ ] `PowerOperationsSuite.scala:30` "Costs plans a PayCost that places favor and burns secrets atomically" — DUPLICATE — merge into `:122` — medium — `:122` pays all four portions, a superset of this case's two.
- [ ] `PowerOperationsSuite.scala:53` "Costs accepts a free cost…" — DUPLICATE (first half) — merge into `:135` — medium — The free-cost `flatten == empty` check is repeated in `:135`. Only the facedown-card half is unique.
- [ ] `PayCostSettlementSuite.scala:77` "the active player's payment still rests on the card" — DUPLICATE — delete, or keep one side — medium — Same active-player `PayCost(Cost(1,1))` giving `Tokens(1,1)` as `PayCostSuite:34`.
- [ ] `SelectionPaymentsSuite.scala:57` "each payment alone is affordable, so the refusal is about the pair" — DUPLICATE — merge into :49 — medium-high — `muster(1, a)` is already in `:44`. `b` is identical to `a` except for its name, so `muster(1, b)` adds nothing.
- [ ] `TravelSitePowersSuite.scala:109` "each terrain transform changes cost from its no-power baseline" — DUPLICATE — delete — medium-high — The baseline with no candidates equals the base by construction, and `assertNotEquals` is weaker than the exact values `:82` pins for the same routes.
- [ ] `TravelSitePowersSuite.scala:82` "terrain transforms reproduce the retired terrain fold's parity table" — DUPLICATE — keep one level — medium — `TravelProcedureSuite:81-104` asserts the same terrain numbers (+1, +2, coast replace, coast beats island, coastal source on a plain route) through `candidates`. Both suites' docs are migration-parity leftovers.
- [ ] `TakeWealthPowerSuite.scala:72` "a second take at the same site this turn is blocked" — DUPLICATE — keep, but delete or strengthen `TakeWealthProcedureSuite:160` — low — The two tests have the same title. The procedure-level one checks only `isInstanceOf[PowerAlreadyUsed]`.
- [ ] `OathRulesWalkerPowerSuite.scala:185` "a seated non-active player's RollWalker … is rejected" — DUPLICATE — merge into `:205` — low-medium — `:205` makes the same `rollWalkerPrepared(intruder)` call and also proves the check runs before the rebuild.
- [ ] `OathRulesWalkerPowerSuite.scala:453` "Recover parity: its offerable set is what the Recover-window literal returned…" — DUPLICATE / migration leftover — merge with `:405` — low — The empty-catalog case is trivial, and the "literal" parity refers to a finished migration.
- [ ] `OperationVocabularySuite.scala:29` "BuryableCard.Vision buries to the bottom of the world deck" — DUPLICATE — delete — medium — Reads the derived `bury.to` field. `:35` proves the Vision ends up at the bottom of the deck.
- [ ] `RestrictionAnswersSuite.scala:50` "the answers default to none, so every existing caller is unchanged" — DUPLICATE — delete — high — Pins a default parameter. `:43`'s `violations(Vector.empty)` already covers the same case.
- [ ] `RecoverEligibilitySuite.scala:35` and `:49` "beginRecover is offered when a facedown relic…" / "…once Catacombs is face-up there" — DUPLICATE / OBSOLETE — delete — medium — The suite doc says relics do not gate Recover, and `:71` shows the action offered with neither relic nor Catacombs. These look like leftovers from when relics and Catacombs did gate it.
- [ ] `ProcedureWalkerSuite.scala:840` "a node with no window records contributions as Vector.empty" — DUPLICATE — delete — low — `:686` asserts empty contributions for an unpowered step. `:114` could absorb the check.
- [ ] `SiteDiscardFirstSuite.scala:116` "the permission composes with an adviser limit through the walker" — DUPLICATE (first half) — trim — low — The adviser-limit half repeats `PlacementRulesSuite:54`.
- [ ] `NegotiationDealSuite.scala:124` "settlement is refused when an author can no longer afford their terms" — DUPLICATE (first half) — low — `NegotiationProcedureSuite:151` checks the same `InsufficientFavor(3, 1)`. The second half is `.isLeft` only.
- [ ] `TakeWealthPowerSuite.scala:116` "the walker catalog registers the limit" — COVERED-PLUMBING — delete — medium-high — `TakeWealthProcedureSuite:160` runs on `WalkerPowerCatalog.default` and fails if the limit is not registered.
- [ ] `TravelSitePowersSuite.scala:179` "Walker catalog registers every Travel site contribution" — COVERED-PLUMBING — delete if registration is all-or-nothing — low — TravelProcedureSuite covers one site per terrain. The catalog lists more (several coast and mountain sites), so this only holds if they are registered as one group.
- [ ] `PlayerSecretSummarySuite.scala:28` "derived secret accounting reports available facedown committed and total" — OTHER — trim — low — The row `withState(1,0,0)` appears twice (lines 30 and 34). The `totalSecrets` assertion restates its definition (PlayerSecretSummary.scala:8).
- [ ] `OathRulesWalkerPowerSuite.scala:489` "a Roll park under an action declaring no roll decision id…" — OTHER — trim line 507 — low — Line 507 re-pins `WalkerProcedureRegistry.rollDecisionId(Recover)`, which the test's own comment says `walker/WalkerProcedureRegistrySuite` already pins.

### Leads to check in step 4

- [ ] `PlacementRulesSuite:22` and `:78` (default limit 3, Silver Tongue holder limit) against `powers/AdviserLimitSuite:12` and `:16`.
- [ ] `SelectionPaymentsSuite:64` and `:74` (Catacombs selection payment and refusal) against `CatacombsContributionSuite`.
- [ ] `ProcedureWalkerSuite:278-369` (Decide answer shape, undeclared option, partition minimum, malformed query) against `walker/DecisionQuerySuite`.
- [ ] `ProcedureWalkerSuite:522-644` (roll count, face kind, skulls and score) against `walker/WalkerRollsSuite`.
- [ ] `OptionRestrictionSuite` and `RestrictionLookAheadSuite` (query narrowing, emptied optional decision skipped) against `walker/WalkerPreviewSuite` and `walker/DecisionQuerySuite`.
- [ ] `OperationExecutorSuite` and `OperationApplicationSuite` (Move, Draw, Bury, Swap and Replace semantics) against `operations/OperationMutationSuite` and `operations/OperationTreeSuite`.
- [ ] `StateBasedEvaluationSuite:55-105` (goal qualification, Protection ties) against `oathkeeper/OathkeeperRulesSuite`. Round-eight War Exhaustion is tested three times: StateBasedEvaluation:214, RestSuite:333 and RestWalkerSuite:72.
- [ ] `RecoverProcedureSuite` against `GameApplicationServiceSuite`'s walker Recover replay test, which WalkerReplayDriftSuite's doc cites. Also against `powers/recover/*`.
- [ ] `RestSuite:249-331` (IgnoredRulesRecorded diagnostics) and `RuleResolutionSuite:75` against `powers/PowerImplementationStatusSuite`.
- [ ] `PhasePowerSuite` against the phase-power suites under `powers/` (wake, rest, action). PhasePowerFixture's doc mentions a projection suite that shares it.
- [ ] `VisionPlaySuite:91` and `:171` (Conspiracy) against `powers/cardplay` and `powers/whenplayed` suites.
- [ ] `TakeWealthProcedureSuite:221` and `RecoverEligibilitySuite` (projector `legalControls`) against the application projector suites.
- [ ] `OathRulesWalkerPowerSuite:348`: its comment says the unknown-modifier-id case is already covered in `GameApplicationServiceSuite`. Check whether that suite also covers the inapplicable-id case.


## Powers: action, banner, targeting, title

Scope: gameplay/powers/ (top level), action/, banner/, targeting/, title/.

### Survey summary

- Suites read: 42, tests read: 355. Fixtures read: PowerFixture, TargetsFixture, SearchFixture, CardStaging, NoteText, PaidActionHarness, MovementFixture, BannerFixture, TargetingFixture. Production code checked: PhasePowerProcedure, PowerImplementationStatus, SelectedModifier, and the resolution wiring of the targeting powers. Other suites checked: PhasePowerSuite, PowerAccessSuite, PowerKindsCatalogSuite, PowerNoteWalkerSuite.
- Findings by category: DUPLICATE 57 (32 of them are one repeated pattern), TAUTOLOGY 5, WEAK 5, COVERED-PLUMBING 2, OTHER 1, partial-trim 1 bullet. No SOURCE-TEXT, OBSOLETE or TYPE-PREVENTED found.
- Whole-suite candidates: none fully.
  - PowerImplementationStatusSuite is mostly low value: 8 of its 13 tests only restate registry membership.
  - PowerNoteCatalogSuite has 3 of 7 low-value tests.
- Recurring pattern: 32 suites open with "X is a registered phase power". Every other test in the same suite already proves it (evidence in the first finding).

### Findings

- [ ] The "X is a registered phase power" tests — DUPLICATE — delete — high. Every other test in each suite calls `use(...)` / `startWalker(UsePower)` with `PhasePowerCatalog.default(catalog)`. That reaches `PhasePowerProcedure.find` (src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala:148), which returns Left "no phase power is registered" when the power is absent. So all the `.toOption.get` calls would already fail. Locations:
  - `action/AlchemistSuite.scala:32`, `action/BarbedNetSuite.scala:47`, `action/BoneDiceSuite.scala:22`, `action/BookOfRecordsSuite.scala:50`
  - `action/BrassHorseSuite.scala:28` (its extra `usable(staged)` is also covered by every later test)
  - `action/CharmingFriendSuite.scala:38`, `action/ClayRattleSuite.scala:50`, `action/CrystalVialSuite.scala:56`, `action/DemonTailSuite.scala:18`, `action/DowsingSticksSuite.scala:20`, `action/ElderSuite.scala:24`, `action/FaeMerchantSuite.scala:26`, `action/GamblingHallSuite.scala:24`, `action/IvoryEyeSuite.scala:44`, `action/MagicCarpetSuite.scala:24`, `action/MagicWaterskinSuite.scala:23`, `action/MurkyFountainSuite.scala:35`, `action/OracleSuite.scala:71`, `action/OracularPigSuite.scala:40`, `action/ScryerSuite.scala:38`, `action/ShiftingMapSuite.scala:18`, `action/SiegeEnginesSuite.scala:50`, `action/SleightOfHandSuite.scala:36`, `action/SpiritSnareSuite.scala:30`, `action/SpoiledSuppliesSuite.scala:39`, `action/TutorSuite.scala:22`, `action/WaysideInnSuite.scala:27`, `action/WhistleSuite.scala:19`, `action/WizardSchoolSuite.scala:22`, `action/WolvesSuite.scala:26`
  - `banner/WanderingFlameMoveSuite.scala:24` and `banner/WanderingFlamePlaceSuite.scala:21`: these also assert the id string constant (TAUTOLOGY). Place's `assertNotEquals` with Move's id is already implied by `WanderingFlamePlaceSuite.scala:84`, whose usable set holds both ids.
- [ ] `targeting/CircletOfCommandSuite.scala:22` "the Circlet is a registered persistent rule, so it is automatic" — DUPLICATE + TAUTOLOGY — delete — high. `resolution` is `CatalogResolution.of(catalog, id)`, and `PowerKindsCatalogSuite` "resolution follows the flag" already asserts Automatic for `relic.circlet-of-command`. `cardId == circlet` restates catalog data. Registration is never checked, despite the title. Every behavior test in the suite proves the rule applies without being selected.
- [ ] `targeting/ForgottenVaultSuite.scala:35` "the Vault is a registered persistent rule, so it is automatic" — DUPLICATE + TAUTOLOGY — delete — high. Same evidence as the Circlet: `denizen.forgotten-vault` is in PowerKindsCatalogSuite's persistentRules.
- [ ] `targeting/FortressRulesSuite.scala:37` "the Fortress faces are registered persistent rules, so they are automatic" — DUPLICATE + TAUTOLOGY — delete — high. `edifice.e28.intact` and `edifice.e28.ruined` are both pinned Automatic in PowerKindsCatalogSuite. The `id == PowerId("edifice.e28.intact")` line restates a constant.
- [ ] `banner/PeoplesFavorMobSuite.scala:62` "Mob is a registered persistent rule, so it is automatic" — DUPLICATE + TAUTOLOGY — delete — high. Every behavior test drives `SearchFixture.rules`, which uses `WalkerPowerCatalog.default`, with no modifiers selected, and Mob fires. That proves both registration and automatic resolution. `id.value == "banner.peoples-favor.mob"` restates a constant.
- [ ] `title/ChaosCultSuite.scala:43` "Chaos Cult is in the default walker catalog and automatic" — DUPLICATE — delete — medium.
  - `PowerKindsCatalogSuite` "Chaos Cult is a persistent rule…" already asserts `resolution == Automatic`.
  - Default-catalog membership is what `PowerImplementationStatusSuite.scala:64` checks for `denizen.chaos-cult`.
  - This suite's own rules use `WalkerPowers(Vector(power))`, so this is the only in-suite membership check. That is why confidence is medium.
- [ ] `PowerImplementationStatusSuite.scala:31, :36, :41, :47, :52, :56, :59, :64` (the 8 "Vision-play cards" / "catalog batch 2 …" tests) — DUPLICATE / TAUTOLOGY — delete (or collapse into one table test) — medium.
  - For a power in a walker or phase catalog, `implemented(id)` is plain membership (`covered.contains(id)`).
  - The predicate's branches are covered by the tests at :15, :18, :21, :24 and :28.
  - Each listed power's own suite already runs it through the default catalog.
  - These tests pin data, not behavior.
- [ ] `AdviserLimitSuite.scala:12` "the limit is three by default" — DUPLICATE + TAUTOLOGY — delete — medium. `AdviserLimit.Default == 3` restates `PlacementRules.DefaultAdviserLimit`. The no-Silver-Tongue value of 3 is already asserted by :16 (the other players) and :23 (facedown).
- [ ] `PowerNoteCatalogSuite.scala:31` "Gambling Hall and Vow of Peace declare their notes" — TAUTOLOGY — delete — medium. It restates the two powers' `noteKeys` literals. The structural checks at :17 and :21 cover all declared notes.
- [ ] `PowerNoteCatalogSuite.scala:42` "a banner option names its banner as a note's source" — DUPLICATE — merge into `PowerNoteWalkerSuite.scala:115` (same function, other cases) — medium. The banner case is also exercised by the WanderingFlame note tests: `NoteSupport` maps the source via `PowerSourceRef.of`, so without it no note would be written.
- [ ] `PowerNoteCatalogSuite.scala:53` "the Homeland rule declares the line it writes" — TAUTOLOGY — delete — high. `RuleNotes.all` is the one-entry literal `Vector(homelandDiscard -> Vector(PlacementRules.discardFirst))`. The line itself is tested at `HomelandLineSuite.scala:37` and `GameLogPowerLinesSuite.scala:345`.
- [ ] `SelectedModifierSuite.scala:80` "at the action's eligibility window a selected modifier always applies" — WEAK — strengthen — high. `Probe` keeps the default `appliesAt = true`, so the test still passes if the `isEligibility(ctx.window) ||` branch in `SelectedModifier.applicable` is removed. Give Probe `appliesAt = false`.
- [ ] `SelectedModifierSuite.scala:91` "a payment and an effect at the same window keep the payment first" — WEAK — strengthen — high. It only asserts `.size == 2` and never checks the order the title names.
- [ ] `SelectedModifierSuite.scala:85` "the payment is what selecting it pays, for the combined check" — WEAK — strengthen — medium. `.size == 1` has no content. Assert it equals `Costs.onCard(actor, card, Cost(favor = 1), catalog)`.
- [ ] `SelectedModifierSuite.scala:37` "its resolution is read from the catalog" — COVERED-PLUMBING — delete — low. It tests a one-line wiring to `CatalogResolution.of`. PowerKindsCatalogSuite pins tents as PlayerSelected and toll-roads as Automatic, and the modifier scenario suites depend on it.
- [ ] `SelectedModifierSuite.scala:103` "the catalog cards helper finds a power's card, or nothing" — COVERED-PLUMBING — keep or delete — low. The success paths run in every `forCatalog(...).get` (Circlet, Vault, economy powers). Only the `None` case is unique.
- [ ] `banner/BannerFaceSourcesSuite.scala:26` "the reviewed catalog audits the banner powers…" — WEAK — strengthen — low. It asserts only `.isRight`. If the audit were removed, the test still passes.
- [ ] `banner/BannerFaceSourcesSuite.scala:33` "banner faces are not catalogued…" — TAUTOLOGY — delete — medium. It asserts catalog data absence (`printedPower(...) == None`) for three ids and exercises no rules code.
- [ ] `action/GamblingHallSuite.scala:27` "the bank question says how much the roll won" — TAUTOLOGY — delete, or fold into :34 as a heading check on the parked query — low. It restates the format string of `bankHeading`.
- [ ] `action/FaeMerchantSuite.scala:121` "its returned line tells its relic choice" — TAUTOLOGY — delete — high. It asserts `narratedDecisions == Set(decisionId)`, which is literally the override at `FaeMerchant.scala:24`. The consumer is `NoteWordings`, so a real check belongs in a GameLog suite.
- [ ] `action/IvoryEyeSuite.scala:103` "the actor's own facedown adviser is a legal target" — WEAK / DUPLICATE — delete, or strengthen with a knowledge or note assertion — medium. It asserts only `done.nonEmpty`. The offered-options test at :47 already includes the actor's own facedown slots (`facedown(ready)` covers all players, size 4).
- [ ] `action/SpiritSnareSuite.scala:44` "a bank holding one favor gives it and is left empty" — DUPLICATE of :33 "one stocked bank gives a favor without asking" — merge — low. Same path, and 1 versus 3 in the bank does not seem to hit a distinct branch.
- [ ] `action/WaysideInnSuite.scala:47`, `action/WolvesSuite.scala:72` and `action/DowsingSticksSuite.scala:55` ("unusable again while its card holds …") — DUPLICATE of `PhasePowerSuite.scala:147` (the generic empty-card rule) — delete — medium.
- [ ] `action/MagicWaterskinSuite.scala:46`, `action/DowsingSticksSuite.scala:60` and `action/MagicCarpetSuite.scala:87` ("a facedown … cannot be used") — DUPLICATE of `PowerAccessSuite.scala:62` "a relic in your play area must be faceup" — delete — low/medium. They run through the real power, but the gate is the generic `PowerAccess`.
- [ ] `action/GamblingHallSuite.scala:104` "a Gambling Hall at a site the actor rules is usable from another site" — DUPLICATE of `PhasePowerSuite.scala:99` (ruled-site source) — delete — medium/low.
- [ ] `action/WaysideInnSuite.scala:53` "it is usable in the Act phase only" — DUPLICATE of `PhasePowerSuite.scala:90` (wrong phase refused) — delete — low. It does pin WaysideInn's declared timing.
- [ ] Partial trims — DUPLICATE assertions — trim — low/medium. The occupied-card or facedown halves of mixed tests repeat the generic PhasePowerSuite:147 and PowerAccessSuite:62 rules. Keep the per-power cost-amount halves:
  - `action/GamblingHallSuite.scala:94`, `action/CrystalVialSuite.scala:136`, `action/IvoryEyeSuite.scala:121`, `action/FaeMerchantSuite.scala:112` and `action/MurkyFountainSuite.scala:85`: trim the second halves.
  - `action/BoneDiceSuite.scala:65`, `action/BrassHorseSuite.scala:97` and `action/WhistleSuite.scala:62`: trim the occupied and facedown parts.
- [ ] `action/BrassHorseSuite.scala:72` and `:79` ("…any other site may be chosen") — WEAK — strengthen — low. Each asserts only that one site's answer `.isRight`. Assert the offered set, or the pawn's final site as :63 does.
- [ ] `targeting/FortressRulesSuite.scala:90` "a faceup beast adviser lifts the Rotting Fortress's protection" — OTHER (mislabelled) — strengthen — medium. The `facedown` value is built with `adviserOf(..., Suit.Hearth)`, which is a faceup Hearth adviser (`TargetingFixture.adviserOf` is always faceup). So the facedown-Beast case the name implies is untested.

### Leads to check in step 4

- [ ] The "is a registered phase power" pattern also appears in `powers/wake/RiverSitePowerSuite`, `wake/HornedMaskSuite`, `wake/MarbleFountainsSuite`, `cardplay/BookBindersSuite`, `cardplay/GossipSuite`, `travel/GraspingVinesSuite` and `travel/TollRoadsSuite`. They probably have the same subsumption.
- [ ] `PowerImplementationStatusSuite` vs `application/GamePresentationProjectorImplementedSuite` (its doc comment says they are kept in sync) and `PowerKindsCatalogSuite`. There is likely a triple pin of the same power lists.
- [ ] Silver Tongue's limit of 2 (`AdviserLimitSuite`) is probably asserted again in `powers/rest/` (SilverTongue suite) and in placement suites.
- [ ] Per-power gates ("unusable while card holds X", facedown relic, wrong phase, ruled-site source) are probably repeated in the wake/ and rest/ suites against PhasePowerSuite and PowerAccessSuite.
- [ ] `PaidActionHarness.wireRoundTrips` runs in about 25 suites here. If a GameEventWire suite already round-trips every event type, the per-power wire checks add little beyond integration. Check the serialization suites.
- [ ] `PowerSourceRef.of` is tested in both `PowerNoteWalkerSuite:115` and `PowerNoteCatalogSuite:42`.


## Powers: the other folders

Scope: gameplay/powers/ campaign/, cardplay/, economy/, recover/, rest/, search/, setup/, travel/, wake/, whenplayed/.

### Survey summary

- Suites read: 60, tests read: 450 (plus the fixtures PowerFixture, CardStaging, SearchFixture, TargetsFixture, PlanDriver, VisionPlayFixture, TravelFixture, RiverFixture, LeagueTreatyFixture, SilverTongueFixture and WhenPlayedHarness, and the production code where a verdict needed it). All paths below are under `/Users/roman/projects/oathdigital/src/test/scala/oathdigital/gameplay/powers/`.
- Findings by category: TAUTOLOGY 22, DUPLICATE 26, WEAK 18, TYPE-PREVENTED 1, COVERED-PLUMBING 3, OBSOLETE 1, SOURCE-TEXT 0, OTHER 0. Total 71.
- Whole-suite candidates:
  - `setup/EdificeSetupSupportSuite.scala`: its 2 tests unit-test `siteOf`, a helper that the Forge and Grounds rule suites already drive (low confidence, see below).
  - No other suite is mostly low value. The waste sits in three patterns repeated across many suites:
    - A "registered …" test in each suite that restates constructor constants.
    - A "found in the catalog and registered once" count in each campaign suite.
    - `.isLeft` refusal checks that don't say why the action was refused.

### Findings

**Registration tests that restate constants (TAUTOLOGY).** Each of these checks `cardId`, `actions`, `cost` and `resolution` on the power object. In `DragonskinDrum` and `Augury`, `actions` is a hard-coded `def` (I checked the source). `resolution` is already pinned for all of these ids by `gameplay/PowerKindsCatalogSuite` "resolution follows the flag". `actions` and `cost` are covered by each suite's own "only for X" and pay tests, except where noted.
- [ ] `cardplay/WildCrySuite.scala:27` "Wild Cry is a registered selected Search modifier" — TAUTOLOGY — delete — high — the `actions` field is covered by the :33 offered test.
- [ ] `cardplay/WelcomingPartySuite.scala:25` "Welcoming Party is a registered selected Search modifier" — TAUTOLOGY — delete — medium — its `offerableWalkerPowers` line is implied by every `play(…, modifiers, …)` that succeeds.
- [ ] `cardplay/GossipSuite.scala:29` "Gossip is a registered persistent rule, so it is automatic" — TAUTOLOGY — delete, along with the private `WalkerPowerCatalogHas` object — high — the behaviour tests go through the default catalog (`SearchFixture.rules`).
- [ ] `economy/AnimalPlaymatesSuite.scala:41` "…registered free selected Muster modifier" — TAUTOLOGY — delete — medium — `Cost.free` is not asserted anywhere else (the Supply tests don't check favor or secrets).
- [ ] `economy/BirdsongSuite.scala:45` "…registered free selected Trade modifier" — TAUTOLOGY — delete — medium — same caveat about `Cost.free`.
- [ ] `economy/CupOfPlentySuite.scala:41` "the Cup is a registered selected Trade modifier" — TAUTOLOGY — delete — high — the :71 test covers `actions`.
- [ ] `economy/KnightsErrantSuite.scala:90` "Knights Errant is a registered selected Muster modifier" — TAUTOLOGY — delete — high.
- [ ] `economy/RowdyPubSuite.scala:37` "Rowdy Pub is a registered selected Muster modifier" — TAUTOLOGY — delete — high.
- [ ] `recover/RelicWorshipSuite.scala:53` "…registered selected Recover modifier that costs a secret" — TAUTOLOGY — delete — high — the :60 test proves the secret is paid onto the card.
- [ ] `search/AugurySuite.scala:23` "Augury is a registered selected Search modifier" — TAUTOLOGY — delete — medium — nothing else checks that it is not offered for other actions.
- [ ] `search/TruthfulHarpSuite.scala:28` "the Harp is a registered selected Search modifier" — TAUTOLOGY — delete — high.
- [ ] `travel/DragonskinDrumSuite.scala:16` "the Drum is a registered selected Travel modifier" — TAUTOLOGY — delete — medium — same `Cost.free` caveat.
- [ ] `travel/ForestPathsSuite.scala:21` "…registered selected Travel modifier that costs 1 favor" — TAUTOLOGY — delete — high — :27 asserts the favor is placed on the card.
- [ ] `travel/GraspingVinesSuite.scala:21` "…registered persistent rule, so it is automatic" — TAUTOLOGY — delete — high.
- [ ] `travel/RoyalStablesSuite.scala:17` "…registered free selected Travel modifier" — TAUTOLOGY — delete — medium — same `Cost.free` caveat; :68 covers `actions`.
- [ ] `travel/TentsSuite.scala:17` "Tents is a registered selected Travel modifier that costs 1 favor" — TAUTOLOGY — delete — high — covered by :29 and :66.
- [ ] `travel/TollRoadsSuite.scala:22` "Toll Roads is a registered persistent rule, so it is automatic" — TAUTOLOGY — delete — high.

**Registration presence already proven by behaviour tests or another suite (DUPLICATE).**
- [ ] `cardplay/SacredGroundSuite.scala:31` "Sacred Ground is a registered, implemented rule" — DUPLICATE — delete — high — every other test runs through the default catalog. `PowerImplementationStatusSuite` "the Vision-play cards are implemented" already lists e08.
- [ ] `cardplay/SecretPoliceSuite.scala:40` "Secret Police is a registered, implemented rule" — DUPLICATE — delete — high — same reasons as Sacred Ground.
- [ ] `cardplay/BookBindersSuite.scala:39` "Book Binders is a registered persistent rule, so it is automatic" — DUPLICATE — delete — high — resolution is covered by PowerKindsCatalogSuite, presence by the behaviour tests.
- [ ] `cardplay/VowOfObedienceSuite.scala:31` "…registered as a walker rule and a REST power, and is implemented" — DUPLICATE — delete — high:
  - Automatic resolution is covered by PowerKindsCatalogSuite "Vow of Obedience's rule is automatic".
  - The walker part is covered by the Search tests, the phase part by the REST tests (`PhasePowerCatalog.default`).
  - "Implemented" is covered by PowerImplementationStatusSuite.
- [ ] `campaign/GleamingArmorSuite.scala:145` "the card is registered once and is automatic…" — DUPLICATE — delete — medium — Automatic is covered by PowerKindsCatalogSuite; the "once" part joins the group below.
- [ ] `wake/HornedMaskSuite.scala:48` "Horned Mask is a registered phase power" — DUPLICATE — delete — high — `TargetsFixture.use` starts the power by id through `PhasePowerCatalog.default`.
- [ ] `wake/MarbleFountainsSuite.scala:38` "Marble Fountains is a registered phase power" — DUPLICATE — delete — high — the suite's `rules` use the default phase catalog.
- [ ] `wake/HungerSuite.scala:60` "Hunger is a registered, forced WAKE power" — DUPLICATE — delete — medium — :65 proves it is found, forced and parks in the Wake.
- [ ] `whenplayed/ConspiracyWhenPlayedSuite.scala:98` "the default walker catalog carries the Conspiracy power" — DUPLICATE — delete — medium — BookBindersSuite:126 and :166 park on the Conspiracy decision through the default catalog.
- [ ] `whenplayed/FamilyHeirloomSuite.scala:28` "Family Heirloom is in the default walker catalog" — DUPLICATE — delete — high — WhenPlayedDecisionAfterPlaySuite runs Heirloom through `SearchFixture.rules` (the default catalog).
- [ ] Not reported: the same presence test for FaithfulFriend, Garrison, ASmallFavor, TwinBrother and ShiftingFog. Those suites use the harness, so that test is their only proof of catalog wiring. Keep them, or fold all of them into one catalog-wide test.

**"The card is found in the catalog and registered once" (campaign).** These assert `SimplePlans/PlanRules.forCatalog(catalog).count(_.id == X) == 1` over a hand-written list. Every behaviour test already proves the plan is registered at least once, and the only extra thing they guard is a doubled entry in that list.
- [ ] `campaign/FireTalkersSuite.scala:57` — DUPLICATE — merge into one "no power id registered twice" test over `WalkerPowerCatalog.default(catalog).powers.map(_.id)` — medium. The same applies to:
  - `campaign/MercenariesSuite.scala:137`
  - `campaign/HorseArchersSuite.scala:73`
  - `campaign/HospitalSuite.scala:187`
  - `campaign/LongbowsSuite.scala:37`
  - `campaign/BlackSwordSuite.scala:36`
  - `campaign/NatureWorshipSuite.scala:73`
  - `campaign/CrackedSageSuite.scala:61`
  - `campaign/StormCallerSuite.scala:52`
- [ ] `campaign/RampartSuite.scala:93` "the two faces are two powers of one card, registered once each" — DUPLICATE plus TAUTOLOGY — merge into the uniqueness test — medium — it also asserts the literal id strings "edifice.e20.intact" and "edifice.e20.ruined", which only restates constants.
- [ ] `campaign/BagOfSiegeworksSuite.scala:84` "the reviewed-catalog stub is retired and the plan registered once" — OBSOLETE — delete — medium-high — `!CampaignPowers.powers.exists(_.id == BagOfSiegeworks.id)` guards a finished migration. `CampaignPowers.powers` now lists only VowOfPeace, Outriders, BrassArmyCampaign and Watchdog.

**Other duplicates**
- [ ] `economy/KnightsErrantSuite.scala:192` "a nested Campaign that is allowed is not stopped by a restriction once it is under way" — DUPLICATE of :136 "a Campaign run this way finishes, and the Muster ends after it" — delete — high — the steps are the same (`staged()` with no restriction, campaign, force 0, `walkerProcedure == None`). :136 asserts more, and this test sets up no restriction despite its title.
- [ ] `search/AugurySuite.scala:77` "without the selection the draw is the printed three" — DUPLICATE of :29, whose first two lines assert `start(withAugury(top))` draws `top.take(3)` from the same input — delete — high.
- [ ] `whenplayed/ShiftingFogSuite.scala:20` "the next bank to the right, Nomad's going to Discord" — DUPLICATE of :24 — delete — medium — :24 uses banks 1..6 and asserts the full result vector, which fixes `next` completely.
- [ ] `cardplay/WildCrySuite.scala:103` "a hook walked with the power alone applies it once" — DUPLICATE of :49 — delete — medium — :49 asserts exactly +1 Supply through the real rules, so a double application would already fail.
- [ ] `cardplay/BookBindersSuite.scala:92` "the Conspiracy triggers it" — DUPLICATE of :126 and :166 — delete — medium — it pokes the Transform internals (single `Branch` node, `Decide.owner`), while :126 proves through the rules that Conspiracy parks on Book Binders' choice. It is also brittle to restructuring.
- [ ] `campaign/StickyFireSuite.scala:109` "the same Raid without Sticky Fire kills half the board" — DUPLICATE — merge as a contrast into :94 or delete — low — it tests baseline Raid losses. `gameplay/CampaignRaidSuite.scala:53` "a Raid victory…" already asserts the defender's board warbands.

**WEAK (would pass with the behaviour removed)**
- [ ] `campaign/FearsomeShieldSuite.scala:38` "with fewer than two faceup secrets the defender cannot pay, and the plan is not offered" — WEAK — strengthen with `assert(!run.offered(b.actor).contains(ref))` — high — `run.finish` answers Finish, so no `PayCost` occurs even if the shield is offered.
- [ ] `campaign/FearsomeShieldSuite.scala:47` "the relic must be faceup…" (first half) — WEAK — strengthen the same way — high — `commit(...)` picks nothing, so `count(PayCost) == 0` holds whatever is offered. The attacker half is fine.
- [ ] `travel/GraspingVinesSuite.scala:62` "a facedown Grasping Vines is not active" — WEAK — strengthen by placing a facedown Vines at the ruled site with `denizenAt` — high. The test gives the actor a facedown adviser, but `SiteRulers.rulerOfCard` only finds faceup site cards, so a Vines adviser never kills in either orientation.
- [ ] `travel/TollRoadsSuite.scala:70` "a facedown Toll Roads is not active" — WEAK — strengthen the same way (a facedown card at plains(1)) — high — Toll Roads also uses `rulerOfCard`, so an adviser Toll Roads is never active.
- [ ] `wake/HungerSuite.scala:187` "Hunger cannot be used as an optional power" — WEAK (and DUPLICATE of :65's `usable(...)` check) — delete — medium-high — the state it builds from comes from `rested(staged(FaceDown))`. :130 shows that state has moved on to Phase.Act, so any WAKE `UsePower` is refused for the phase.
- [ ] `economy/KnightsErrantSuite.scala:146` "it cannot be selected for a Campaign, or for any other action" — WEAK (last assertion) — drop that line or give the board enough Supply — medium-high — `staged()` has 1 Supply and a Campaign costs 2 (see :122/:132), so `startWalker(Campaign, modifiers).isLeft` fails for want of Supply. The `offered(...)` assertions are the real content.
- [ ] `setup/ProvingGroundsRulesSuite.scala:37` "Empty Grounds discards every other denizen in its region" — WEAK — strengthen by staging other denizens first, as :76 does — medium-high:
  - The fixture guard `remainingOtherDenizens(staged)` is always true, because the staged edifice itself matches `e.id == edifice`.
  - :76 says "The fixture's region holds no other card".
  - So "only the edifice remains" holds without the power.
  - Line 29 (`PlayerFacts.forceKind(...).toOption.get`) is a dead statement.
- [ ] `setup/GreatForgeRulesSuite.scala:39` "Broken Forge discards every relic in its region to setAsideRelics" — WEAK — strengthen by asserting `setAsideRelics` contains `relicsBefore` — medium — it only checks that one site's relics are empty and never checks the destination the title names.
- [ ] `search/AugurySuite.scala:42` "the player keeps any one of the drawn cards" — WEAK — strengthen by placing the 4th card `adviser-facedown` and asserting it is held — medium — the "discard" placement plus "temporary hand empty" holds for any finished Search. Only the helper not throwing shows the 4th card was kept.

**`.isLeft` refusal checks with no reason.** Each would also pass if the action were refused for an unrelated cause. Strengthen by asserting that `offerableWalkerPowers` does not contain the id (as CupOfPlenty and AnimalPlaymates do) or by matching the violation.
- [ ] `cardplay/WildCrySuite.scala:42` "it is not offered when the card is facedown or out of reach" — WEAK — strengthen — medium.
- [ ] `search/AugurySuite.scala:81` "it is not offered when the card is out of reach" — WEAK — strengthen — medium-low.
- [ ] `search/TruthfulHarpSuite.scala:76` "a facedown Harp cannot be selected" — WEAK — strengthen — medium-low.
- [ ] `travel/DragonskinDrumSuite.scala:44` "a facedown Drum is not usable" — WEAK — strengthen — medium-low.
- [ ] `travel/ForestPathsSuite.scala:73` "it cannot be selected without a favor to place" — WEAK — strengthen — medium-low.
- [ ] `travel/TentsSuite.scala:57` "it cannot be selected without a favor to place, or onto an occupied card" — WEAK — strengthen — medium-low.
- [ ] `travel/TentsSuite.scala:73` "it is not offered when the card is facedown" — WEAK — strengthen — medium-low.
- [ ] `travel/RoyalStablesSuite.scala:64` "it may not be used at a site the actor neither stands at nor rules" — WEAK — strengthen — medium-low.
- [ ] `recover/RelicWorshipSuite.scala:119` "it cannot be selected without a faceup secret, or onto an occupied card" — WEAK — strengthen — medium-low.

**Other categories**
- [ ] `travel/DragonskinDrumSuite.scala:40` "a Travel that is rejected gains nothing" — TYPE-PREVENTED — delete — high — it asserts only `travel(0 Supply).isLeft`. That tests Travel's Supply check, not the Drum, and a `Left` carries no state that could "gain" anything.
- [ ] `wake/RiverSitePowerSuite.scala:34` "the actor's pawn starts at Ancient City, a River" — TAUTOLOGY — delete, or turn into a `require` in RiverFixture — high — it checks the test fixture, not production.
- [ ] `wake/RiverSitePowerSuite.scala:28` "every River is a registered phase power" — TAUTOLOGY — delete — low — it restates the catalog's four river ids. Only Ancient City and Riverbank are otherwise exercised, so keep it if the other two matter.
- [ ] `whenplayed/FamilyHeirloomSuite.scala:69` "its notes tell its decision" — TAUTOLOGY — delete — medium — it asserts `narratedDecisions == Set(decisionId)`, a declared constant, and no narration outcome.
- [ ] `whenplayed/ConspiracyTargetWindowSuite.scala:67` "the target decision carries the Conspiracy target window" — TAUTOLOGY (first assertion only) — drop the `key == "conspiracy.target-selection"` line — low — the rest of the test is meaningful.
- [ ] `campaign/WarningSignalsSuite.scala:152` "the recorded moves replay to the same state, and survive the journal wire" — COVERED-PLUMBING — delete if a generic journal/wire scenario covers `DistributeAnswer` events — low — this is a serialization round-trip, not rule behaviour.
- [ ] `setup/EdificeSetupSupportSuite.scala:11` "siteOf finds an edifice staged on a side, and misses the other side" — COVERED-PLUMBING — delete — low — `siteOf` is a lookup that GreatForgeRules and ProvingGroundsRules call on every staged edifice. But no rule test checks that the wrong face does not fire (for example, Ruined E22 not granting warbands), so the "misses the other side" half is only partly covered.
- [ ] `setup/EdificeSetupSupportSuite.scala:21` "siteOf finds nothing when the edifice is not on the board" — COVERED-PLUMBING — delete — low — same helper.

### Leads to check in step 4

- [ ] **Resolution of every in-scope modifier and persistent rule** is pinned by `gameplay/PowerKindsCatalogSuite` ("resolution follows the flag", plus the Vow of Obedience and Dazzle tests). The per-card `resolution` asserts above repeat it.
- [ ] **Registration of Vision-play and batch-2 powers** is pinned by `powers/PowerImplementationStatusSuite` (e08, secret-police, book-binders, vow-of-obedience, shifting-fog, twin-brother, hunger, the battle plans). One catalog-wide "every power id is registered exactly once" test could replace the ~12 campaign "registered once" checks and the when-played "is in the default walker catalog" checks.
- [ ] **Generic rejection of an option the decision did not offer** is re-tested per power:
  - HungerSuite:114
  - TwinBrotherSuite:75
  - FamilyHeirloomSuite:82
  - GarrisonSuite:85
  - ConspiracyWhenPlayedSuite:181
  - HornedMaskSuite:181 (second half)
  - SilverTongueSuite:39 (the Order line)

  These are likely covered by walker/decision-validation suites. Check `gameplay/walker/*` and `RestrictionLookAheadSuite`.
- [ ] **Warband-sacrifice plans:** `gameplay/CampaignPlanWindowSuite.scala:233/252` test Raid-defender sacrifice from the board, and not offering it with no warband, through a synthetic plan. `campaign/WrestlersSuite.scala:48/55` test the same through the production card.
- [ ] **Adviser limit:** `powers/AdviserLimitSuite` tests Silver Tongue's limit. `rest/SilverTongueSuite:69/101` and `wake/HornedMaskSuite:126/149` test it again through card play and Horned Mask. They use different entry points, so this is probably intended, but worth a look.
- [ ] **Replay and wire checks:** `PaidActionHarness.replayed` and `wireRoundTrips` are embedded in about 15 behaviour tests here (Tents, Drum, Cup, Birdsong, RowdyPub, AnimalPlaymates, Augury, Harp, Gossip, WelcomingParty, WildCry, RelicWorship, GraspingVines, TollRoads, ForestPaths, RoyalStables, HornedMask). If a generic journal round-trip scenario exists (for example `WalkerReplayDriftSuite`), these lines are plumbing that could be dropped per the scenario-first rule.
- [ ] **Baseline Raid losses:** `StickyFireSuite:109` and part of `HospitalSuite:155` overlap `gameplay/CampaignRaidSuite.scala:53/93`.


## Walker, model, catalog, persistence, serialization

Scope: gameplay/ oathkeeper/, operations/, setup/, walker/; model/, catalog/, engine/, testkit/, persistence/, serialization/, presentation/.

### Survey summary

- Suites read: 48, tests read: 330. I also read the fixtures and helpers: FirstGameSetupFixture, SetupWalkDriver, ParkedDecisionAssertions, TestGameFixtures and ReadyGames. I grepped src/main to check whether code is used.
- Findings by category: TAUTOLOGY 16, WEAK 9, DUPLICATE 9, COVERED-PLUMBING 1, TYPE-PREVENTED 3, SOURCE-TEXT 2, OBSOLETE 6, OTHER 1 (47 bullets).
- Whole-suite candidates:
  - `presentation/VisualResolverSuite`: the whole `oathdigital.presentation` package (ViewModel, VisualResolver) is referenced only by `PresentationExample`, and nothing references that.
  - `model/ChronicleSuite`: 2 of its 3 tests are tautologies. The 80-line TTS sample only feeds `.size` checks. The only real test is "at most three items".
  - `model/EnumShapeSuite`: a guard left over from the enum migration (added in 3c0cfae6). It restates constants and duplicates ActionKindSuite and ActionValuesSuite.
  - `model/CampaignWindowsSuite`, `ChallengeWindowsSuite`, `NegotiationWindowsSuite`: they restate `PowerWindow.key` literals. Nothing in src/main reads those keys: there is no `fromKey` and no serializer.
  - `model/GameStartedSuite`: one tautology plus one key pin.

### Findings

- [ ] `src/test/scala/oathdigital/model/GameStartedSuite.scala:11` "GameStarted carries a Chronicle and its resolved deal order" — TAUTOLOGY — delete — high — Builds a case class and reads its own fields back. GameEventWireSuite:768 already round-trips the real event.
- [ ] `src/test/scala/oathdigital/model/ChronicleSuite.scala:89` "constructs from the TTS sample's shape with its section sizes" — TAUTOLOGY — delete (along with the 80-line name lists) — high — Asserts the sizes and distinctness of vectors the suite itself wrote. `Chronicle` has no logic on that path.
- [ ] `src/test/scala/oathdigital/model/ChronicleSuite.scala:104` "reliquary, foundations and lineages default empty for a first game" — TAUTOLOGY — delete — medium — Restates case-class default parameters.
- [ ] `src/test/scala/oathdigital/model/WorldModelSuite.scala:178` "banner faces change without changing physical banner families" — TAUTOLOGY — delete — high — Checks that `copy` of one field leaves the other fields alone, which is case-class semantics.
- [ ] `src/test/scala/oathdigital/model/WorldModelSuite.scala:206` "site tokens represent current loose favor and secrets" — TAUTOLOGY — delete — high — Constructs a `SiteState` with tokens and reads the same tokens back.
- [ ] `src/test/scala/oathdigital/model/CampaignWindowsSuite.scala:4` "the Campaign windows have stable keys and belong to the Campaign action" — TAUTOLOGY — delete, or keep a one-line trait check — medium — Nothing in src/main reads `PowerWindow.key` (no `fromKey`, not serialized). `associatedMajorAction` is a `final val` on the sealed trait each window extends.
- [ ] `src/test/scala/oathdigital/model/CampaignWindowsSuite.scala:31` "every Campaign window key is distinct" — DUPLICATE — delete — high — Test :4 already pins each of these keys to a distinct literal, and this test covers only a subset of those windows.
- [ ] `src/test/scala/oathdigital/model/ChallengeWindowsSuite.scala:4,16` "Challenge windows carry…" / "Place Banner Resource windows are not tied…" — TAUTOLOGY — delete — medium — Same reason as CampaignWindowsSuite:4.
- [ ] `src/test/scala/oathdigital/model/NegotiationWindowsSuite.scala:4` "Negotiation windows have stable keys and no major action" — TAUTOLOGY — delete — medium — Same reason.
- [ ] `src/test/scala/oathdigital/model/SupplySuite.scala:31` "the Chancellor uses the distinct Imperial refresh bands" — TAUTOLOGY — delete — high — The Chancellor band table exists only in the test. src/main has only `FinishRestProcedure.ExileSupply`, so nothing in production uses Chancellor bands.
- [ ] `src/test/scala/oathdigital/model/SupplySuite.scala:23,27` "an Exile with nine or more…" / "saved Supply moves the effective marker…" — WEAK — strengthen — medium — The tests use a hand copy of the Exile bands. They would still pass if `FinishRestProcedure.ExileSupply` (public) changed. Use it instead.
- [ ] `src/test/scala/oathdigital/model/ReadyGameSuite.scala:50` "the test builder seats players in order and starts the active player" — TAUTOLOGY — delete — medium — Tests the test helper `ReadyGames.of`, not production code.
- [ ] `src/test/scala/oathdigital/model/ReadyGameSuite.scala:34,42` "updateCurrent…/updateCampaign changes … and nothing else" — TAUTOLOGY — delete — low — Tests one-line `copy` wrappers.
- [ ] `src/test/scala/oathdigital/model/CardIndexSuite.scala:98` "the fixture satisfies structural domain invariants" — TAUTOLOGY — delete, or merge into PlayerSetupStateSuite:6 — low — Checks the fixture rather than CardIndex. The negative tests use `contains`, so they do not need the fixture to be clean.
- [ ] `src/test/scala/oathdigital/catalog/CatalogLoaderSuite.scala:68` "catalog powers round-trip the shared PowerId type" — DUPLICATE — delete — high — :51 already asserts the same first power's id. `power.id.value` is the identity on an opaque String, so it can't fail.
- [ ] `src/test/scala/oathdigital/catalog/CatalogLoaderSuite.scala:204` "the production catalog contains only the final runtime corpus" — TAUTOLOGY — keep only if it is the intended data-drift alarm — low/medium — Restates counts from the catalog data (255/48/30/36/24, restriction histogram). Any catalog edit breaks it.
- [ ] `src/test/scala/oathdigital/catalog/CatalogLoaderSuite.scala:254,309` "printed relic values…" / "production sites retain verified printed gameplay data" — TAUTOLOGY — keep or trim — low — Point checks of catalog data values, not loader logic. The loader decoding is already covered by the fixture tests.
- [ ] `src/test/scala/oathdigital/catalog/CatalogLoaderSuite.scala:331` "legacy selection flags do not produce partial catalogs" — OBSOLETE — delete along with `CatalogSelection` — medium — CatalogModel.scala says the "selection flags are intentionally ignored by CatalogLoader". It is a migration shell that ServerRuntime still passes, and the test pins only sizes.
- [ ] `src/test/scala/oathdigital/catalog/CatalogLoaderSuite.scala:128` "reviewed runtime-power mirror exactly preserves authoritative structures" — SOURCE-TEXT — keep, or move to the ingestion pipeline — medium — Compares two checked-in JSON files and runs no Scala code. It guards that `reference/catalog-ingestion/reviewed-runtime-powers.json`, which `build_runtime_catalog.py` reads, matches the docs catalog.
- [ ] `src/test/scala/oathdigital/model/OpaqueIdSuite.scala:8,32` "ids construct, extract and expose their value" / "UserId wraps any string and prints raw" — TYPE-PREVENTED — delete — medium — Added in the opaque-types commit 6850781a. For `opaque type X = String`, `value` and `unapply` are the identity. The validation parts are real and are already in :15.
- [ ] `src/test/scala/oathdigital/model/OpaqueIdSuite.scala:23` "an id prints as its raw string" — TYPE-PREVENTED — keep or delete — low — Guaranteed while the ids stay opaque. It only matters if someone turns them back into case classes, and log text depends on this.
- [ ] `src/test/scala/oathdigital/model/OpaqueIdSuite.scala:37` "ViewId keeps its validation and prints raw" — OBSOLETE — delete along with the presentation package — medium — `ViewId` lives only in the unused `presentation/ViewModel.scala`.
- [ ] `src/test/scala/oathdigital/presentation/VisualResolverSuite.scala:19-49` (all 5 tests) — OBSOLETE — delete along with the package — medium-high — `VisualResolver`, `BoardView` and `duplicateIds` have no caller in src/main, shared or frontend apart from `PresentationExample`, which is itself unreferenced.
- [ ] `src/test/scala/oathdigital/model/EnumShapeSuite.scala:4` "enum cases keep their names, keys and hand-written order" — OBSOLETE — delete, keeping any wire pin that is not duplicated — medium — A guard from the enum migration. `toString`/`productPrefix` come free with Scala enums. `ActionKind.fromKey("when-played")` duplicates ActionKindSuite:9. The TriggeredProcedureRef keys duplicate ActionValuesSuite:31 and GameEventWireSuite:194. `FoundationNumber.IV.value` and `Role.Chancellor.isImperial` restate constants.
- [ ] `src/test/scala/oathdigital/model/WorldModelSuite.scala:54,74,93,125,156` (the five Atlas tests) — OBSOLETE — delete, or keep if the Chronicle end-of-game work is imminent — medium — `AtlasState.addRecent`, `removeRecent`, `removeForgotten`, `mostRecent` and `mostForgotten` have no caller in src/main.
- [ ] `src/test/scala/oathdigital/persistence/HsqldbIdentityRepositorySuite.scala:88,120` (and the seeding in :69 and :137) "trusted seat creation rejects…" — OBSOLETE — delete 88 and 120, and reseed 69 and 137 through `HsqldbTrustedGameStore` — medium — `IdentityRepository.createTrustedSeats` has no production caller. Production writes `trusted_seats` through `HsqldbTrustedGameStore.create` (TrustedGameProvisioning). :120's null player is also TYPE-PREVENTED. `resolveTrustedSeat` is production code (TrustedSeatRoutes), so keep coverage of it.
- [ ] `src/test/scala/oathdigital/persistence/HsqldbIdentityRepositorySuite.scala:346,355` "session schema contains only the digest…" / "trusted seat schema…" — SOURCE-TEXT — keep — high (that these are layout guards) — They read table column names through `private[persistence]` accessors that exist only for these tests. They protect the rule that no raw bearer token or seat code is stored.
- [ ] `src/test/scala/oathdigital/gameplay/operations/OperationTreeSuite.scala:11` "composites flatten depth-first" — WEAK — strengthen — high — Asserts only `.size == 2`, so the depth-first order in the title is never checked. Assert the favor leaf, then the secret leaf.
- [ ] `src/test/scala/oathdigital/gameplay/setup/SetupProcedureSuite.scala:12` "each player places a pawn, then chooses an adviser, in turn order" — WEAK — strengthen or rename — high — Checks only the first park (the first player's pawn decision), not the order.
- [ ] `src/test/scala/oathdigital/gameplay/setup/GameStartRulesSuite.scala:46` "a stored denizen on an atlas site is refused" — WEAK — strengthen to the exact `UnsupportedChronicle` message — high — atlasBox(0) is Deep Woods, a Homeland. Replacing its items with a denizen drops its edifice, so the Homeland check ("has no stored edifice") would still return `Left` if the stored-denizen check were removed.
- [ ] `src/test/scala/oathdigital/gameplay/setup/GameStartRulesSuite.scala:59` "an unknown denizen id is refused" — WEAK — strengthen to `UnsupportedChronicle("unknown denizen no-such-denizen")` — medium — It checks only `isLeft`.
- [ ] `src/test/scala/oathdigital/gameplay/operations/OperationMutationSuite.scala:97` "a pawn already on a site cannot move from the player area again" — WEAK — strengthen to the violation code — medium — Only `isLeft`, and any rejection of that Move passes.
- [ ] `src/test/scala/oathdigital/persistence/HsqldbEventStreamRepositorySuite.scala:83` "rejects newer and non-contiguous schema ledgers and releases files" — WEAK — strengthen — medium — `result.left.toOption.nonEmpty` passes for any open failure, including an I/O error.
- [ ] `src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala:56` "a tree that never parks, or runs an operation first, or parks on another query shape has nothing to preview" — WEAK — strengthen — low/medium — Three bare `isLeft` checks. The three reasons are not told apart.
- [ ] `src/test/scala/oathdigital/gameplay/operations/ShuffleOperationSuite.scala:36` "a shuffle the walker never ordered is refused" — WEAK — strengthen — low — Bare `isLeft`.
- [ ] `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala:112` "a partition query accepts a placement of every option into a declared section" — DUPLICATE — delete — high — Its answer is exactly `legalPlacement`, which :281 already accepts against the same `partition` query.
- [ ] `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala:756` "current state-based Usurper events round trip" — DUPLICATE — delete — high — :729 round-trips the same `UsurperFlipped`/`UsurperVictory` pair and also checks formatVersion 1.
- [ ] `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala:311` "Catacombs' recorded batch round-trips…" — DUPLICATE — merge into :439 — medium — :439 already contains the same `Move` (Deck(Relic) top → Site, facedown) and the same `PayCost(OnCard)` op. Only the four JSON field-shape assertions are unique here.
- [ ] `src/test/scala/oathdigital/persistence/HsqldbEventStreamRepositorySuite.scala:174` "persists and reloads exact Oathkeeper evaluation records" — COVERED-PLUMBING — delete — medium-high — The repository stores opaque strings. Reopen fidelity is covered by :299, and the wire round trip of these events by GameEventWireSuite:78 and :729.
- [ ] `src/test/scala/oathdigital/persistence/HsqldbIdentityRepositorySuite.scala:23` "identity migration is idempotent and survives close and reopen" — DUPLICATE — delete — medium — Both Owned* wrappers delegate `initializeSchema`/`schemaVersion` to the same `HsqldbDatabaseOwner`. That makes this a repeat of HsqldbEventStreamRepositorySuite:74, and the reopen/owner-membership half repeats HsqldbDatabaseOwnerSuite:82.
- [ ] `src/test/scala/oathdigital/persistence/HsqldbEventStreamRepositorySuite.scala:299` "close and reopen preserve the authoritative stream" — DUPLICATE — merge with HsqldbDatabaseOwnerSuite:82 — low/medium — The owner test already reopens and reloads the journal records. Only the append-at-sequence-2 after reopen is extra.
- [ ] `src/test/scala/oathdigital/gameplay/setup/GameStartToWakeSuite.scala:22` "driving every player's two decisions starts the first turn with the recorded seating" — DUPLICATE — delete — medium — Uses the same `Situation.wake(Situation.rules(catalog))` entry as SituationSuite:39, which asserts the same things plus whole-state equality.
- [ ] `src/test/scala/oathdigital/gameplay/setup/GameStartToWakeSuite.scala:16` "beginGame parks on the first player's pawn-placement decision" — DUPLICATE — keep one — low — SituationSuite:90 makes the same `assertParked(Setup, pawnDecisionId(first), first)` after `Begin`, and SetupProcedureSuite:12 checks the same first park. The entry layers differ.
- [ ] `src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala:115,127,133,172` Forge/Travel entry facts, Begin/Finish Rest fallbackKind, "only Muster and Trade require a playable option" — TAUTOLOGY — keep unless a scenario covers the routing — low — Each restates the registry's constant data. The scaladoc argues a wrong value would misroute silently.
- [ ] `src/test/scala/oathdigital/testkit/TableSuite.scala:41,45` "the turn passes p1, p2, p3" / "boards keep their printed start…" — TAUTOLOGY — delete or fold into :17 — low — Restate the fixture's seating and printed board constants.
- [ ] `src/test/scala/oathdigital/testkit/TableSuite.scala:190,195,203` "real commands run from a table: Rest / Muster / Travel" — OTHER — merge into one — low — Each re-runs a rule that its own action suite covers. The value here, that a Table state is accepted as a real game, needs only one of them.
- [ ] `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala:189,430,489` negative-minimum and negative-range cases — TYPE-PREVENTED — keep — low — Could be made impossible with a non-negative count type. As written, they are cheap defensive checks.

### Leads to check in step 4

- [ ] Trusted seats: suites outside this partition call the dead `createTrustedSeats` API as a fixture: server/TrustedSeatRoutesSuite, SessionSecuritySuite, TrustedGameProvisioningSuite and application/MembershipAuthorizationServiceSuite. Check whether any test drives the production writer `HsqldbTrustedGameStore` against a real DB, since my partition has no suite for it.
- [ ] The Oathkeeper title changing at the action boundary (OathkeeperProcedureSuite:40, :107) and the SetOathkeeper executor tests (OperationMutationSuite:43, :52) are likely repeated in gameplay/StateBasedEvaluationSuite and the Travel/TakeWealth suites.
- [ ] Round trips of `UsurperFlipped`/`UsurperVictory`/`RoundEnded`/`WarExhaustionResolved` may also appear in gameplay/StateBasedEvaluationSuite and application/gamelog/GameLogEventSuite.
- [ ] Table's "real commands" tests (Rest/Muster/Travel) overlap with the action and phase suites: Muster, Travel, FinishRest.
- [ ] ParkedDecisionAssertionsSuite's Recover parks and SituationSuite:114 may overlap RecoverProcedureSuite and application/ParkedServiceFixture users.
- [ ] WalkerProcedureRegistrySuite's rollDecisionId and Forge-entry facts are exercised end to end in OathRulesWalkerPowerSuite and GameApplicationServiceSuite, which its own scaladoc names.
- [ ] PowerWindow `associatedMajorAction` is also used in gameplay/PowerResolverSuite, ContributionCollectorSuite, CardPlayHooksSuite and ConspiracyTargetWindowSuite. Check whether those make the window suites redundant.
- [ ] The setup→Wake flow (GameStartToWakeSuite, SetupProcedureSuite:27) is probably also covered by the application begin/bootstrap scenarios (GameApplicationServiceSuite, GameServerGatewaySubmitBeginSuite).
- [ ] DecisionQuerySuite's negotiate `accepts` cases may be repeated in the Negotiation procedure suites.


## Application and server

Scope: application/ (with gamelog/), server/.

### Survey summary

- Suites read: 68 suites plus 4 fixtures (ForgeWalkerFixture, ParkedServiceFixture, GoldenLog, LogScripts). Tests read: 405 declarations. GameLogGoldenSuite's single declaration expands to 26 tests at runtime.
- Findings by category: TAUTOLOGY 7, WEAK 7, DUPLICATE 18, COVERED-PLUMBING 5, TYPE-PREVENTED 0, SOURCE-TEXT 1, OBSOLETE 0 (stale titles are counted under OTHER), OTHER 4.
- Whole-suite candidates: none is wholly low value. The closest are:
  - `server/GameHttpWireSuite`: about 5 of 13 tests are low value. It is also misfiled: most of its tests exercise `GameIntentMapper` (application), not HTTP wire.
  - `ChronicleFirstGamePlanSuite`: 1 of 3 tests is redundant.
  - `ServerConfigSuite`: 4 of 17 tests overlap other tests or delegated validators.

### Findings

- [ ] `application/MembershipAuthorizationServiceSuite.scala:16` "pluggable authenticator returns a provider-neutral principal" — TAUTOLOGY — delete — high — Defines an anonymous `Authenticator` inside the test, then asserts that the stub returns what it was written to return. No production code runs.
- [ ] `application/ChronicleShuffleSuite.scala:19` "the random port shuffles without changing membership" — TAUTOLOGY — delete — medium-high — `ChronicleRandomPort.random.shuffle` is just `rng.shuffle(values)` (ChronicleShuffle.scala:13), so the test checks `scala.util.Random`.
- [ ] `application/PricedOptionProjectionSuite.scala:45` "a price never affects which answer names the option" — TAUTOLOGY — delete — medium — `Priced.ref` is defined as `option.ref`, and `isFree` is `this == OptionPrice()` (Decisions.scala:184, :222). The test restates both definitions.
- [ ] `application/GameApplicationServiceSuite.scala:58` "withWorldDeckTop preserves two absent requested denizens of one suit" — TAUTOLOGY (tests a helper) — delete, or move to a fixture self-check — medium — It only exercises `ParkedServiceFixture.withWorldDeckTop`. A misplacement would already fail the Hunger, augury and other LogScripts users.
- [ ] `application/gamelog/GameLogDecisionSuite.scala:32` "the narrated list names setup, card play, campaign and negotiation" — TAUTOLOGY — delete — medium — It restates the `NarratedIds` / `NarratedPrefixes` constant (DetailLines.scala:200). The behaviour it protects is checked end to end by :24 "decisions an action line already tells post no Chose line".
- [ ] `server/GameHttpWireSuite.scala:25` "development and authenticated transports decode the same actorless intent" — TAUTOLOGY — delete — high — `GameHttpWire.decodeCommand` is literally `AuthenticatedGameHttpWire.decodeCommand` (GameHttpWire.scala:18-19), so the two results are always equal. The round trip is covered by shared `CommandProtocolSuite:57`.
- [ ] `server/GameHttpWireSuite.scala:36` "actor injection is rejected by both transports" — TAUTOLOGY — delete — medium-high — Comparing the two transports is guaranteed to pass for the same reason as :25. The `$.intent.playerId` path is covered by `CommandProtocolSuite:64`.
- [ ] `application/ChronicleFirstGamePlanSuite.scala:15` "the dealt orders still satisfy GameStartRules" — WEAK/DUPLICATE — delete — high — It only checks `.isRight`. Test :11 already asserts the orders equal `FirstGameSetupFixture.orders`, and those orders are begun successfully everywhere (for example GameHistorySuite:7).
- [ ] `application/gamelog/GameLogSetupSuite.scala:16` "every player places a pawn and keeps an adviser, in turn order" — WEAK — strengthen — high — It only counts lines. Turn order is never checked.
- [ ] `application/gamelog/GameLogActionLineSuite.scala:66` "a successful Recover names the relic to the recoverer only" — WEAK — strengthen, or delete in favour of the property test — high — The check `!theirs.exists(Card) || theirs.spans == mine.spans` passes exactly when the other seat is leaked the same card span. The leak property is really covered by GameLogPropertiesSuite:52.
- [ ] `application/gamelog/GameLogActionLineSuite.scala:28` "Play Facedown Adviser: a start line, then where the card went" — WEAK/DUPLICATE — delete — medium — It checks only that some "Played" or "Discarded" line exists, never the order. The same ground is covered more tightly by :111 in this suite, by GameLogDetailSuite:57, and by the facedown-adviser golden.
- [ ] `server/TrustedSeatRoutesSuite.scala:64` "trusted preview binds resolved actor and rejects a different game" — WEAK — strengthen — medium — The preview for the wrong player is only `.isLeft`, so any failure passes. Assert the specific error.
- [ ] `application/PhasePowerProjectorSuite.scala:109` "real League Treaty and Silver Tongue parks project their panels" — WEAK — strengthen — low-medium — "Panel" is checked only as `isInstanceOf[ChooseOne]`. Assert the offered option ids.
- [ ] `application/GameApplicationServiceSuite.scala:1223` "Search draw port cannot inject card identities inconsistent with state" — WEAK — strengthen — medium-low — `isInstanceOf[CommandRejected]` would also pass if Search were rejected for an unrelated reason. Assert the specific violation.
- [ ] `application/GeneratedFirstGamePlanFactorySuite.scala:13` "a generated Chronicle feeds GameStartRules" — DUPLICATE — delete — medium-high — Test :23 already runs `GameStartRules.evolve(...).toOption.get` on the same plan. The deck-size and atlas assertions repeat FirstGameChronicleGeneratorSuite:16 and :33.
- [ ] `application/GeneratedFirstGamePlanFactorySuite.scala:66` "successive plans are randomized, not the fixed dev order" — DUPLICATE — delete — high — It makes the same atlas-order-varies assertion as FirstGameChronicleGeneratorSuite:104, because the factory just calls the generator. It is also covered end to end by ServerRuntimeSuite:16. The "fixed dev order" it mentions belongs to the retired `DevelopmentFirstGamePlanFactory`.
- [ ] `application/SitePowerTextSuite.scala:15` "a site power other than a Homeland keeps its printed name" — DUPLICATE — delete — medium — The River label is already asserted by GamePresentationProjectorPrintedFacesSuite:36 and PhasePowerProjectorSuite:133.
- [ ] `application/NegotiationDealProjectionSuite.scala:125` "Negotiation is offered as a start control, not as a board-target selection" — DUPLICATE — merge into :119 — medium-high — Its first assertion is identical to :119's first line. Only the `boardTargetActions` check is new.
- [ ] `application/WalkerDecisionProjectorSuite.scala:708` "a Campaign decision no roll belongs beside projects no roll" — DUPLICATE — merge into :655, or into WalkerDecisionProjectionSuite:382 — medium — Same claim (no `rollOutcome` on a rollless Campaign decision) as WalkerDecisionProjectionSuite:382 "a Campaign force decision carries no roll", on a sibling decision.
- [ ] `application/GameApplicationServiceSuite.scala:1274` "facedown adviser plays through the shared walker after reload" — DUPLICATE — merge into :702 — medium-high — Same flow (PlayFacedownAdviser, discard, reload equality) as :702. The only extra is a reload between start and resolve.
- [ ] `application/GameApplicationServiceSuite.scala:1009` "a walker Campaign persists every command and replays to the same state" — DUPLICATE — delete — medium — The `forgeReadyGame` prefix is already replayed and compared by :789 (lines 832-833, 939-941) and :950 (line 996). The conquest is a precondition of Forge.
- [ ] `application/GameApplicationServiceSuite.scala:1849` "HSQL close and reopen preserves v2 replay equality" — DUPLICATE — delete — medium-high — :1871, :1899, :1965 and :1993 each run the same setup on HSQL, reopen and compare state. The count of 29 is asserted by :1022.
- [ ] `application/GameApplicationServiceSuite.scala:1355` "Begin Rest finishes Rest, persists and reloads to the next player's Wake" — DUPLICATE — merge with :1871 — medium — :1871 runs the same setup, EndWake, BeginRest and reload on HSQL. Its extra "secret summary" check adds nothing after state equality, since `totalSecrets == available + committed` follows from `facedown == 0` and `totalSecrets = available + facedown + committed`.
- [ ] `server/GameHttpWireSuite.scala:58` "walker intents map onto ... StartWalker/RollWalker/ResolveWalker" (lines 111-116) — DUPLICATE — drop the two trailing `.isLeft` checks — high — :133 and :139 assert the same inputs (unknown option kind, blank relic id) with exact paths.
- [ ] `server/TrustedGameProvisioningSuite.scala:85` "the game ID comes from the server's generator" — DUPLICATE — delete — medium-high — :94 already asserts the generator-supplied id ("fresh") and its row counts.
- [ ] `server/ServerConfigSuite.scala:56` "CLI values take precedence over environment values" — DUPLICATE — delete — high — Subsumed by :77, which overrides every option. The version check repeats :9.
- [ ] `server/ServerConfigSuite.scala:288` "runtime mode accepts only development and trusted-alpha" — DUPLICATE — delete — medium — Covered by :9 (Development default), :24 and :56 (trusted-alpha), and :218 (`production` rejected).
- [ ] `server/ServerConfigSuite.scala:153` "authenticated public origin enforces the CSRF origin policy" — DUPLICATE — trim to one accept and one reject — medium-high — ServerConfig delegates to `SameOriginCsrfProtection.validateOrigin` (ServerConfig.scala:228). The origin table is the same as SessionSecuritySuite:113.
- [ ] `server/ServerConfigSuite.scala:301` "development mode rejects non-loopback bind hosts", together with `server/DevelopmentIdentityShimSuite.scala:32` "enabled shim refuses wildcard and non-loopback bindings" — DUPLICATE — trim each to one host — medium-low — Both delegate to `DevelopmentTrustBoundary.validateLoopbackHost` and repeat DevelopmentTrustBoundarySuite:4's host list.
- [ ] `server/ServerRoutesSuite.scala:15` "authenticated routes mount only with complete session configuration" (lines 16-23) — DUPLICATE — drop the `fromOptions` asserts — medium — ServerConfig calls `fromOptions` (ServerConfig.scala:217), and ServerConfigSuite:118 covers the partial combinations with exact messages.
- [ ] `application/gamelog/GameLogHeadlineSuite.scala:21` "a whole round posts one turn headline per seat and opens Round 2" — DUPLICATE — merge with :30 — low — It uses the same script and viewer as :30, the tail assertion (Wake, Act) is repeated, and the round golden pins both.
- [ ] `server/SeatCodeSuite.scala:17` "distinct random bytes produce distinct seat codes" — DUPLICATE — merge into :9 — low-medium — Asserting in :9 that the decoded bytes equal the input bytes would prove the random source is used, which is all :17 guards.
- [ ] `server/GameHttpWireSuite.scala:162` "development bootstrap remains configuration-only" — COVERED-PLUMBING — delete — medium — `decodeBootstrap` is now reached only through the authenticated route, which AuthenticatedGameBootstrapRoutesSuite:29 drives (valid body, and a hidden-field injection returning 400). The title still names the retired dev bootstrap, and "configuration-only" is not actually asserted.
- [ ] `application/GameIntentMapperNegotiationSuite.scala:23` "accept and decline map to their engine answers" — COVERED-PLUMBING — delete — medium — AuthenticatedGameRoutesSuite:23 sends `accept-deal` and `decline-deal` over HTTP and gets 200s. The :12 disclosure mapping is not covered there, so keep :12.
- [ ] `server/GameHttpWireSuite.scala:44` "one mapper binds the transport-selected actor" — COVERED-PLUMBING — delete — low-medium — Actor binding is exercised by AuthenticatedGameRoutesSuite:111 (derived actor, spoofing rejected) and TrustedSeatRoutesSuite:33 and :82.
- [ ] `application/WalkerDiceAdapterSuite.scala:10` "the adapter routes by die kind and passes the count through" — COVERED-PLUMBING — delete — low — This is pure delegation. Defense rolls go through it in the GameApplicationServiceSuite Recover tests, and attack rolls in the ForgeWalkerFixture conquest. Keep :22, because no scenario overrides the port's shuffle.
- [ ] `application/gamelog/GameLogRouteSuite.scala:34` "the trusted gateway pages for the seat and refuses another game" — COVERED-PLUMBING — delete — low — TrustedSeatRoutesSuite:137 covers seat-scoped paging and cursor checks at the route. The first assertion is only `.isRight`.
- [ ] `application/PendingWalkerInvariantSuite.scala:44` "the sample holds every GameCommand constructor" — SOURCE-TEXT — keep, or replace — medium — Regex over `GameCommands.scala` guards that `everyCommand` samples every constructor. It could be replaced by an exhaustive `match` over `GameCommand` in the helper, so the compiler flags a new constructor.
- [ ] `application/WalkerDecisionQueryPowerSuite.scala:166` "a transform applied to the walk but not to the projection makes the two disagree" — OTHER (a negative control that tests deliberately mis-wired test setup) — delete — medium — It shows that a projector built with a different power set disagrees. That is already implied by :147 and :156, where powers change the projection.
- [ ] `application/gamelog/GameLogPropertiesSuite.scala:80` "every ProcedureRef key the formatter handles appears in some script" — OTHER (guards test-script coverage against a hand-kept list) — strengthen — low-medium — The expected set is a literal, so a new production procedure would not fail it. Derive the set from the production registry, or drop the test.
- [ ] `application/GameApplicationServiceSuite.scala:1022, :1043, :1558, :1642, :1849` titles mentioning "v2" or "v3" — OTHER (stale titles) — rename — high — `GameEventWire.FormatVersion` is 1, and :1043 "appends v3" actually asserts `formatVersion == 1`.
- [ ] `server/ServerRuntimeSuite.scala:16` "…not the fixed dev one" — OTHER (stale title) — rename, keep the test — medium — It is the only guard that the default runtime is wired to the random factory, but the "dev one" it names is retired.

### Leads to check in step 4

- [ ] The ActorlessCommand round trip and actor-injection path are also tested in `shared/.../protocol/CommandProtocolSuite.scala:57` and `:64`. These overlap GameHttpWireSuite:25 and :36.
- [ ] GameApplicationServiceSuite:1544 (malformed envelope gives CodecFailure), :1642 (missing sequence 0) and :1598 (stream identity) look likely to repeat `serialization/GameEventWireSuite.scala:668` and `:685` at the codec level. Keep only the service-level mapping to `GameApplicationError`.
- [ ] CardKnowledgeSuite tests pure `OperationExecutor` knowledge rules from inside application/. Check against `gameplay/OperationExecutorSuite.scala:261` and `:350`, and `testkit/TableSuite` (knowledge.*).
- [ ] WalkerDecisionProjectorSuite:108 re-asserts `WalkerProcedureRegistry.rollDecisionId(ActionRef.Forge)`, which `gameplay/walker/WalkerProcedureRegistrySuite.scala:102` already pins.
- [ ] GamePresentationProjectorPrintedFacesSuite:12 (Vision printed text) may repeat `shared/.../VisionCardPresentationSuite`.
- [ ] ChronicleShuffleSuite (ShufflePolicy ordering) may repeat `gameplay/setup/FirstGameSetupMaterializerSuite`.
- [ ] WalkerDecisionQueryPowerSuite's "walker accepts" halves may repeat `gameplay/OathRulesWalkerPowerSuite`, which uses the same seam.
- [ ] EconomyProjectionSuite:44/:51, ChallengeProjectionSuite:24 and NegotiationDealProjectionSuite:119 (control offered iff legal) may repeat legality tests for Muster, Trade, Challenge and Negotiation in gameplay/.
- [ ] Within this partition: most focused GameLog*Suite text assertions run on scripts that also have golden logs, for the actor and one other seat. GameLogGoldenSuite pins the exact text, so tests asserting on those two viewers are strictly implied by the goldens. I did not list them one by one, because they still catch regressions a reviewer could miss when goldens are regenerated. The lead may want a policy on this.


## Frontend

Scope: frontend/src/test/scala/.

### Survey summary

- Suites read: 44 (plus the fixtures `RecordingControls`, `ProtocolTestCommands` and `TestBrowser`). Tests read: 455 declared, 459 at runtime because two `ServerModeUiSuite` loops each make 3 tests.
- Findings by category (one primary category per bullet): TAUTOLOGY 12, WEAK 3, DUPLICATE 27, COVERED-PLUMBING 5, TYPE-PREVENTED 1, SOURCE-TEXT 0, OBSOLETE 3, OTHER 2.
- Whole-suite candidates:
  - `HttpGameClientSuite`: about 20 of its 33 tests are low value. `GameJson.encodeCommand` and `decodeProjection` just call the shared `ActorlessCommandCodec` and `GameProjectionCodec`. `CommandProtocolSuite` and `ProjectionProtocolSuite` already round-trip every intent and a fully populated projection. Most transport-path cases are also asserted exactly by `ServerModeUiSuite` scenarios. Worth keeping: the trusted and dev URL shapes (:30), the actorless body keys (:194), the JSON-safe `nextSequence` bound (:525), the malformed-input decode failures (:503), and the log client tests (:669–:694).
  - No other suite is mostly low value.
  - `ServerModeUiSuite` has about 12 low-value tests out of 56.

### Findings

- [ ] `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala:643` "a roll answer carries the projected pool key and no die faces" — TAUTOLOGY — delete — high — It asserts `GameCommand.RollWalker("red","recover") == GameIntent.RollWalker("recover")`. `GameCommand` is the test helper in `ProtocolTestCommands.scala`, so no production code runs.
- [ ] `ServerModeUiSuite.scala:652` "the parked Recover choice decision resolves its projected button options…", `:669` "the parked Recover relic decision offers one control per projected option, never a preselected relic", `:1035` "a choose-one decision outside Recover is answered from its projected options" — DUPLICATE — merge into one test — high — All three call the same generic `WalkerPanelSupport.resolveChooseOneCommand`, and it has no Recover-specific branch. They differ only in option data. The :669 title promises "one control per option", which it never checks; `CardChoicePanelSuite.scala:79` checks that.
- [ ] `ServerModeUiSuite.scala:521` "Forge is answered by moving projected options between projected sections" — DUPLICATE — delete — high — Same query, same moves (item 2 to pay-favor, item 0 to pay-secret) and same commands as `PartitionDecisionStateSuite.scala:116` and `:128`. The "undeclared section ignored" part is covered by `PartitionDecisionStateSuite.scala:25`.
- [ ] `ServerModeUiSuite.scala:797` "inactive setup viewer waits without pawn or private adviser controls" — DUPLICATE — delete — high — `ServerUiSupport.viewerPresentation` has no phase branch, so this runs the same active≠viewer path as `:746` "inactive Wake viewer waits…". No pawn or adviser controls are asserted, despite the title.
- [ ] `ServerModeUiSuite.scala:810` "active viewer retains Wake and setup gameplay controls" — DUPLICATE — trim or delete — medium — The setup half hits the same default branch as the Wake half. The Wake half repeats `:702` "Take Wealth actions use the active-player labels and commands".
- [ ] `ServerModeUiSuite.scala:911` "populated site details render properties, stable IDs, and hidden relics" — TAUTOLOGY — strengthen or delete — medium — Lines 929–931 assert `site.denizens` on the test's own input. The rest are pass-through counts from `SiteCardPresentation.from`. Nothing is rendered, despite the title. `SiteFaceSuite` covers the rendered form.
- [ ] `ServerModeUiSuite.scala:945` "empty site details have image-independent empty states" — COVERED-PLUMBING — delete — low — Zero pass-through fields. The DOM empty states are pinned by `SiteFaceSuite.scala:93`, `:123` and `:168`.
- [ ] `ServerModeUiSuite.scala:962` "a forgeable site shows its forge cost instead of its recover difficulty" — DUPLICATE — delete one — medium — `SiteFaceSuite.scala:155` "forge and recover are one corner, never both" checks the same precedence through the rendered site.
- [ ] `ServerModeUiSuite.scala:173` "host colors map to their own player badge tokens" — TAUTOLOGY — delete — medium — `PlayerColorCss.of` is `s"player-${color.key}"`, so the test restates `PlayerColor.key` catalog data. `LineageColors` order is exercised by `:116`.
- [ ] `ServerModeUiSuite.scala:1061` "available controls use durable ordered presentation categories" — TAUTOLOGY — trim — low — `actionCategoryOrder` and `majorFamilyOrder` are restated constants. Ordering is already observed at the DOM (`EconomyControlsSuite.scala:26`, `ActionOptionRowSuite.scala:39`). `actionCategory("campaign")` repeats `CampaignControlsSuite.scala:33`.
- [ ] `ServerModeUiSuite.scala:686` "site forces retain accessible labels counts and stable color classes" — TAUTOLOGY (line 699 only) — trim that line — low — It asserts a case-class default (`forces = None`).
- [ ] `frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala:153` "Recover walker commands encode…", `:170` "Forge commands encode…", `:252` "Wake commands and action-selection projection are deterministic", `:292` "Travel encodes destination…", `:324` "Search start uses walker arguments…" — DUPLICATE (cross-partition) — delete — medium-high — `GameJson.encodeCommand` is a one-line `ActorlessCommandCodec.encode` (`GameClient.scala:246`). `shared/.../CommandProtocolSuite.scala:57` round-trips every one of these intents. Most commands here are built by the `ProtocolTestCommands` helper, so the substring asserts partly test the helper. Nothing about "deterministic" is tested.
- [ ] `HttpGameClientSuite.scala:186` "Rest commands encode current sequence without an actor" — TYPE-PREVENTED — delete — high — `GameIntent.BeginRest` and `FinishRest` have no actor field. The helper `GameCommand.BeginRest(actor)` throws the actor away before the codec runs, so `!contains("playerId")` cannot fail. Actor injection is covered by `CommandProtocolSuite.scala:64`.
- [ ] `HttpGameClientSuite.scala:252` (decode half) and `:292` (decode half) — OBSOLETE (frontend) — delete — high — They decode `actionFamilies`, `currentSiteResources` and `legalTravelDestinations`, which have 0 uses in `frontend/src/main`. `ProjectionProtocolSuite` round-trips all three anyway.
- [ ] `HttpGameClientSuite.scala:584` "projection contains scoped choices, no hidden plan, and Ready state" — TAUTOLOGY — delete — high — The `!json.contains("denizenOrder")` checks run on the test's own `projectionJson` fixture. The decode asserts read back fixture fields that the shared round trip already covers.
- [ ] `HttpGameClientSuite.scala:578` "transport timeout and abort are typed failures" — TAUTOLOGY — delete — medium — "Typed" is true by construction. It only checks substrings of messages built in the test.
- [ ] `HttpGameClientSuite.scala:55` "trusted conflict allows one reload without retry" and `:381` "409 is surfaced and caller refreshes without command retry" — DUPLICATE / COVERED-PLUMBING — keep one, reduced to "409 → StalePosition, one POST" — medium — Both clients share `GameJson.responseFailure`. The "reload" GET is made by the test itself. The real behaviour is covered by `ServerModeUiSuite.scala:364` (exact GET/POST/GET list) and `TableSessionSuite.scala:352`.
- [ ] `HttpGameClientSuite.scala:427` "transient disconnect reconnects by GET at authoritative sequence" — COVERED-PLUMBING — delete — medium-high — The test drives `ServerSessionCoordinator` itself. The same path is covered by `TableSessionSuite.scala:175`. `!exists(POST)` is tautological because the test never submits.
- [ ] `HttpGameClientSuite.scala:412` "existing game reload uses selected identity without creating history" — COVERED-PLUMBING — delete — medium — `ServerModeUiSuite.scala:182` asserts the exact request list, including `/api/dev/first-games/existing?playerId=red`.
- [ ] `HttpGameClientSuite.scala:68` "production client previews and submits the same ordered modifiers" — OBSOLETE — delete or rewrite — medium — It submits `PeekSiteRelics` with outer `orderedModifiers`. Nothing in the frontend can do that: `ModifierFlowDraft.submission` is reached only with `StartWalker`, which folds the modifiers in and sends none outside. The preview path is covered by `ServerModeUiSuite.scala:273`.
- [ ] `HttpGameClientSuite.scala:21` "trusted viewer identity round trips while old projections omit it" — DUPLICATE (cross-partition) — delete — medium — Shared codec behaviour. Server `TrustedSeatRoutesSuite.scala:44-51` and `AuthenticatedGameRoutesSuite.scala:290` check it.
- [ ] `HttpGameClientSuite.scala:88` "a projected deal decodes for a spectator and a proposal encodes" — DUPLICATE (cross-partition) — delete — medium — `ProjectionProtocolSuite.scala:151` round-trips the deal. `ProposeTerms` is in the `CommandProtocolSuite` examples. `protocolNegotiationTerms` is covered by `NegotiationDealPanelSuite.scala:52`.
- [ ] `HttpGameClientSuite.scala:109` "minor actions decode private state and encode typed controls" — DUPLICATE (cross-partition) — delete — medium — Shared round trip, plus `contains` asserts built from legacy helpers.
- [ ] `HttpGameClientSuite.scala:130` "an unclaimed banner projection decodes", `:141` "projection decodes scoped Oathkeeper and Usurper victory status", `:346` "site detail decoder…" — DUPLICATE (cross-partition) — delete, or move any missing case (null holder, winner kind) to `ProjectionProtocolSuite` — low-medium — Codec behaviour. `ProjectionProtocolSuite.scala:75`, `:262` and `:275` cover banners, oathkeeper and forces/colour rejection.
- [ ] `HttpGameClientSuite.scala:14` "trusted colon game uses the encoded canonical cookie path" — DUPLICATE — delete — low — Encoding is covered by `:30` (space, slash, ?) and `ServerModeUiSuite.scala:28`. The only other assert is `result.isRight`.
- [ ] `HttpGameClientSuite.scala:491` "malformed projection is a typed decode failure" — DUPLICATE — merge into `:503` — low.
- [ ] `frontend/src/test/scala/oathdigital/frontend/SnapshotPollingCoordinatorSuite.scala:69` "disconnect stops polling and explicit reconnect resumes it" — DUPLICATE — delete — medium — The test calls `poller.stop()` itself, and the `ServerSessionCoordinator` calls are not wired to the poller. What is left repeats `:29`. The real disconnect wiring is `TableSessionSuite.scala:303`.
- [ ] `SnapshotPollingCoordinatorSuite.scala:45` "nextSequence distinguishes unchanged, older, and changed snapshots" — OTHER (misplaced, stale name) — move to a coordinator suite or delete — low — It tests `ServerSessionCoordinator.snapshotAdvances`, not the poller, and no `nextSequence` method exists. Unchanged and changed are covered by `TableSessionSuite.scala:219`; "older" is not covered elsewhere.
- [ ] `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala:370` "a stale-position submit redraws before the reload lands" — DUPLICATE — merge into `:352` — low-medium — Same setup and same drafts/failure asserts. The only new check is the redraw count.
- [ ] `TableSessionSuite.scala:383` "the flow's preview carries the session's game and seat" — COVERED-PLUMBING — delete — medium — `ServerModeUiSuite.scala:273` asserts the preview URL `/g/preview?playerId=red`.
- [ ] `TableSessionSuite.scala:297` "reload leaves the page to the browser" — COVERED-PLUMBING — delete — low — `reloadClient` is the one-line `navigation.reload()`.
- [ ] `frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala:187` "empty has no context and nothing staged" — TAUTOLOGY — delete — high — It restates the definition of `SessionDrafts.empty`.
- [ ] `SessionDraftsSuite.scala:89`, `:96`, `:102`, `:108`, `:116`, `:126` (Restarted, Failed, Completed, Cancelled, TargetsLeft, OrderingLeft) — DUPLICATE — keep one layer — low-medium — The same slot outcomes are asserted through the flow in `ModifierFlowSuite.scala:157`, `:220`, `:240`, `:257`, `:283` and `:274`. Only `Cancelled(None)` and "TargetsLeft with no modifier stage" are unique here.
- [ ] `frontend/src/test/scala/oathdigital/frontend/CardFaceSuite.scala:209` "face-up and face-down cards of one type carry the same box class" — TAUTOLOGY — merge into `:28` — medium-high — Line 215 is `assertEquals(boxClass(kind), boxClass(kind))`, and the rest uses the production `boxClass` as its own oracle. Only the vision/edifice render cases are new; add them to `:28` with literal class names.
- [ ] `CardFaceSuite.scala:178` "knowability is read per render, so a later projection can add the pip" — DUPLICATE — delete — high — `render` is pure. Both asserts repeat `:99` (hidden → not knowable) and `:169` (knowable → knowable).
- [ ] `CardFaceSuite.scala:161` "an unidentifiable adviser still says whether it is a denizen or a vision" — DUPLICATE — merge with `:150` "each hidden kind gets its own letter" — low-medium — V is already asserted via render at `:99`.
- [ ] `CardFaceSuite.scala:194` "an empty denizen slot and an unknown relic occupy their type's box" — DUPLICATE — delete — low-medium — `SiteFaceSuite.scala:65` and `:76` pin the same classes and aria-labels through `siteDetails`.
- [ ] `frontend/src/test/scala/oathdigital/frontend/RulesTextRendererSuite.scala:25` "the three warm suits reference three different symbols" — TAUTOLOGY — delete — high — `:14` already asserts href == `s"#token-$token"` for every id, so distinct ids always give distinct hrefs. `TokenSprite.ids.size == 17` at `:15` and `:60` restates the catalog count (low).
- [ ] `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala:47` "choosing an option submits the generic answer for its kind and id" — TAUTOLOGY — strengthen with a literal `ResolveWalker("muster.source", ChooseOneWire("denizen","d1"))` — medium — It uses the production `resolveChooseOneCommand` as the oracle. Once strengthened, the `ServerModeUiSuite` trio above can go.
- [ ] `frontend/src/test/scala/oathdigital/frontend/CardChoicePanelSuite.scala:88` "taking a relic submits the option the button belongs to" — WEAK — strengthen — medium — `submitted.head.toString.contains("relic:horn")` passes for any intent that mentions the id. Assert the exact `ResolveWalker(recoverRelicDecisionId, ChooseOneWire("relic","relic:horn"))`.
- [ ] `frontend/src/test/scala/oathdigital/frontend/CardInspectionOverlaySuite.scala:143` "focus return survives the opener being rebuilt out from under it" — WEAK — strengthen — medium — It only asserts `!isOpen`, which amounts to "does not throw". Assert where focus actually lands.
- [ ] `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala:87` "a reader scrolled up is not moved by new entries" — DUPLICATE — delete — high — `:142` "…offered the New chip…" has the same setup (box(500,100,0), scrollTop 40, append) and asserts `scrollTop == 40.0` too.
- [ ] `GameLogPaneSuite.scala:169` "a reader at the end is followed and never shown the chip" — DUPLICATE — merge into `:76` — medium — Identical setup to `:76`; only adds the chip-hidden assert.
- [ ] `frontend/src/test/scala/oathdigital/frontend/LogMarkerSuite.scala:22` "the key names the game and the seat" — DUPLICATE — delete — low — Every other test asserts the literal `oath.log.seen.g1.red` key.
- [ ] `frontend/src/test/scala/oathdigital/frontend/DistributeDecisionStateSuite.scala:32`, `:37`, `:47` ("increment stops when nothing remains", "fill is limited by what remains", "fill with nothing remaining changes nothing") — DUPLICATE — merge into one — low-medium — All three use `atMinimums.fill("nomad")` and share the remaining==0 assertion.
- [ ] `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala:123` "zero modifiers skip ordering and a targeted action still requires explicit confirmation" — TAUTOLOGY — delete — medium — `!draft.ordering` is true because the test sets `stage = Targets`. Real skipping is `ModifierFlowDraftSuite.scala:26`. Explicit confirm is `:88` in the same suite and `BoardTargetSelectionStateSuite.scala:47`.
- [ ] `ModifierSelectionStateSuite.scala:212` "submission leaves non-walker commands on the legacy ordered-modifiers channel untouched" — OBSOLETE — delete (or delete the `case other` branch of `ModifierFlowDraft.submission`) — medium — Both callers (`ModifierFlow.scala:102` and `:115`) receive only `StartWalker`: `ModifierFlowDraft.action` whitelists walker starts, `commandForSelection` returns only travel `StartWalker`, and the facedown command is `StartWalker`.
- [ ] `ModifierSelectionStateSuite.scala:163` (lines 192–196, codec tail) — DUPLICATE (cross-partition) — trim — low — `StartWalker("recover", Vector("denizen.catacombs"))` is literally in `CommandProtocolSuite` examples.
- [ ] `frontend/src/test/scala/oathdigital/frontend/BannerControlsSuite.scala:38` (Place Banner Resource half) and `CampaignControlsSuite.scala:33` (`actionCategory("campaign")`) — DUPLICATE — trim — low — The first is covered by `ModifierSelectionStateSuite.scala:57`, the second by `ServerModeUiSuite.scala:1066`.
- [ ] `frontend/src/test/scala/oathdigital/frontend/ProtocolTestCommands.scala` (fixture, used at about 41 call sites in 5 suites) — OTHER — retire in favour of `GameIntent` directly — medium — Its doc says keeping actor parameters "makes existing assertions prove that identity never reaches the encoded payload". That is false: each builder drops the actor, so those assertions only test the helper. It is a leftover of the actorless migration.

### Leads to check in step 4

- [ ] `GameJson` encode/decode cases in `HttpGameClientSuite`: compare with `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala:57` and `:64`, and `ProjectionProtocolSuite.scala:75`, `:151`, `:262`, `:275` and `:287`.
- [ ] The JSON-safe `nextSequence` bound (`HttpGameClientSuite.scala:525`) is not in any shared suite. It is a `GameProjectionCodec` property, so it probably belongs in `ProjectionProtocolSuite`.
- [ ] `viewerPlayerId` present or omitted: server `TrustedSeatRoutesSuite.scala:44-51` and `AuthenticatedGameRoutesSuite.scala:290`.
- [ ] 409 `stale-client-position` mapping: server `GameRoutesSuite.scala:105`.
- [ ] The Conspiracy rules-text test (`RulesTextRendererSuite.scala:62`) reads `VisionCardPresentation`; check `shared/.../VisionCardPresentationSuite.scala` for the same two-paragraph text.
- [ ] Walker decision form routing: `ParkedDecisionSuite` defers unknown forms to `ProjectionProtocolSuite:237`. The server `WalkerDecisionProjectionSuite` may also pin query shapes the frontend suites rebuild by hand.
- [ ] `projectionJson` in `HttpGameClientSuite` still carries `pendingCardDecision`, `legalTravelDestinations` and `actionFamilies`. `src/main/.../application/GameProjection.scala` still projects them, but the frontend reads only `legalSearchSources` and `pendingCardDecision.decisionId`. Worth checking whether the server-side projection tests of those fields are also obsolete.

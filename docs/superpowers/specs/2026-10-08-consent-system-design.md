# Consent system

**Status:** designed 2026-10-08. It covers the roadmap's **Phase - Consent
system**. Not yet implemented.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**.
The [undo design](2026-10-06-undo-design.md) defines **command batch**,
**issuer**, **barrier** and **void marker**. This design adds **waivable
power**, **granter**, **askable option**, **consent request**, **consent
answer**, **consent preference** and **consent ledger**.

## Goal

A player blocked by an enemy's persistent power can ask that power's ruler
for consent to ignore it, for one specific choice. The ruler answers Yes or
No, or sets a standing preference that answers that player's future requests
automatically. Answers and preferences never get in the way of undo.
Preferences are private to the player who sets them.

## User story

Red rules the Narrow Pass. Blue wants to Travel to the Deep Woods, which the
Pass blocks.

1. In the Travel destination choice, Deep Woods is shown in amber, not grey.
   Its hover reason reads "Narrow Pass: Cannot travel to other sites in the
   region without ruler's consent."
2. Blue clicks Deep Woods. A confirmation asks "Ask Red for consent to ignore
   Narrow Pass?" **No** returns Blue to the destination choice. **Yes** logs
   "Asked Red for consent to ignore Narrow Pass and select Deep Woods."
3. Red is prompted: "Give Blue consent to ignore Narrow Pass and select Deep
   Woods?" The options are **Never Allow**, **No**, **Yes** and **Always
   Allow**, with the notice "You can adjust your consent preferences by
   clicking on the Narrow Pass power."
4. **Never Allow** and **No** log "Red gave no consent." Blue is back at the
   destination choice, with Deep Woods greyed out and showing the ordinary
   reason ("Narrow Pass: Cannot travel to other sites in the region."). Blue
   may pick another site or undo the Travel.
5. **Yes** and **Always Allow** log "Red gave consent." Blue's Travel
   continues as if Blue had selected Deep Woods.
6. **Never Allow** and **Always Allow** also set Red's standing preference
   for Blue on the Narrow Pass. From then on Blue's requests to ignore it are
   answered automatically. Blue sees the same log lines either way and cannot
   tell an automatic answer from a manual one.
7. While Red rules the Narrow Pass, clicking the power shows its text and a
   grid with one row per other player: **Never Allow**, **Case-by-case**
   (the default) and **Always Allow**. Red can change it at any time, even
   off turn. Each change writes a log line only Red sees.

## Rulings

### Scope

1. **Waivable powers.** A persistent power is waivable when its text
   restricts the ruler's "enemies" (CR p.29, NF p.16). This includes
   "cannot" powers (the Pass, Secret Police, Hall of Ministers, Forgotten
   Vault), "cannot … unless they give favor" powers (Toll Roads, Forced
   Labor, Curfew), where consent skips the payment, and "must" powers
   (Grasping Vines), where consent skips the penalty. Powers worded
   "players" or "other players" (Lost Tongue, Sacred Ground, Rotting
   Fortress, Diplomat) are not waivable.
2. **Campaign actions.** No consent inside a Campaign action (CR p.29),
   except the Pass's own campaign-targeting clause, whose text grants it.
   Campaign-only cards (Gleaming Armor, Insect Swarm, True Names, Beast
   Tamer) are never waivable. Forgotten Vault is waivable only outside a
   campaign.
3. **Enforcement.** The game always enforces. There is no "forgot to
   enforce" rule (CR p.43, ambiguities decision 3).
4. **Bandits** never consent. An option blocked by a bandit-ruled power is
   not askable.
5. **Own powers.** A ruler is never asked about a power they rule; ruling it
   already ignores it.
6. **Ignore effects** such as Forest Paths still drop the Pass without anyone
   being asked, because ignore hooks run before restrictions are checked.

### Requests

7. **Two request points, one mechanism.** A request is always made from an
   askable option. For a hard block it is the blocked option itself. For a
   toll or penalty power, the step becomes a choice such as "Pay 1 favor" or
   "Travel without paying", and the free option is the askable one. A
   refusal leaves only the paying option.
8. **Askability.** An option is askable only when every block on it comes
   from a waivable power, no granter is the bandits or the requester, and no
   answer for it has been refused this turn. Otherwise it is unavailable, as
   today.
9. **Several granters.** One prompt per granter covers all of that granter's
   powers on the option. All granters are asked at once; the first refusal
   closes the other prompts. Grants given before that are remembered. This
   needs several pending asks, which land in the first slice where two hard
   blocks from different rulers can fall on one option.
10. **Automatic answers.** For each granter: if any of its powers on the
    option is set to Never for the requester, the answer is No. If all are
    set to Always, the answer is Yes. Otherwise the granter is prompted, and
    the prompt covers all of that granter's powers on the option.
11. **Speed leak.** An automatic answer arrives at once, so the requester
    can infer it. This is accepted for now.

### Memory and undo

12. **Answers last the turn.** An answer is remembered for the rest of the
    turn for its exact key: power, requester, decision and target. A refused
    target stays unavailable. A granted target can be selected again without
    asking. A preference set later in the same turn does not change a
    remembered answer; it applies only to targets nobody has asked about yet.
13. **Answers never block undo.** The requester may undo past a consent
    answer, as far as the ordinary barriers allow. The answer stays
    remembered and its log lines are never struck through.
14. **Withdrawing.** The requester may undo while a request is unanswered.
    The request is withdrawn, its log line is struck through, and nothing is
    remembered.

### Preferences

15. **Key and default.** A preference is stored per granter, power and
    requester. Missing means Case-by-case.
16. **Who sees it.** The grid is shown only to the player who currently
    rules the power's card, and only that player's own preferences.
17. **Reset.** When the granter stops ruling the card, that card's
    preferences are cleared and the granter sees a private log line. A card
    lost and regained within a single command keeps its preferences; this is
    accepted.
18. **Changing.** A change is accepted at any time, including during another
    player's parked decision. It is never undoable and never becomes a
    command batch.
19. **Changing while prompted.** An open prompt stays open after the granter
    changes a preference. The change applies only to requests made later.

## Architecture

Consent lives beside the walker, not inside its procedure tree. Requests,
answers and preferences are their own commands and events, kept off the undo
stack. The walker gains one new thing: options in an askable state.

### State

`ReadyGame` gains `consent: ConsentState`, next to `knowledge`:

- `preferences: Map[(PlayerId, PowerId, PlayerId), ConsentPreference]`,
  keyed by granter, power and requester, where `ConsentPreference` is
  `Never | Always`.
- `answers: Map[ConsentKey, ConsentAnswer]`, where `ConsentKey` is
  `(power, requester, decisionId, target: DecisionOptionRef)` and
  `ConsentAnswer` is `Granted | Refused`. The decision id keeps Deep Woods as
  a travel destination apart from Deep Woods in a toll step.
- `pending: Option[ConsentRequest]`, where `ConsentRequest` holds the
  requester, decision id, target and `asks: Vector[ConsentAsk]`. A
  `ConsentAsk` is a granter, its powers and an optional answer. Slice 1 has
  one ask at most.
- `turn`: the turn `answers` belongs to.

The state sits outside `CurrentGameState` because undo rewinds to a saved
`CommandBatch.before`. Consent state has to be carried across that rewind.

### Events

New `OathEvent` cases. None opens a `CommandAccepted` batch, so the journal
fold keeps them off the undo stack without further changes.

- `ConsentRequested(requester, decisionId, target, asks)`
- `ConsentAnswered(granter, granted: Boolean)`
- `ConsentRequestWithdrawn(requester)`
- `ConsentPreferenceSet(granter, power, requester, setting)`, where
  `setting` is `Never | CaseByCase | Always`. Case-by-case removes the entry.
- `ConsentPreferencesCleared(granter, power)`

A manual Never Allow or Always Allow emits `ConsentPreferenceSet` and then
`ConsentAnswered` in the same append. An automatic answer is
`ConsentAnswered` alone, emitted in the requester's command.

The fold clears `answers` when the turn changes. That expiry writes no event
and no log line.

### Undo

On `StepTakenBack`, the fold takes the batch's `before` state and puts the
current `ConsentState` back onto it, minus any pending request. Grants,
refusals and preferences therefore survive undo. `StepRestored` does the
same.

When a consent request is pending, the requester's `Undo` does not take back
a batch. It emits `ConsentRequestWithdrawn`.

### Commands

The pending-walker gate admits three new `GameCommand`s.

**`RequestConsent(requester, decisionId, target)`**

1. Validate four conditions: the walker is parked at `decisionId`, owned by
   `requester`; the look-ahead marks `target` askable; no answer for it is
   remembered this turn; no request is pending.
2. Group the waivable breaches by granter into asks and emit
   `ConsentRequested`.
3. Emit automatic answers per ruling 10. Asks without one stay open and
   their granters are prompted.
4. Settle the request if it is complete (see below).

**`AnswerConsent(granter, choice)`**, where `choice` is
`Never | No | Yes | Always`.

1. Validate that `granter` has an open ask on the pending request.
2. For Never or Always, emit `ConsentPreferenceSet` for each power in the
   ask, then `ConsentAnswered`.
3. Settle the request if it is complete.

**Settling.** A request is complete when every ask has an answer, or when
any ask is refused.

- On any refusal, record Refused for every key and clear the request. The
  parked decision re-renders with the target unavailable.
- When all asks are granted, record Granted, clear the request, and answer
  the parked decision with `target` for the requester. That answer opens a
  `CommandAccepted(requester)` batch even though the granter's command
  produced it, so the requester can undo the move itself. This is the one
  case where a batch's issuer is not the command's actor.

**`SetConsentPreference(granter, power, requester, setting)`**

Validate that `granter` rules the power's card now, the power is waivable,
`requester` is another player and the game is not over. Emit
`ConsentPreferenceSet`. Accepted at any time.

**While a request is pending,** `ResolveWalker` and `RollWalker` are
rejected with a new `OathViolation.ConsentPending`. Accepted: `AnswerConsent`
from an asked granter, `Undo` from the requester, and
`SetConsentPreference` from anyone.

**After every command,** the application compares each stored preference
with the current ruler of its card. Where they differ it appends
`ConsentPreferencesCleared`.

### Walker and powers

**Powers declare waivability; they never read the ledger.**
`Restriction` and `OptionRestriction` gain a field `waiver: PowerCtx =>
Option[SiteRuler]`. It defaults to not waivable and otherwise returns the
power's ruler, the granter. Campaign-only contributions never set it, which
enforces ruling 2 card by card.

The Pass sets it on its Travel `Restriction` in slice 1 and on its campaign
`OptionRestriction` in slice 2. Its "the actor rules the Pass" check stays
(ruling 5). Only the consent half of its old approximation goes.

**One ledger check in `WalkerPowerGather`.** A breach of a waivable
contribution is dropped when `answers` holds Granted for its power, the
requester, the decision and the reference:

- For an `OptionRestriction`, the reference is the option being judged.
- For a tree-level `Restriction`, it is the walk's `Answered` value for the
  decision.

**The look-ahead decides askability.** `lookAheadNoted` keeps only the first
new breach today. It will collect all new breaches. The option becomes
`DecisionOption.Askable(option, reason, granters)` when ruling 8 holds, and
stays `DecisionOption.Unavailable` otherwise. The same classification applies
on the `restrictOptions` and `firstNote` path that `OptionRestriction` uses.

**Reasons.** Each waivable contribution declares a consent-worded `NoteKey`,
used only while the option is askable. The Pass gets `no-travel-consent`:
"Cannot travel to other sites in the region without ruler's consent." A
refused option uses the ordinary reason.

**Toll and penalty steps.** In slice 4, Toll Roads, Forced Labor, Curfew and
Grasping Vines each become a `Decide` step whose free option is barred by
that power's waivable `OptionRestriction`. The step appears only when its
ruler is a player other than the actor.

**Settling a grant** runs the ordinary walker resume. The ledger check drops
the breach, so there is no special path.

### Projection

- `DecisionOptionProjection.state` gains `"askable"`, and the projection
  gains `consent: Option[ConsentAskProjection]` with the granters and powers,
  which the client uses to word the confirmation.
- While a request is pending, the requester gets `consentWaiting` ("Waiting
  for Red's consent…") with Undo offered as withdraw. Each asked granter gets
  `consentPrompt` with the requester, powers and target label. Other players
  see the usual waiting banner. `awaitedPlayers` includes the asked granters.
- `consentPreferences` goes to the viewer only: one entry per waivable power
  the viewer rules, with a row per other player. A power is waivable when any
  of its contributions declares a `waiver`.
- No other player's preferences, and nothing that marks an answer as
  automatic, leave the server.

### Frontend

- An askable option gets `OptionState.Askable` and the classes
  `board-option-askable` and `walker-choice-askable`. It uses a new amber
  colour token from DESIGN.md, is clickable and keeps the reason as its
  `title`.
- Clicking an askable option opens a client-side confirmation. Yes sends
  `RequestConsent`.
- The granter's prompt panel shows the question, the four buttons and the
  preferences notice.
- Site power labels become clickable and open `CardInspectionOverlay` with
  the power text. For a power the viewer rules, the overlay also shows the
  grid. Each change sends `SetConsentPreference` at once.

### Game Log

- Request: "Asked Red for consent to ignore Narrow Pass and select Deep
  Woods." The actor is omitted, as in other actor lines.
- Answer: "Red gave consent." or "Red gave no consent." The granter is named
  because they are off turn, and several granters may answer one request.
- Withdrawal marks the request line `takenBack`, with no "took back" group
  header.
- `LogEntry` gains `visibleTo: Option[PlayerId]`; the formatter drops such
  an entry for every other viewer. Private lines:
  - Preference set: "Red now always allows Blue to ignore Narrow Pass."
    Also "never allows" and "decides case by case whether Blue may ignore".
  - Preferences cleared: "Red's consent preferences for Narrow Pass were
    reset."

## Testing

- **Fold:** every consent event; undo putting `ConsentState` back on the
  rewound state; redo; turn expiry; replaying a journal with consent events
  reproduces the same state.
- **Walker:** askable versus unavailable for one waivable breach, a bandit
  granter, a requester who rules the power, a waivable breach mixed with one
  that is not, a remembered refusal, and Forest Paths dropping the Pass. A
  Granted entry drops the breach and the resume proceeds.
- **Application scenarios:**
  - Request flow: manual Yes, No, Always and Never; automatic answers from
    preferences.
  - Undo: after a grant, after a refusal, withdrawing a pending request.
    Reselecting a granted target; a refused target staying unavailable after
    undo.
  - Gate rejections: `ResolveWalker` while a request is pending, an answer
    from a player who was not asked, a preference for a card the granter
    does not rule.
  - `ConsentPreferencesCleared` when the ruler changes.
- **Hidden information:** Blue's projection and log are identical after an
  automatic Yes and a manual Yes. No other viewer receives the preference
  rows or the private lines.
- **Frontend:** askable rendering, the confirmation, the prompt panel and
  the grid. One browser acceptance run of the user story.
- The coverage gate applies as usual.

## Slices

1. **Machinery and the Pass on Travel.** State, events, commands, the gate,
   undo carry-over, private log lines, askable options, the confirmation,
   the prompt, the grid and the cleared event. Docs: add CR p.29 and NF p.16
   to `docs/rules/rules-reference.md`, update the "Enemies cannot" consent
   row in `docs/rules/implementation-traceability.md`, and update the
   roadmap phase.
2. **The Pass on campaign targeting.** A waiver on its `OptionRestriction`,
   through the `restrictOptions` and `firstNote` path.
3. **Hard-block cards.** Secret Police, Hall of Ministers, and Forgotten
   Vault outside campaigns.
4. **Toll and penalty cards.** Toll Roads, Forced Labor, Curfew and Grasping
   Vines, each converted to a pay-or-ask `Decide` step.

Several pending asks (ruling 9) land in the first slice where two hard
blocks from different rulers can fall on one option. Among cards still in
holding, Forbidden Forest with the Pass is the likely first case.

## Deferred

- A ruler offering consent without being asked.
- Hidden Passages ("if its ruler consents"), a positive consent on an
  unimplemented card.
- Imperial-law consent and Empire rulers. The game is all-Exile.
- Timeouts for unanswered requests, covered by the roadmap's unanswered
  off-turn decisions item.
- Hiding the speed leak of automatic answers (ruling 11).

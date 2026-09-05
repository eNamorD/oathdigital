# Typed rule resolution

Oath Digital executes component powers through explicit typed Scala behavior.
Catalog handler IDs select registered behavior; catalog `rulesText` is
documentation and is never an executable language.

## Facts, inventory, and activation

`RuleSourceIndex` is the factual inventory for the current game. It emits
stable `RuleSourceRef` identities for sites, site cards and edifice faces,
advisers, relics, banners, Foundations, legacies, and game rules together with
their declared handler IDs and state.

`CatalogHandlerInventory` independently enumerates the complete catalog
handler vocabulary and produces a stable fingerprint. Bounded modules pin or
classify that inventory so a catalog change cannot silently bypass an audit.

A command/query chooses which factual sources are active and relevant. Facedown,
remote, inaccessible, or wrong-window sources remain factual but are activated
only when the printed rule permits it. Relevant missing behavior produces a
typed unsupported violation with handler and source identity.

## Registries and ordering

`RuleResolution.scala` defines shared source/activation/query/outcome vocabulary
and deterministic ordering. Small generic modifiers use explicit registries such
as `RuntimeRuleRegistry`. Action-specific systems may own narrower registries:
Campaign plans author side/window-scoped options in
`gameplay/actions/CampaignPlans.scala`; other bounded actions use exact-ID
classifications beside their rule modules.

Order is explicit: priority, stable source key, then handler ID unless the
owning procedure defines a stricter printed order.

Terrain travel no longer runs through `RuntimeRuleRegistry` (Phase 4): the
travel path was cost-only with zero core-operation coupling, so it migrated
onto power windows. Terrain site powers under
`gameplay/powers/travel/TravelCostWindow.scala` declare typed cost facts; the
window fold computes the route (coast route, Island/Mountain destination
modifiers) from those facts, and a generic `SuppressionRegistry` expresses
"Coast ignores Island/Mountain/Pass on a coast route". Narrow Pass is a power
holding a restriction body evaluated by Travel legality against a simulated
pawn move; `RuntimeRuleRegistry` is now an empty stub retained only for
Negotiation's explicit blocking boundary, and Wake take-wealth rules live under
`gameplay/phases/TakeWealthRules.scala`.

Typed outcomes may allow or block, or report an unsupported relevant handler.
They are not generic scripts; reserved effect forms reject until both a
registered handler and a window-specific executor exist. The retired
`ModifyCost`/`RequireDecision`/`PostActionEffect` outcome forms were removed
with the travel registry path in Phase 4 — cost modification now runs through
typed power contributions instead of typed-rule outcomes.

## Commands, projection, and replay

Commands and `LegalActionProjector` call the same gameplay legality APIs.
Events record the fully resolved durable facts. Replay resolves the same
sources/handlers again and rejects changed source, order, cost, decision, or
resource result. The frontend receives only shared projection DTOs and never
runs the registry.

Campaign defender decisions can belong to a non-active player. Bandit choices
use a deterministic policy only for cost-free, choice-free registered options;
paid, ambiguous, or unsupported relevant behavior blocks.

## Window-driven power runtime

`PowerRegistry` and `PowerResolver` keep four concepts separate: factual sources
discovered by `RuleSourceIndex`, precise procedure windows, implemented typed
handlers, and reviewed powers that the pre-alpha deliberately ignores. Reviewed
definitions live under `gameplay/powers`. Their audited catalog fingerprint
makes that last category closed:
optional unimplemented handlers are neither options nor blockers, reached
mandatory/triggered handlers emit durable `IgnoredRulesRecorded` diagnostics,
and a changed handler vocabulary still rejects.

Actorless preview requests contain the expected journal position, base action
parameters, and ordered source/handler references. Preview is stateless and
does not create pending state or events. The final command carries the same
order and the application service revalidates it against the newly loaded
authoritative state. Empty option sets skip the modifier stage; the frontend
selection model nevertheless preserves click order, keyboard reordering, and
clears stale drafts when context, candidates, or preview identity changes.

The runtime is connected to Search, Campaign, Muster, Trade, Forge,
Recover, and Challenge plus Wake, Rest, card-play, and post-action windows; reviewed
Negotiation definitions are indexed for its existing explicit blocking boundary.
It does not
replace action ownership: Travel cost resolves through the TravelCost window
fold (typed terrain facts + suppression), Campaign retains its later
attacker/defender/bandit battle-plan windows, and Economy target choice remains
an explicit confirm.
`PowerRuntime` translates precise resolver results into the current command and
durable-event shapes. The legacy central classification switch has been removed;
stable request and diagnostic protocol labels live separately in
`RuleFallbackProtocol`. No component effect or universal effect language is
introduced by this layer, and modifier metadata remains descriptive only.

### Compact handler declarations

Power definitions use immutable function bundles rather than bespoke handler
classes. `PowerInspector.partial` turns a focused context pattern into a total
inspector whose unmatched facts are safely inapplicable. `PowerHandlers`
supplies automatic and player-selected declarations; procedure-specific
factories add typed preparation and replay callbacks only where needed.

Operation-backed power events still record concrete outcomes. During replay,
the owning adapter reconstructs the canonical typed operation sequence,
compares it with the recorded payload, and applies it in order. This removes
dispatch and mismatch boilerplate without turning operations into independent
events or a generic effects language.

This borrows HRF's useful compact behavior-registration style, but not its
mutable expansion dispatcher. HRF commonly routes recorded actions through a
large expansion pattern match and checks persistent effects directly inside
base procedures. Oath Digital retains precise windows, procedure ownership,
server-authored events, and replay validation instead.

Catacombs is the first executable vertical slice. Its Recover-owned inspector
checks the precise site card, pawn, secret, empty relic slot, Recover Difficulty,
Supply, and relic-deck facts. The selected invocation is revalidated before
randomness is prepared; one command atomically appends a power-owned activation
event that spends the secret and places the recorded top relic facedown, followed
by the ordinary `RecoverRolled` event that pays Supply, records defense dice, and
creates the normal pending procedure. All later retry, success, hidden choice,
and transfer behavior continues through the existing Recover procedure.

League Treaty is the corresponding Rest-owned slice. At the precise
`RestReturnFavor` window its handler inspects a faceup site source, current site
ruler, Rest actor, regional favor-bearing cards, and legal favor banks. The
ruler—not necessarily the active Rest player—owns the optional decision.
Resolution revalidates every source and amount, moves favor atomically, records
typed events, and returns control to ordered Rest hooks before ordinary cleanup.
Facedown site relics are addressed by site slot, so neither projection nor
transport exposes their identity. `RestPowerIntegration` owns resolver ordering
and continuation; the registered typed handler owns only League Treaty's
decision payload, concrete events, validation, and state effect. `RestPowers`
remains a reviewed registry and contains no procedure lifecycle or state mutation.

## Adding a power

1. Verify the exact catalog handler and printed source.
2. Confirm `RuleSourceIndex` exposes the needed factual state and
   `CatalogHandlerInventory` covers the family.
3. Declare automatic or selected windows with `PowerHandlers`; reuse one
   focused inspector when applicability is identical across windows.
4. Use the owning procedure's functional adapter for preparation and replay.
   Compose recorded semantic operations only when they represent the effect
   honestly; keep specialized decision reducers explicit.
5. Reuse the owning legality/evolution path for commands and projection.
6. Test ordering, inactive sources, replay tampering, and unsupported inventory.

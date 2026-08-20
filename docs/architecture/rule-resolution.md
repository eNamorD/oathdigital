# Typed runtime rule resolution

The engine resolves the bounded runtime powers implemented so far through
`gameplay/RuleResolution.scala`. This mechanism is deliberately not a rules-text
interpreter or a data-driven rules DSL. Catalog `handlers` remain stable IDs;
only IDs listed in `RuntimeRuleRegistry` acquire Scala behavior. Travel handler
classification uses the rulebook-aligned `TravelModifierKind` vocabulary.

## Sources and activation

`RuleSourceRef` gives stable typed identities to sites, site cards, advisers,
relics, edifices, banners, Foundations, legacies, and game-level rules. This
slice activates only the site sources needed by Travel. `PowerUseRef` remains
unchanged and continues to identify use-limited powers such as Take Wealth.

A command/query owns activation. It selects only handlers relevant to that
query from sources active in the current state, then asks the explicit registry
to resolve them. Catalog presence never activates a handler globally. A
selected handler missing from the registry produces
`UnsupportedRelevantRule`; it is not silently skipped.

## Outcomes, order, and durability

The typed vocabulary covers allow, block, additive or replacing cost changes,
a required decision boundary, post-action effects, and unsupported relevant
rules. The decision outcome is the typed seam for command-owned
`PendingProcedure` flows. Bounded Search uses a pending procedure, while its
base rules currently have no registered Search modifier.

Activations are ordered by ascending priority, stable source key, then handler
ID. Travel uses these priority bands:

1. Coast route replacement (`0`);
2. Pass restrictions (`10`);
3. Island and Mountain destination costs (`20`).

Coast's replacement route activates alone, preserving its precedence over
Pass, Island, and Mountain. Within a band, source and handler identity make the
result independent of map/container iteration order.

Commands and private projections share the same typed query functions. Events
still contain the fully resolved durable result (for example, Travel Supply
spent). Replay independently reruns the query and rejects a mismatched source,
cost, legality condition, or resource invariant. No event schema or serialized
power identity changed in this slice.

Bounded Economy activates the explicit set of Muster/Trade handler IDs from
accessible faceup advisers, the pawn site's denizens, and held faceup relics.
No Economy modifier is executable in L6d, so typed resolution reports an
unsupported relevant rule instead of interpreting catalog text or silently
applying base behavior.
Base Economy outcomes are durable v6 facts and replay recalculates their source,
suit, cost, yield, component limits, and resource movement.
The target reference carries an explicit denizen/edifice kind through projection,
HTTP, commands, and events. Edifice activation uses handlers from its current
intact or ruined face; a non-relevant ruined face remains a legal base target.

## Campaign plans

Campaign uses a narrower registered contract in
`gameplay/actions/CampaignPlans.scala`. A handler is scoped to attacker or
defender timing and authors a generic option containing its stable source,
decision owner, label, typed costs, and typed effects. Campaign itself advances
the printed windows and reduces those effects; it does not switch on component
IDs. Replay re-resolves each handler and compares the full ordered result before
applying costs or rolls.

The current effect vocabulary covers attack/defense pool additions, source
reveal, and ignored attack skulls. Typed extension forms reserve explicit
boundaries for result transforms, losing-force policy replacement, and a
suspended decision. These forms are not interpreted scripts: a registered
handler and a window-specific executor must still implement each behavior;
until then, selecting or replaying one rejects explicitly rather than silently
dropping the effect.

Player-defender decisions are authorized to the defender independently of the
active turn. Bandits have no UI: a stable policy uses every applicable
cost-free, choice-free defender option in registry order. Paid, choice-bearing,
unknown, or otherwise unsupported relevant plans block rather than being
guessed.

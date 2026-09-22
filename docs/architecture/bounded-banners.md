> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design.

# Bounded banners and Challenge

This slice implements Combined Rulebook pp. 26 and 32 and New Foundations
pp. 12 and 15 for the fixed first-game banner faces: Mob and Wandering Flame.
The typed face identities remain in state, but altered faces and mutable
Foundation selection are explicit unsupported boundaries.

`BannerRules` owns holder/resource lookup, faceup-only comparison inputs,
least-stocked favor banks and sites, and ordered resource distribution. Mob
Challenge and Campaign Raid share the deterministic one-at-a-time favor helper,
including the printed leftmost tied-bank rule. Only Wandering Flame site ties
create owner-scoped decisions. The aggregate never derives behavior from
component `rulesText`.

Challenge begins as a normal Act major action and records the prior holder,
prior resources, exact 1-Supply cost, and any forced ribbon prefix. Each tie
choice is an authoritative event. The old banner holder and resources remain
unchanged until the ribbon is complete and the challenger supplies a finite
replacement amount strictly greater than the prior amount. Completion then
applies the ribbon movements, resource payment, holder transfer, and new banner
resources atomically. Replay recalculates eligibility, forced distributions,
tie legality, payment, and every recorded terminal fact.

The Mob and Wandering Flame resource-placement powers are typed 0-Supply minor
actions. They require the active holder, a positive finite amount, and use only
favor or faceup secrets from that player's board. Altered faces, roles outside
the bounded first-game Exile state, active legacies, and changed handler
vocabularies fail through `UnsupportedBannerState`.

Banner faces, holders, and resource totals are public projections. Challenge
decisions, their legal bank/site choices, replacement bounds, and controls are
visible only to the authenticated decision owner. Authenticated HTTP intents do
not accept a player identifier; development commands retain the loopback actor
selector check. The Scala.js client reuses board targeting for declaration and
narrow bank/site/amount controls for the pending procedure.

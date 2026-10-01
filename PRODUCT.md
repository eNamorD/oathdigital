# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Experienced Oath: New Foundations players who already know the rules and the
physical components. They play in small groups whose members trust one another.
One member hosts: they run the server on their own machine and send each player
a seat link.

The same group plays in two ways, and the client must serve both:

- **Live:** everyone is online at once and plays turn by turn in real time.
- **Async:** players take turns over hours or days and come back to the game
  cold. They must see quickly what changed, whose decision is parked, and what
  they are being asked.

## Product Purpose

Oath Digital is a Scala/Scala.js implementation of Oath: New Foundations. The
server stores a database of games and enforces the rules. Players connect with
a web browser and play at a digital table.

Success means a group can play a full game without the physical box, with the
rules enforced faithfully, and without the client getting in the way of
players who already know the game.

## Positioning

A fan-made, self-hosted digital table for Oath, built for veterans. It
enforces the rules through a procedure walker that parks one decision at a
time, and it stays visually faithful to the physical components. The approach
is heavily inspired by the HRF (haunt-roll-fail) implementation of Arcs.

## Operating Context

- The host starts the server from a downloaded archive (macOS arm64, Windows
  x64, Linux x64) or a container, then creates a game and sends seat links.
  See `docs/operations/quick-start.md`.
- Seat links grant full control of a seat. There are no accounts, passwords,
  invitations, revocation, or remote administration.
- Players use a desktop web browser on the LAN or over HTTPS.
- Server mode adds developer tools to the UI: switching between players and
  inspecting raw event logs.
- Game setup is a Chronicle, shaped like the Tabletop Simulator export format,
  plus recorded shuffle orders.

## Capabilities and Constraints

- The rules engine parks at most one decision per game at a time. The viewer
  sees it on one surface: a panel in the action pane, or clickable sites on the
  world board. `CONTEXT.md` defines the product terms (parked decision, form,
  surface, subject card, roll feedback, act-action control, draft set, modifier
  flow). Future UI copy and design must use those terms.
- Rulebook terms (site, adviser, denizen, relic, Chronicle, campaign, recover)
  come from `docs/rules/glossary.yaml`.
- Current scope is the all-Exile alpha. Empire and campaign-continuity rules
  are future work (`docs/ROADMAP.md`).
- Only part of the denizen, relic and edifice catalog is implemented. Setup
  draws only implemented cards.
- Mobile support is undecided. No mobile requirement has been confirmed.

## Brand Commitments

- Name: **Oath Digital**.
- Unofficial and fan-made. Oath: New Foundations is designed by Cole Wehrle
  and published by Buried Giant Studios; this project is not affiliated with
  or endorsed by them. The MIT license covers the software only, not Buried
  Giant Studios' game design, text, or art.
- The table must read like the physical game. Suits, tokens, dice and card
  layout follow the tabletop components. The suit and token colors in
  `frontend/styles.css` were sampled from the product owner's reference images
  and then adjusted so the warm suits stay distinguishable.

## Evidence on Hand

- Rules reference, glossary, rule index and traceability: `docs/rules/`.
- Component catalog: the Scala card catalog, listed in
  `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`; the old JSON
  is kept as reference data in `docs/catalog/reference/`.
- Operations and release docs: `docs/operations/`; acceptance records:
  `docs/testing/`.
- HRF Arcs sources, as MIT-licensed reference material only:
  `vendor/haunt-roll-fail/hrf`.
- There are no testimonials, player counts, reviews or press. Do not invent
  any.

## Product Principles

1. **Faithful to the table.** The digital components read like the physical
   ones, so a veteran recognizes a suit, token or die without learning a new
   vocabulary.
2. **The parked decision is the focus.** At any moment, a viewer can tell
   whose decision is parked, what it asks, and how to answer it.
3. **Serve the cold return.** A player coming back after hours or days can
   catch up on what changed without reconstructing the game from a raw log.
4. **Expert speed over teaching.** Assume players know the rules; do not slow
   them with tutorials. Show enough context to act correctly and quickly.
5. **Trust the group.** Seat access stays simple. Avoid friction that only
   makes sense for strangers.

## Accessibility & Inclusion

Suits must stay distinguishable without color: each suit has its own glyph
shape, and color only keeps them from blurring together. No further standard
has been confirmed.

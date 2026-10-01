# Reference catalog

`new-foundations-component-catalog.json` is the last runtime catalog the game
loaded before the card classes phase (version `2026.08.29-pre5`). It is
reference only. The game no longer reads it, and it is not kept in sync.

The card data is Scala: card types in `src/main/scala/oathdigital/catalog/`,
cards not yet implemented in `catalog/holding/`, each implemented card beside
its power under `gameplay/powers/`, and the full list in
`gameplay/cards/NewFoundations.scala`.

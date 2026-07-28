package hrf.tracker4
//
//
//
//
import hrf.colmat._
import hrf.logger._
//
//
//
//

    object implicits {
        implicit val tracker = this

        trait KeyEx[K, U <: K, T] {
            val k : U
            implicit val tracker : IdentityTracker[K, T]

            def -->(l : $[T]) = {
                l.foreach { obj =>
                    if (tracker.find(obj).has(k).not)
                        throw new Error("obj " + obj + " not found at " + k)
                }

                l
            }

            def -->(obj : T) = {
                if (tracker.find(obj).has(k).not)
                    throw new Error("obj " + obj + " not found at " + k)

                obj
            }
            def -->(to : K) = tracker.get(k).foreach { u =>
                tracker.move(u, to)
            }
        }

        implicit class KEx[K, T](val k : K)(implicit val tracker : IdentityTracker[K, T]) extends KeyEx[K, K, T]

        trait ElemEx[K, T, U <: T] {
            val u : U
            implicit val tracker : IdentityTracker[K, T]

            def -->(to : K) {
                tracker.move(u, to)
            }
        }

        implicit class TEx[K, T](val u : T)(implicit val tracker : IdentityTracker[K, T]) extends ElemEx[K, T, T]

        implicit class TListEx[K, T](val list : $[T])(implicit tracker : IdentityTracker[K, T]) {
            def -->(to : K) {
                list.foreach { u =>
                    tracker.move(u, to)
                }
            }
        }
    }

class IdentityTracker[K, T] {
    private var rules : Map[K, T => Boolean] = Map()
    private var entities : $[T] = $
    private var k2e : Map[K, $[T]] = Map()
    private var e2k : Map[T, K] = Map()

    def cloned() : IdentityTracker[K, T] = {
        val t = new IdentityTracker[K, T]
        t.rules = rules
        t.entities = entities
        t.k2e = k2e
        t.e2k = e2k
        t
    }

    def copyFrom(t : IdentityTracker[K, T]) {
        rules = t.rules
        entities = t.entities
        k2e = t.k2e
        e2k = t.e2k
    }

    def all : $[T] = entities

    def keys : $[K] = rules.keys.$

    def sortBy[U : Ordering](key : K)(f : T => U) {
        val l = k2e(key)

        if (l.num > 1)
            k2e += (key -> l.sortBy(f))
    }

    def has(key : K) = rules.contains(key)

    def register(key : K, rule : T => Boolean = (e : T) => true, content : $[T] = $) : K = {
        if (rules.contains(key))
            throw new Error("key already registered " + key)

        content.foreach { e =>
            if (entities.has(e))
                throw new Error("entity already registered " + e + " at " + key)

            if (!rule(e))
                throw new Error("entity doesn't match rule " + e + " at " + key)
        }

        rules += key -> rule

        entities ++= content

        k2e += key -> content

        content.foreach(e2k += _ -> key)

        key
    }

    def find(e : T) : |[K] = e2k.get(e)

    def get(key : K) : $[T] = {
        if (rules.contains(key).not)
            throw new Error("key not registered " + key)

        k2e(key)
    }

    def move(entity : T, key : K) {
        if (entities.has(entity).not)
            throw new Error("entity not registered " + entity)

        if (rules.contains(key).not)
            throw new Error("key not registered " + key)

        if (rules(key)(entity).not)
            throw new Error("entity doesn't match rule " + entity + " at " + key)

        val old = e2k(entity)

        k2e += old -> k2e(old).but(entity)
        k2e += key -> k2e(key).add(entity)

        e2k += entity -> key
    }

    def dump() {
        +++("")
        +++("--------------------------------------------------------------------------")
        k2e.keys.foreach(k => +++("" + k + " -> " + k2e(k).mkString(" ")))
        e2k.keys.foreach(k => +++("" + k + " -> " + e2k(k)))
        +++("--------------------------------------------------------------------------")
        +++("")
    }

}

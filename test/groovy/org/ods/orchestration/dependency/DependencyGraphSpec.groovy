package org.ods.orchestration.dependency

import spock.lang.*

class DependencyGraphSpec extends Specification {

    def "add nodes"() {
        when:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])
        def g = new DependencyGraph([a, b])

        then:
        g.nodes == [a, b]
    }

    def "resolve: a -> b"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])

        when:
        a.addTo(b)

        then:
        DependencyGraph.resolve([a, b]).nodes == [b, a]
    }

    def "resolve: b -> a"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])

        when:
        b.addTo(a)

        then:
        DependencyGraph.resolve([a, b]).nodes == [a, b]
    }

    def "resolve: b -> a -> b"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])

        when:
        b.addTo(a)
        a.addTo(b)
        DependencyGraph.resolve([a, b])

        then:
        thrown(CircularDependencyException)
    }

    def "resolve: (a, b) -> c"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])
        def c = new Node([ name: "c" ])

        when:
        a.addTo(c)
        b.addTo(c)

        then:
        DependencyGraph.resolve([a, b, c]).nodes == [c, a, b]
    }

    def "resolve: (a, b) -> c, d, e -> f"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])
        def c = new Node([ name: "c" ])
        def d = new Node([ name: "d" ])
        def e = new Node([ name: "e" ])
        def f = new Node([ name: "f" ])

        when:
        a.addTo(c)
        b.addTo(c)
        e.addTo(f)

        then:
        DependencyGraph.resolve([a, b, c, d, e, f]).nodes == [c, d, f, a, b, e]
    }

    def "resolve groups: a -> b"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])

        when:
        a.addTo(b)

        then:
        DependencyGraph.resolveGroups([a, b]).nodes == [ [b], [a] ]
    }

    def "resolve groups: b -> a"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])

        when:
        b.addTo(a)

        then:
        DependencyGraph.resolveGroups([a, b]).nodes == [ [a], [b] ]
    }

    def "resolve groups: b -> a -> b"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])

        when:
        b.addTo(a)
        a.addTo(b)
        DependencyGraph.resolveGroups([a, b])

        then:
        thrown(CircularDependencyException)
    }

    def "resolve groups: (a, b) -> c"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])
        def c = new Node([ name: "c" ])

        when:
        a.addTo(c)
        b.addTo(c)

        then:
        DependencyGraph.resolveGroups([a, b, c]).nodes == [ [c], [a, b] ]
    }

    def "resolve groups: (a, b) -> c, d, e -> f"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])
        def c = new Node([ name: "c" ])
        def d = new Node([ name: "d" ])
        def e = new Node([ name: "e" ])
        def f = new Node([ name: "f" ])

        when:
        a.addTo(c)
        b.addTo(c)
        e.addTo(f)

        then:
        DependencyGraph.resolveGroups([a, b, c, d, e, f]).nodes == [ [c, d, f], [a, b, e] ]
    }
}

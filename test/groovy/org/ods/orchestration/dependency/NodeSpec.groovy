package org.ods.orchestration.dependency

import spock.lang.*

class NodeSpec extends Specification {

    def "a"() {
        when:
        def a = new Node([ name: "a" ])

        then:
        a.inDegree() == 0
        a.outDegree() == 0
        a.isIsolated()
    }

    def "a -> b"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])

        when:
        a.addTo(b)

        then:
        a.inDegree() == 0
        a.outDegree() == 1
        !a.isIsolated()
        a.hasDirectLinkTo(b)

        b.inDegree() == 1
        b.outDegree() == 0
        !b.isIsolated()
        b.hasDirectLinkTo(a)
    }

    def "(a, b) -> c -> (d, e)"() {
        given:
        def a = new Node([ name: "a" ])
        def b = new Node([ name: "b" ])
        def c = new Node([ name: "c" ])
        def d = new Node([ name: "d" ])
        def e = new Node([ name: "e" ])

        when:
        a.addTo(c)
        b.addTo(c)
        c.addTo(d)
        c.addTo(e)

        then:
        a.inDegree() == 0
        a.outDegree() == 1
        b.inDegree() == 0
        b.outDegree() == 1
        c.inDegree() == 2
        c.outDegree() == 2
        d.inDegree() == 1
        d.outDegree() == 0
        e.inDegree() == 1
        e.outDegree() == 0

        !a.hasDirectLinkTo(b)
        a.hasDirectLinkTo(c)
        !a.hasDirectLinkTo(d)
        !a.hasDirectLinkTo(e)

        !b.hasDirectLinkTo(a)
        b.hasDirectLinkTo(c)
        !b.hasDirectLinkTo(d)
        !b.hasDirectLinkTo(e)

        c.hasDirectLinkTo(a)
        c.hasDirectLinkTo(b)
        c.hasDirectLinkTo(d)
        c.hasDirectLinkTo(e)

        !d.hasDirectLinkTo(a)
        !d.hasDirectLinkTo(b)
        d.hasDirectLinkTo(c)
        !d.hasDirectLinkTo(e)
        
        !e.hasDirectLinkTo(a)
        !e.hasDirectLinkTo(b)
        e.hasDirectLinkTo(c)
        !e.hasDirectLinkTo(d)
    }
}

package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class WeakPair<U, V> {

    private final U first
    private final V second

    WeakPair(U first, V second) {
        this.first = first
        this.second = second
    }

    @NonCPS
    U getFirst() {
        return first
    }

    @NonCPS
    V getSecond() {
        return second
    }

    @Override
    // Checks specified object is "equal to" the current object or not
    boolean equals(Object o) {
        if (this == o) {
            return true
        }

        if (o == null || getClass() != o.getClass()) {
            return false
        }

        WeakPair<?, ?> pair = (WeakPair<?, ?>) o

        // call `equals()` method of the underlying objects
        if (!(first == pair.first)) {
            return false
        }
        return second == (pair.second)
    }

    @Override
    // Computes hash code for an object to support hash tables
    int hashCode() {
        // use hash codes of the underlying objects
        return 31 * first.hashCode() + second.hashCode()
    }

    @Override
    String toString() {
        return "(" + first + ", " + second + ")"
    }

}

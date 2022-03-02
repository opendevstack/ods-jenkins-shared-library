package org.ods.orchestration.util

class WeakPair<U, V> {

    private final U first;       // the first field of a pair
    private final V second;      // the second field of a pair

    // Factory method for creating a typed Pair immutable instance
    static <U, V> WeakPair<U, V> of(U a, V b) {
        // calls private constructor
        return new WeakPair<>(a, b)
    }

    // Constructs a new pair with specified values
    WeakPair(U first, V second) {
        this.first = first
        this.second = second
    }

    U getFirst() {
        return this.first
    }

    V getSecond() {
        return this.second
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

.PHONY: all
all: test

.PHONY: test
test:
	./gradlew clean test jacocoTestReport --no-daemon --stacktrace

.PHONY: doc
doc:
	./gradlew groovydoc
	go run render-adoc.go

.PHONY: docs
docs: doc

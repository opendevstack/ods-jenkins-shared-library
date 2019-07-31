.PHONY: all
all: test

.PHONY: test
test:
	./gradlew test --no-daemon --stacktrace

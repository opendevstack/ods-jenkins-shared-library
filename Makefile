.PHONY: all
all: test

.PHONY: test
test:
	./gradlew clean test jacocoTestReport --no-daemon --stacktrace

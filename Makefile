ADMIN_VERSION=1.0.22
ELECTION_VERSION=1.0.22
COUNTER_VERSION=1.0.22

default: versioncheck

clean:
	./gradlew clean

compile: build

build: clean
	./gradlew build -xtest

jars:
	./gradlew adminJar electionJar counterJar

tests:
	./gradlew check

refresh:
	./gradlew build --refresh-dependencies

build-admin:
	./gradlew adminJar
	docker build -f ./docker/admin.df -t pambrose/etcd-admin:${ADMIN_VERSION} .

push-admin:
	docker push pambrose/etcd-admin:${ADMIN_VERSION}

run-admin:
	docker run -p 8080:8080 pambrose/etcd-admin:${ADMIN_VERSION}

build-election:
	./gradlew electionJar
	docker build -f ./docker/election.df -t pambrose/etcd-election:${ELECTION_VERSION} .

push-election:
	docker push pambrose/etcd-election:${ELECTION_VERSION}

run-election:
	docker run -p 8080:8081 pambrose/etcd-election:${ELECTION_VERSION}

build-counter:
	./gradlew counterJar
	docker build -f ./docker/counter.df -t pambrose/etcd-counter:${COUNTER_VERSION} .

push-counter:
	docker push pambrose/etcd-counter:${COUNTER_VERSION}

run-counter:
	docker run -p 8080:8082 pambrose/etcd-counter:${COUNTER_VERSION}

admin:  compile build-admin push-admin

election: compile build-election push-election

counter: compile build-counter push-counter

local: compile build-admin build-election build-counter

docker: compile build-admin build-election build-counter push-admin push-election push-counter

versioncheck:
	./gradlew dependencyUpdates
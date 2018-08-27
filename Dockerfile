FROM openjdk:8

ADD https://dl.bintray.com/sbt/debian/sbt-1.2.1.deb /tmp/

RUN dpkg -i /tmp/sbt-1.2.1.deb

ADD project /src/project

ADD build.sbt /src/build.sbt

WORKDIR /src

RUN sbt compile

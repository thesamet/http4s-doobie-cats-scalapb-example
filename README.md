[![Build Status](https://travis-ci.org/thesamet/spaces.svg?branch=master)](https://travis-ci.org/thesamet/spaces)

# Spaces

Spaces is an API that allows CRUD of workspaces.

A workspace consists of a collection of environments and source repositories.

An enviroment is a collection of databases.

## Technologies

- Scala
- MySQL
- http4s
- ScalaPB

## Running locally inside Docker

This is the easiest way. Using docker-compose you will get an isolated
environment that consists of a MySQL database and a Scala API server.

1. Install docker and docker-compose

2. Clone this repository

       git clone https://github.com/thesamet/spaces

3. Start the service:

       cd spaces
       docker-compose up

Hold on for 10 minutes while it downloads and compiles - it is going to be slow for
the first time when all the dependencies are needed to be downloaded.

You will see a message that the server is ready once it is ready...

You can make edits to the Scala code and the project will get restarted after you save
(note that proto edits don't trigger a recompile).

## Running without docker

Ensure you have MySQL running.

In MySQL:

    create database spaces;

Execute the schema in sql/schema.sql inside the given databases.

Update src/main/resources/application.conf to point at the database correctly.

    brew install sbt

    sbt reStart

## Using this thing

Regardless of the way you installed it, the servier is now accepting requests
at port 8080.  Here are a few requests you can try now:

### Creating a workspace

Let's start by creating a workspace, and assign the group `g2` so we can
actually access it later:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/createWorkspace \
        -d '{"name": "MyWorkspace", "groupRefs": ["g2"]}' -D -

Note the id of the created workspace, you will need it...

### Querying the workspace

    curl -H "Authorization: Bearer t2"  -XGET http://localhost:8080/workspaces/1

### Creating an environment

Now, let's add an environment inside our workspace:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/createEnvironment \
        -d '{"name": "myenv", "workspaceId": "1"}' -D -

Take note of the environment id, you will need it....

### Linking/Unlinking a database to an environment

Each database has a `ref` that identifies it in the remote database management
service. There are a few `refs` that are hard-coded in our fake remote
database service.

To link a database to the environment we just created:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/linkDatabase \
       -d '{"workspaceId": "1", "environmentId": "2", "ref": "mysql://abc"}' -D -

Note that it appears in the response.  To unlink it:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/unlinkDatabase \
       -d '{"workspaceId": "1", "environmentId": "2", "ref": "mysql://abc"}' -D -

### Linking/Unlinking a repository to a workspace:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/linkSourceRepository \
       -d '{"ref": "git://xyz", "workspaceId": "1"}' -D -

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/unlinkSourceRepository \
       -d '{"ref": "git://xyz", "workspaceId": "1"}' -D -

## Deleting

Delete environment:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/deleteEnvironment \
        -d '{"workspaceId": "1", "environmentId": "2"}' -D -

Delete workspace:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/deleteWorkspace \
        -d '{"workspaceId": "1", "environmentId": "2"}' -D -

### Fetching sub-components:

The following routes are available to fetch only sub-components:

    /workspaces

    /workspaces/:workspaceId

    /workspaces/:workspaceId/environments/:environmentId

    /workspaces/:workspaceId/environments/:environmentId/databases/:dbRef

    /workspaces/:workspaceId/repositories/:repositoryId

## Guided tour of the code: starting points

- The request and response formats are [api.proto](https://github.com/thesamet/spaces/blob/master/src/main/protobuf/api.proto)
  also look around the [protobuf directory](https://github.com/thesamet/spaces/blob/master/src/main/protobuf)

- The [services directory](https://github.com/thesamet/spaces/tree/master/src/main/scala/spaces/services) contains abstract interfaces and concrete
  implementations.

- The main business logic is at [WorkspaceService.scala](https://github.com/thesamet/spaces/blob/master/src/main/scala/spaces/services/WorkspaceService.scala)

- Database and source repositories are added by external services which are
  modeled in
  [InfraService.scala](https://github.com/thesamet/spaces/blob/master/src/main/scala/spaces/services/InfraService.scala)

- Request handling happens here: [Api.scala](https://github.com/thesamet/spaces/blob/master/src/main/scala/spaces/api/Api.scala)

## Storage

Database schema in [schema.sql](https://github.com/thesamet/spaces/blob/master/sql/schema.sql)

The workspace is the top-level entity. Our storage module always fetch and
stores an entire workspace. This is expected to be fine since workspaces are
relatively small, and don't change often. This can be further optimized for
reads by caching the individual components, but it is more of an
implementation detail.

The workspace table layout:
- pk (primary key)
- workspace_id
- version
- serialized workspace

Each update to a workspace results in an additional row to the table. The version gets
incremented. We have a uniqueness constraint on `(workspace_id, version)`  and thus we have
optimistic concurrency control to avoid conflicts in updates.

Each component must have a single parent, so to enforce that we have an additional
table that contains `(workspace_id, componenet_id)` which has a unique key constraint.

## Authentication

The user directory contains a static list of users. To authenticate to the API
each request must include an `Authorization: brearer $api_token` header.

When a workspace is created, the user specifies which groups may have access
to the workspace. Only if a user is a member of these groups they are able to
perform any operation on it.

## Tests

The tests are in src/main/test, to run:

    sbt test

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

Hold on for 10 minutes while it downloads and compiles.  The service is set up such that
file edits will get it to recompile and build.

## Running without docker

Ensure you have MySQL running.

In MySQL:

    create database spaces;

Execute the schema in sql/schema.sql inside the given databases.

Update src/main/resources/application.conf to point at the database correctly.

    brew install sbt

    sbt run

## Using this thing

Regardless of the way you installed it, it is now accepting requests at port
8080.  Here are a few requests you can try now:

### Creating a workspace

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/createWorkspace \
        -d '{"name": "MyWorkspace", "groupRefs": ["g2"]}' -D -

Note the id of the created workspace, you will need it...

### Querying the workspace

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/workspaces/1

### Creating an environment

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/createEnvironment \
        -d '{"name": "myenv", "workspaceId": "1"}' -D -

Take note of the environment id, you will need it....

### Linking/Unlinking a database to an environment

Attach a database to the environment we just created:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/linkDatabase \
       -d '{"workspaceId": "1", "environmentId": "2", "ref": "mysql://abc"}' -D -

Unlinking it:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/unlinkDatabase \
       -d '{"workspaceId": "1", "environmentId": "2", "ref": "mysql://abc"}' -D -

### Linking/Unlinking a repostiory to a workspace:

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/linkSourceRepository \
       -d '{"ref": "git://xyz", "workspaceId": "1"}' -D -

    curl -H "Authorization: Bearer t2"  -XPOST http://localhost:8080/unlinkSourceRepository \
       -d '{"ref": "git://xyz", "workspaceId": "1"}' -D -

### Fetching sub-components:

The following routes are available to fetch only sub-components:

    /workspaces/:workspaceId

    /workspaces/:workspaceId/environments/:environmentId

    /workspaces/:workspaceId/environments/:environmentId/databases/:dbRef

    /workspaces/:workspaceId/repositories/:repositoryId

## Guided tour of the code: starting points

- The request and response formats are [https://github.com/thesamet/spaces/blob/master/src/main/protobuf/api.proto](defined here) (look also in the
  directory)

- The [https://github.com/thesamet/spaces/tree/master/src/main/scala/spaces/services](services directory) contains abstract interfaces and concrete
  implementations.

- The main business logic is at [https://github.com/thesamet/spaces/blob/master/src/main/scala/spaces/services/WorkspaceService.scala](WorkspaceService.scala)

- Database and source repositories are added by external services which are
  modeled in
  [https://github.com/thesamet/spaces/blob/master/src/main/scala/spaces/services/InfraService.scala](InfraService.scala)

- Request handling happens here: [https://github.com/thesamet/spaces/blob/master/src/main/scala/spaces/api/Api.scala](Api.scala)

## Storage

Database schema in [https://github.com/thesamet/spaces/blob/master/sql/schema.sql](schema.sql)

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

# JDBC Middleware Provider

Include `funcatron:jdbc_middleware:0.3.0-SNAPSHOT` in your
project.

Then when you enabled your Func Bundle, include the
following properties:

* `jdbc.url` -- The URL of the database
* `jdbc.classname` -- the optional classname of the JDBC driver
* `jdbc.username` -- the username of the DB
* `jdbc.password` -- the password for the DB

Check `Context.getRequestInfo().get("jdbc.connection")`
in your Func and boom... there's a pooled JDBC connection.

The connection has `autoCommit` set to false.

If the Func returns normally, the connection will
be committed. If the Func throws an exception,
the connection will be rolled back.


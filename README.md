This is about java FX not about discord. If i or somebody else used this wrong it's not the fault of
this repository but the fault of the perpetrator, since the library used has ratelimit precautions and
more crap which will most likely not allow the causing of trouble

Configscript is like:

```groovy
// config.groovy or a filename that goes in the commandline argument
token = "token"
client.log.with {
  formatter = { "[${it.level.name.toUpperCase()}] $it.by: $it.content" }
  debug.enable()
}
lolhr.customCommands.loadlogs = { String args ->
  // fills the current chat with downloaded logs
  lolhr.pane.chat?.loadLogs(args ? Integer.parseInt(args) : 50)
}
```

Available commands:

```
/eval <code>               -- evaluates groovy code
/st, /stacktrace           -- prints full stacktrace of error from /eval
/im, /implicit <command>   -- feeds the command with any input, unless the Post button is used
/ex, /explicit             -- leaves implicit state
/login [token]             -- logs in if not logged in already, using config token if no token is given
/goto <channel/guild>      -- takes an ID or a name of a channel or guild and goes to it
```
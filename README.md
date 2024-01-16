Old Discord client for bot accounts using Groovy, JavaFX and DiscordG
(made for educational purposes). Works using a config script and has
custom commands that allow further scripting. 

Configscript is like:

```groovy
// config.groovy or a filename that goes in the commandline argument
token = "token"
client.log.with {
  formatter = { "[${it.level.name.toUpperCase()}] $it.by: $it.content" }
  debug.enable()
}
lolhr.customCommands.addcommand = { String args ->
  def (name, code) = args.split(/\s+/, 2)
  lolhr.customCommands[name] = { new GroovyShell(args: it).evaluate(code) }
}
```

Available commands:

```
/eval, /e <code>           -- evaluates groovy code
/st, /stacktrace           -- prints full stacktrace of error from /eval
/im, /implicit <command>   -- feeds the command with any input, unless the Post button is used
/ex, /explicit             -- leaves implicit state
/and <cmd1> <cmd2> ...     -- runs given commands sequentially
<cmd1> & <cmd2> & ...      -- alias for /and
/or <cmd1> <cmd2> ...      -- runs given commands in parallel
<cmd1> | <cmd2> | ...      -- alias for /or

/login [token]             -- logs in if not logged in already, using config token if no token is given
/logout                    -- logs out
/goto <channel/guild>      -- takes an ID or a name of a channel or guild and goes to it
/nav, /navigation          -- switches to navigation tab
/showlog                   -- switches to log tab
/loadlogs [amount]         -- loads the given amount (default 50) of messages in the current channel
/chatlimit [limit]         -- sets the maximum number of messages (default 50, initially none) shown at a given moment.
                              limit can be "none" to remove the limit
```

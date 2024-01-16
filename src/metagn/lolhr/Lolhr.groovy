package metagn.lolhr

import groovy.transform.CompileStatic
import metagn.discordg.Client
import metagn.discordg.data.DiscordObject
import metagn.discordg.DiscordRawWSListener
import metagn.discordg.data.Snowflake
import metagn.discordg.exceptions.HTTPException
import metagn.discordg.data.Channel
import metagn.discordg.data.Guild
import metagn.discordg.data.Message
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import java.util.concurrent.atomic.AtomicBoolean

@CompileStatic
class Lolhr {
	Client client = new Client()
	HTTPException loginException
	ConfigSlurper configSlurper = new ConfigSlurper()
	ConfigObject config
	App app
	Stage stage
	MainPane pane
	String initialError
	AtomicBoolean readied = new AtomicBoolean(false)
	List<Closure> readyHooks = new ArrayList<Closure>().asSynchronized()
	List<Closure> stageHooks = new ArrayList<Closure>().asSynchronized()
	Guild guild
	Channel getChannel() { chat?.channel }
	Chat chat
	Map<Snowflake, Chat> chats = new HashMap<>()
	Map<Snowflake, Channel> guildDefaultChannels = new HashMap<>()
	Map<Snowflake, List<ChatEvent>> chatEvents = new HashMap<>()

	Lolhr() {
		configSlurper.binding = [lolhr: this, client: client, hookReady: this.&hookReady,
			hookStage: this.&hookStage, error: this.&setInitialError]
	}

	void begin(String[] args) {
		def conf = new File(args.length != 0 ? args[0] : 'config.groovy')
		// we don't really need a config
		if (false && !conf.exists()) {
			initialError = "I need a config file like config.groovy, you can supply in arguments"
			return
		}
		client.cacheMessages = false
		client.addListener(new DiscordRawWSListener() {
			void fire(String type, Map<String, Object> data) {
				if (type == 'READY') {
					readied.set(true)
					for (hook in readyHooks) hook()
				} else if (type == 'MESSAGE_CREATE') {
					final m = new Message(client, data)
					feedChat(Snowflake.swornString(data.channel_id), new MessageEvent(m))
				} else if (type == 'MESSAGE_UPDATE') {

				}
			}
		})
		client.cacheReactions = true
		client.dontRequestMembersOnReady()
		client.copyReady = false
		config = conf.exists() ? configSlurper.parse(conf.toURI().toURL()) : new ConfigObject()
		if (null != initialError) return
		if (config.autoStart) {
			try {
				if (config.token) login()
				else initialError = "Can't autostart without token"
			} catch (HTTPException ex) {
				loginException = ex
			} catch (ex) {
				ex.printStackTrace()
				new Dialog(title: "Lolhr", contentText: ex.toString()).show()
			}
		}
	}

	void feedChat(Snowflake channelId, ChatEvent event) {
		def ch = chats[channelId]
		def p = chatEvents[channelId]
		if (null == p) chatEvents[channelId] = [event]
		else p.add(event)
		if (null != ch) ch.feed(event)
	}

	void start(Stage stage) {
		if (null != initialError) {
			stage.scene = new Scene(new Label(text: initialError, alignment: Pos.CENTER), 400, 400)
			stage.show()
			return
		}

		stage.scene = new Scene(pane = new MainPane(this), 800, 600)
		stage.scene.stylesheets.add('style.css')
		if (config.stylesheets) for (x in config.stylesheets) stage.scene.stylesheets.add(x.toString())
		stage.onCloseRequest = {
			client?.logout()
			app.stop()
		}
		this.stage = stage
		stage.show()
		for (hook in stageHooks) hook(stage)
	}

	void hookReady(Closure hook) {
		if (readied.get()) hook()
		readyHooks.add(hook)
	}

	void hookStage(Closure hook) {
		if (null != stage) hook(stage)
		else stageHooks.add(hook)
	}

	void log(String msg, Color color) {
		Platform.runLater {
			pane.log.items.add(new LogEntry(message: msg, bg: color))
			pane.switchToLog()
		}
	}

	void error(String msg) {
		log msg, Color.RED
	}

	void warn(String msg) {
		log msg, Color.rgb(210, 210, 140)
	}

	void ask(String msg) {
		log msg, Color.rgb(140, 210, 140)
	}

	void info(String msg) {
		log msg, Color.rgb(162, 167, 240)
	}

	static class MultipleCommandIterator implements Iterator<String> {
		String text
		int startIndex
		List<Integer> backslashIndexes = new ArrayList<Integer>()
		boolean escaped = false
		int i

		MultipleCommandIterator(String text) {
			this.text = text
			i = startIndex = text.charAt(0) == ((char) '/') ? 1 : 0
		}

		boolean hasNext() { i < text.length() }

		String next() {
			for (; hasNext(); ++i) {
				final c = text.charAt(i)
				if (escaped) {
					if (c == ((char) '\\') || c == ((char) '/'))
						backslashIndexes.add(i - startIndex - 1)
					escaped = false
				} else {
					if (c == ((char) '/')) {
						final len = i - startIndex - backslashIndexes.size()
						def builder = new StringBuilder(len)
						def bsIter = backslashIndexes.iterator()
						def bsNext = bsIter.hasNext() ? bsIter.next() : -1
						for (int j = startIndex; j < i; ++j) {
							if (bsNext != j)
								builder.append(text.charAt(j))
							else if (bsIter.hasNext()) bsNext = bsIter.next()
						}
						startIndex = ++i
						backslashIndexes.clear()
						return builder.toString().trim()
					}
				}
			}
			final len = i - startIndex - backslashIndexes.size()
			def builder = new StringBuilder(len)
			def bsIter = backslashIndexes.iterator()
			def bsNext = bsIter.hasNext() ? bsIter.next() : -1
			for (int j = startIndex; j < i; ++j) {
				if (bsNext != j)
					builder.append(text.charAt(j))
				else if (bsIter.hasNext()) bsNext = bsIter.next()
			}
			startIndex = ++i
			backslashIndexes.clear()
			return builder.toString().trim()
		}
	}

	void runCommandsSequential(String rest) {
		def iter = new MultipleCommandIterator(rest)
		while (iter.hasNext()) runCommand(iter.next(), false)
	}

	void runCommandsParallel(String rest) {
		def iter = new MultipleCommandIterator(rest)
		while (iter.hasNext()) {
			final s = iter.next()
			Thread.start { runCommand(s, false) }
		}
	}

	def lastEvaluated
	String implicitCommand
	Throwable lastEvalThrowable
	Map<String, String> commandAliases = new HashMap<>(e: 'eval',
			im: 'implicit', ex: 'explicit', st: 'stacktrace',
			ll: 'loadlogs', cl: 'chatlimit')
	Map<String, Closure<Void>> customCommands = new HashMap<>()
	CompilerConfiguration evalConfig = new CompilerConfiguration();

	{
		def im = new ImportCustomizer()
		im.addStarImports('metagn.discordg', 'metagn.discordg.objects',
			'metagn.discordg.util', 'metagn.discordg.exceptions',
			'metagn.discordg.net', 'metagn.discorg.collections')
		im.addStarImports('metagn.lolhr')
		evalConfig.addCompilationCustomizers(im)
	}

	void runCommand(String text) {
		final slashed = text.charAt(0) == (char) '/'
		runCommand(slashed ? text.substring(1) : text, slashed)
	}

	void runCommand(String text, boolean slashed) {
		if (text.trim().empty) return
		String cmdname, rest
		if (null == implicitCommand || slashed) {
			final x = text.split(/\s+/, 2)
			cmdname = x[0]
			rest = x.length == 2 ? x[1] : null
		} else {
			cmdname = implicitCommand
			rest = text
		}
		final spsgm = commandAliases[cmdname]
		if (spsgm) cmdname = spsgm
		if (cmdname == 'and') {
			runCommandsParallel(rest)
		} else if (cmdname == 'or') {
			runCommandsSequential(rest)
		} else if (cmdname == 'nav' || cmdname == 'navigation') {
			pane.switchToNavigation()
		} else if (cmdname == 'showlog') {
			pane.switchToLog()
		} else if (cmdname == 'eval' || cmdname == 'e')
			Thread.start {
				try {
					def binding = [lolhr: this, last: lastEvaluated,
							lastException: lastEvalThrowable,
							channel: channel, channelList: pane.channels,
							guild: guild, guildList: pane.guilds,
							chat: chat, stage: stage,
							post: null == channel ? null :
									channel.&sendMessage,
							command: this.&runCommand,
							now: System.&currentTimeMillis,
							client: client, config: config]
					Platform.runLater {
						lastEvaluated = new GroovyShell(new Binding(binding), evalConfig)
								.evaluate(rest)
						info lastEvaluated.toString()
					}
				} catch (ex) {
					lastEvalThrowable = ex
					error "do /stacktrace. $ex.message"
				}
			}
		else if (cmdname == 'implicit' || cmdname == 'im')
			implicitCommand = rest
		else if (cmdname == 'explicit' || cmdname == 'ex')
			implicitCommand = null
		else if (cmdname == 'stacktrace' || cmdname == 'st')
			lastEvalThrowable.printStackTrace()
		else if (cmdname == 'goto') {
			final oldChat = chat
			DiscordObject obj
			if (null != (obj = client.channel(rest))) {
				pane.moveChat(oldChat, (Channel) obj)
			} else if (null != (obj = client.guild(rest))) {
				pane.moveChat(oldChat, guildDefaultChannels[obj.id])
			} else {
				warn "cant get where to go from \"$rest\""
			}
		}
		else if (cmdname == 'loadlogs')
			chat?.loadLogs(rest ? Integer.parseInt(rest) : 50)
		else if (cmdname == 'chatlimit')
			if (rest?.toLowerCase() == 'none')
				chat?.removeLimit()
			else
				chat?.limit(rest ? Integer.parseInt(rest) : 50)
		else if (cmdname == 'login') {
			if (rest) {
				def spl = rest.split(/\s+/, 2)
				if (spl.length == 1)
					login(spl[0])
				else if (spl.length == 2)
					login(spl[0], Boolean.parseBoolean(spl[1]))
			}
			else if (config.token)
				login()
			else error "tried to use login command with no creds?"
		}
		else if (cmdname == 'logout')
			Thread.start { client?.logout() }
		else if (customCommands.containsKey(cmdname))
			customCommands[cmdname](rest)
		else ask "what is command $cmdname?"
	}

	void login(String token = (String) config.token, boolean bot = !config.containsKey('isBot') || config.isBot) {
		if (null == token) throw new IllegalArgumentException('Supplied token for login was null')
		Thread.start {
			client.login(token, bot)
		}
	}
}

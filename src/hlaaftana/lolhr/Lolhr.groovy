package hlaaftana.lolhr

import groovy.transform.CompileStatic
import hlaaftana.discordg.Client
import hlaaftana.discordg.DiscordObject
import hlaaftana.discordg.exceptions.HTTPException
import hlaaftana.discordg.objects.Channel
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.paint.Color
import javafx.stage.Stage

import javax.swing.*
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
	List<Closure> readyHooks = [].asSynchronized()

	Lolhr() {
		configSlurper.binding = [lolhr: this, client: client]
	}

	void begin(String[] args) {
		def conf = new File(args.length != 0 ? args[0] : 'config.groovy')
		if (!conf.exists()) {
			initialError = "I need a config file like config.groovy, you can supply in arguments"
			return
		}
		client.addListener('ready') {
			readied.set(true)
			for (hook in readyHooks) hook()
		}
		config = configSlurper.parse(conf.toURI().toURL())
		if (config.autoStart) {
			try {
				if (config.token) client.login((String) config.token)
				else initialError = "I need a config file where you can put a token in"
			} catch (HTTPException ex) {
				loginException = ex
			} catch (ex) {
				ex.printStackTrace()
				JOptionPane.showMessageDialog(null, ex)
			}
		}
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
		this.stage = stage
		stage.show()
	}

	void hookReady(Closure hook) {
		if (readied.get()) hook()
		else readyHooks.add(hook)
	}

	void error(String msg) {
		pane.console.message(msg, Color.RED)
	}

	void warn(String msg) {
		pane.console.message(msg, Color.rgb(210, 210, 140))
	}

	void ask(String msg) {
		pane.console.message(msg, Color.rgb(140, 210, 140))
	}

	def lastEvaluated
	String implicitCommand
	Throwable lastEvalThrowable
	Map<String, Closure<Void>> customCommands = new HashMap<>()

	void runCommand(String text) {
		final slashed = text.charAt(0) == (char) '/'
		runCommand(slashed ? text.substring(1) : text, slashed)
	}

	void runCommand(String text, boolean slashed) {
		if (text.trim().empty) return
		String cmdname, rest
		if (null == implicitCommand || slashed) {
			final x = text.split(/\s/, 2)
			cmdname = x[0]
			rest = x.length == 2 ? x[1] : null
		} else {
			cmdname = implicitCommand
			rest = text
		}
		if (cmdname == 'eval')
			try {
				def binding = [lolhr: this, last: lastEvaluated,
					lastException: lastEvalThrowable,
					channel: pane.channel, channelList: pane.channels,
					guild: pane.guild, guildList: pane.guilds,
					chat: pane.chat, stage: stage,
					post: null == pane.channel ? null :
							pane.channel.&sendMessage,
					command: this.&runCommand,
					now: System.&currentTimeMillis]
				lastEvaluated = new GroovyShell(new Binding(binding))
						.evaluate(rest)
			} catch (ex) {
				lastEvalThrowable = ex
				error "do /stacktrace. $ex.message"
			}
		else if (cmdname == 'implicit' || cmdname == 'im')
			implicitCommand = rest
		else if (cmdname == 'explicit' || cmdname == 'ex')
			implicitCommand = null
		else if (cmdname == 'stacktrace' || cmdname == 'st')
			lastEvalThrowable.printStackTrace()
		else if (cmdname == 'goto') {
			final oldChat = pane.chat
			DiscordObject obj
			if (null != (obj = client.channel(rest))) {
				pane.moveChat(oldChat, (Channel) obj)
			} else if (null != (obj = client.guild(rest))) {
				pane.moveChat(oldChat, pane.guildDefaultChannels[obj.id])
			} else {
				warn "cant get where to go from \"$rest\""
			}
		}
		else if (cmdname == 'login') {
			if (rest) {
				Thread.start { client.login(rest) }
			} else if (config.token) Thread.start { client.login((String) config.token) }
			else error "tried to login with no creds?"
		}
		else if (cmdname == 'logout')
			Thread.start { client.logout() }
		else if (customCommands.containsKey(cmdname))
			customCommands[cmdname](rest)
		else ask "what is command $cmdname?"
	}
}

package hlaaftana.lolhr

import groovy.transform.CompileStatic
import hlaaftana.discordg.objects.Channel
import hlaaftana.discordg.objects.Guild
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.util.MiscUtil
import javafx.animation.Interpolator
import javafx.animation.Transition
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment
import javafx.scene.text.TextFlow
import javafx.util.Duration

import java.math.MathContext
import java.math.RoundingMode

@CompileStatic
class MainPane extends GridPane {
	Lolhr lolhr
	ListView<Channel> channels
	ListView<Guild> guilds
	ConsolePane console
	GridPane chatBox
	GridPane navigationBox
	Guild guild
	Channel getChannel() { chat?.channel }
	Chat chat
	Map<String, Chat> chats = [:]
	Map<String, Channel> guildDefaultChannels = [:]

	MainPane(Lolhr lolhr) {
		this.lolhr = lolhr

		channels = new ListView<>()
		channels.styleClass.add('channels')
		guilds = new ListView<>()
		guilds.styleClass.add('guilds')
		navigationBox = new GridPane()
		navigationBox.styleClass.add('navigation-box')
		console = new ConsolePane(lolhr)
		chatBox = new GridPane()
		chatBox.styleClass.add('chat-box')

		def nbrow1 = new RowConstraints()
		def nbrow2 = new RowConstraints()
		navigationBox.rowConstraints.addAll(nbrow1, nbrow2)
		navigationBox.add(guilds, 0, 0)
		navigationBox.add(channels, 0, 1)
		def nbcol1 = new ColumnConstraints()
		nbcol1.hgrow = Priority.ALWAYS
		navigationBox.columnConstraints.add(nbcol1)

		def cbrow1 = new RowConstraints()
		def cbrow2 = new RowConstraints()
		cbrow2.vgrow = Priority.ALWAYS
		cbrow1.percentHeight = 70
		chatBox.rowConstraints.addAll(cbrow1, cbrow2)
		chatBox.add(console, 0, 1)
		def cbcol1 = new ColumnConstraints()
		cbcol1.hgrow = Priority.ALWAYS
		chatBox.columnConstraints.add(cbcol1)

		lolhr.client.addListener('message') { Map data ->
			if (chats.containsKey(data.channel_id)) {
				Platform.runLater {
					final ch = chats[(String) data.channel_id]
					ch.messages.items.add((Message) data.message)
				}
			}
		}

		lolhr.hookReady {
			guilds.items.setAll(lolhr.client.guilds)
		}

		guilds.selectionModel.selectedItemProperty().addListener(new ChangeListener<Guild>() {
			void changed(ObservableValue<? extends Guild> observable, Guild oldValue, Guild newValue) {
				guild = newValue
				channels.items.setAll(newValue.channels)
			}
		})

		channels.selectionModel.selectedItemProperty().addListener(new ChangeListener<Channel>() {
			@CompileStatic
			void changed(ObservableValue<? extends Channel> observable, Channel oldValue, Channel nv) {
				final newValue = nv ?: guildDefaultChannels[guild.id] ?: guild.defaultChannel
				final oldChannel = channel
				guildDefaultChannels[newValue.guildId] = newValue
				final logs = newValue.cachedLogs
				chat = moveChat(null == oldChannel ? null : chats[oldChannel.id], newValue)
				chat.messages.items.setAll(logs)
			}
		})

		def colconst1 = new ColumnConstraints()
		def colconst2 = new ColumnConstraints()
		colconst1.percentWidth = 20
		colconst2.hgrow = Priority.ALWAYS
		columnConstraints.addAll(colconst1, colconst2)

		add(navigationBox, 0, 0)
		add(chatBox, 1, 0)
	}

	Chat addChat(Channel channel) {
		final c = new Chat(lolhr, channel)
		chats.put(channel.id, c)
		c
	}

	Chat moveChat(Chat oldChat, Channel channel) {
		if (null != oldChat) chatBox.children.remove(oldChat)
		final c = addChat(channel)
		chatBox.add(c, 0, 0)
		c
	}
}

@CompileStatic
class ConsolePane extends GridPane {
	Lolhr lolhr
	TextArea text, output
	Button post, command
	VBox buttonBox

	void message(String str, Color color) {
		println str
		final back = (Region) output.lookup('.content')
		new Transition() {
			{
				setCycleDuration(Duration.millis(800))
				setInterpolator(Interpolator.EASE_BOTH)
			}

			@Override
			protected void interpolate(double frac) {
				back.background = new Background(new BackgroundFill(
						color.interpolate(Color.WHITE, frac), CornerRadii.EMPTY, Insets.EMPTY))
			}
		}.play()
	}

	void writeToOutput(char c) {
		output.appendText(String.valueOf(c))
	}

	ConsolePane(Lolhr lolhr) {
		styleClass.add('console')
		text = new TextArea()
		text.promptText = "Jeaboru aorjoaeae gpe LOLE"
		text.styleClass.add('console-text-area')
		text.wrapText = true
		text.requestFocus()
		text.onKeyPressed = {
			final t = text.text.trim()
			if (t.empty) return
			if (it.code == KeyCode.ENTER) {
				if (it.shiftDown) {
					text.insertText(text.caretPosition, "\n")
				} else if (it.controlDown) {
					lolhr.runCommand(t)
				} else {
					if (t.charAt(0) == (char) '/')
						lolhr.runCommand(t.substring(1), true)
					else if (null != lolhr.implicitCommand)
						lolhr.runCommand(t, false)
					else if (null != lolhr.pane.channel) {
						Thread.start { lolhr.pane.channel.sendMessage(t) }
					} else return
					text.clear()
				}
				it.consume()
			}
		}
		post = new Button("Post")
		post.styleClass.add('post-button')
		post.onAction = {
			final t = text.text.trim()
			if (t.empty) return
			if (null != lolhr.pane.channel) {
				Thread.start { lolhr.pane.channel.sendMessage(t) }
				text.clear()
			}
		}
		command = new Button("Command")
		command.styleClass.add('command-button')
		command.onAction = {
			final t = text.text.trim()
			if (t.empty) return
			lolhr.runCommand(t)
			text.clear()
		}
		buttonBox = new VBox(5, post, command)
		buttonBox.padding = new Insets(3)
		buttonBox.styleClass.add('console-buttons')
		output = new TextArea()
		output.wrapText = true
		output.styleClass.add('stdout')
		final stdin = new ArrayDeque<Character>()
		output.onKeyTyped = {
			for (def x : it.text.toCharArray()) stdin.addLast(x)
		}
		final oldOut = System.out
		final oldErr = System.err
		System.out = new PrintStream(new OutputStream() {
			@CompileStatic
			void write(int b) throws IOException {
				oldOut.write(b)
				Platform.runLater {
					ConsolePane.this.writeToOutput((char) b)
				}
			}
		}, true)
		System.err = new PrintStream(new OutputStream() {
			@CompileStatic
			void write(int b) throws IOException {
				oldErr.write(b)
				Platform.runLater {
					ConsolePane.this.writeToOutput((char) b)
				}
			}
		}, true)
		System.in = new InputStream() {
			@CompileStatic
			int read() throws IOException {
				if (stdin.empty) -1
				else (int) stdin.pop().charValue()
			}
		}

		def col1 = new ColumnConstraints()
		def col2 = new ColumnConstraints(90)
		def col3 = new ColumnConstraints()
		col1.hgrow = Priority.ALWAYS
		col3.percentWidth = 30
		columnConstraints.addAll(col1, col2, col3)

		def row1 = new RowConstraints()
		row1.vgrow = Priority.ALWAYS
		rowConstraints.add(row1)

		add(text, 0, 0)
		add(buttonBox, 1, 0)
		add(output, 2, 0)
	}
}

@CompileStatic
class Chat extends GridPane {
	Lolhr lolhr
	ListView<Message> messages
	ListChangeListener<Message> pageListener
	Channel channel

	Chat(Lolhr lolhr, Channel channel) {
		this.channel = channel
		styleClass.add('chat')
		styleClass.add('chat-in-channel-name-' + channel.name)
		styleClass.add('chat-in-channel-id-' + channel.name)
		messages = new ListView<>()
		messages.styleClass.add('messages')
		messages.cellFactory = { new MessageCell() }
		def col1 = new ColumnConstraints()
		col1.hgrow = Priority.ALWAYS
		columnConstraints.add(col1)
		def row1 = new RowConstraints()
		row1.vgrow = Priority.ALWAYS
		rowConstraints.add(row1)
		add(messages, 0, 0)
	}

	void loadLogs(int num = 50) {
		final s = messages.items.size()
		def x = new Message[Math.max(s, num)]
		for (int i = 0; i < s - num; ++i) {
			x[i] = messages.items.get(i)
		}
		final logs = channel.forceRequestLogs(num).toSorted(Message.byTimestampAscending)
		final le = Math.min(0, s - num)
		for (int j = 0; j < num; ++j) {
			x[j + le] = logs.get(j)
		}
		messages.items.setAll(x)
	}

	void unpaginate() { messages.items.removeListener(pageListener) }
	void paginate(final int p) {
		messages.items.addListener(new ListChangeListener<Message>() {
			@CompileStatic
			void onChanged(ListChangeListener.Change<? extends Message> c) {
				final x = c.list.size() - p
				c.list.remove(0, x)
			}
		})
	}
}

@CompileStatic
class MessageCell extends ListCell<Message> {
	MessageCell() { setWrapText(true) }

	private static void dgt(char[] arr, int i, int n) {
		arr[i] = Character.forDigit((int) ((n % 100) / 10), 10)
		arr[i + 1] = Character.forDigit(n % 10, 10)
	}

	void updateItem(Message message, boolean empty) {
		super.updateItem(message, empty)
		if (null == message) {
			setGraphic null
			return
		}
		if (graphic != null) return
		final ts = MiscUtil.dateToLDT(message.timestamp)
		def tsstr = new char[9]
		tsstr[2] = tsstr[5] = (char) ':'
		dgt(tsstr, 0, ts.hour)
		dgt(tsstr, 3, ts.minute)
		dgt(tsstr, 6, ts.second)
		tsstr[8] = (char) ' '
		def tsn = new Text(String.valueOf(tsstr))
		tsn.textAlignment = TextAlignment.CENTER
		tsn.styleClass.add('message-timestamp')
		def author = message.getAuthor(true)
		def mem = new Text(author.toString() + ' ')
		if (author instanceof Member && !((Member) author).roleIds.empty) {
			final rgb = ((Member) author).colorValue
			if (rgb != 0)
				mem.fill = Color.rgb((rgb & 0xFF0000) >> 16,
						(rgb & 0x00FF00) >> 8, rgb & 0x0000FF)
		}
		mem.styleClass.add('message-author')
		def content = new Text(message.content)
		content.styleClass.add('message-content')
		def tf = new TextFlow(tsn, mem, content)
		tf.styleClass.add('message')
		if (!message.attachments.empty) {
			def att = new TextFlow()
			att.styleClass.add('attachments')
			def tx = new Text(" files: ")
			att.children.add(tx)
			boolean x = false
			for (attach in message.attachments) {
				if (x) att.children.add(new Text(", "))
				else x = true
				def hyper = new Hyperlink(attach.url)
				hyper.styleClass.add('attachment-url')
				def siz = new Text(" (" + toMB(attach.size) + " MB)")
				siz.styleClass.add('attachment-size')
				att.children.addAll(hyper, siz)
			}
			tf.children.add(att)
		}
		setGraphic tf
		/*
		def t = new StringBuilder("[")
		t << message.getAuthor(true) << ', ' << dgt(ts.hour) << ':' <<
				dgt(ts.minute) << ':' << dgt(ts.second) << '] ' <<
				message.content
		if (message.attachments) {
			t << ' (attached: ' << message.attachments.collect {
				def builder = new StringBuilder()
				if (it.image) {
					builder << 'image ' << it.width << 'x' << it.height << ' '
				}
				builder << it.url << ' (' << toMB(it.size) << ' MB)'
				builder.toString()
			}.join(', ') << ')'
		}
		setText t.toString()*/
		def tool = new StringBuilder("author: ")
		tool.append(message.author.unique)
		if (message.tts) tool.append(", tts")
		if (message.pinned) tool.append(", pinned")
		setTooltip new Tooltip(tool.toString())
		// TODO: add context menu
	}



	static String toMB(int bytes) {
		(bytes / 1_048_576).round(new MathContext(3, RoundingMode.HALF_UP)).toString()
	}
}

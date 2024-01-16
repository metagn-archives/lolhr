package metagn.lolhr

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import metagn.discordg.data.Permissions
import metagn.discordg.data.Snowflake
import metagn.discordg.data.Channel
import metagn.discordg.data.Guild
import metagn.discordg.data.Member
import metagn.discordg.data.Message
import metagn.discordg.util.MiscUtil
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment
import javafx.scene.text.TextFlow
import javafx.stage.Popup
import javafx.util.Callback

import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDateTime

@CompileStatic
class MainPane extends GridPane {
	Lolhr lolhr
	ListView<Channel> channels
	ListView<Guild> guilds
	ConsolePane console
	GridPane chatBox
	TabPane infoBox
	VBox navigationBox
	Tab navigationTab
	ListView<LogEntry> log
	Tab logTab

	MainPane(Lolhr lolhr) {
		this.lolhr = lolhr

		channels = new ListView<>()
		channels.styleClass.add('channels')
		guilds = new ListView<>()
		guilds.styleClass.add('guilds')
		navigationBox = new VBox()
		navigationBox.styleClass.add('navigation-box')
		log = new ListView<>()
		log.cellFactory = new Callback<ListView<LogEntry>, ListCell<LogEntry>>() {
			ListCell<LogEntry> call(ListView<LogEntry> param) {
				new LogEntry.Updater()
			}
		}
		log.styleClass.add('log')
		infoBox = new TabPane()
		infoBox.styleClass.add('info-box')
		console = new ConsolePane(lolhr)
		chatBox = new GridPane()
		chatBox.styleClass.add('chat-box')

		navigationBox.children.addAll(guilds, channels)
		navigationTab = new Tab('Navigation', navigationBox)
		navigationTab.closable = false
		navigationTab.styleClass.add('navigation-tab')
		logTab = new Tab('Log', log)
		logTab.closable = false
		logTab.styleClass.add('log-tab')
		infoBox.tabs.addAll(navigationTab, logTab)

		def cbrow1 = new RowConstraints(percentHeight: 70)
		def cbrow2 = new RowConstraints(vgrow: Priority.ALWAYS)
		chatBox.rowConstraints.addAll(cbrow1, cbrow2)
		chatBox.add(console, 0, 1)
		def cbcol1 = new ColumnConstraints()
		cbcol1.hgrow = Priority.ALWAYS
		chatBox.columnConstraints.add(cbcol1)

		lolhr.hookReady {
			Platform.runLater { guilds.items.setAll(lolhr.client.guilds) }
		}

		guilds.selectionModel.selectedItemProperty().addListener(new ChangeListener<Guild>() {
			void changed(ObservableValue<? extends Guild> observable, Guild oldValue, Guild newValue) {
				lolhr.guild = newValue
				channels.items.setAll(newValue.channels)
			}
		})

		channels.selectionModel.selectedItemProperty().addListener(new ChangeListener<Channel>() {
			@CompileStatic
			void changed(ObservableValue<? extends Channel> observable, Channel oldValue, Channel nv) {
				final newValue = nv ?: lolhr.guildDefaultChannels[lolhr.guild.id] ?: lolhr.guild.defaultChannel
				final oldChannel = lolhr.channel
				lolhr.guildDefaultChannels[newValue.guildId] = newValue
				lolhr.chat = moveChat(null == oldChannel ? null : lolhr.chats[oldChannel.id], newValue)
			}
		})

		def colconst1 = new ColumnConstraints()
		def colconst2 = new ColumnConstraints()
		colconst1.percentWidth = 20
		colconst2.hgrow = Priority.ALWAYS
		columnConstraints.addAll(colconst1, colconst2)

		add(infoBox, 0, 0)
		add(chatBox, 1, 0)
	}

	Chat addChat(Channel channel) {
		final c = new Chat(lolhr, channel)
		final events = lolhr.chatEvents[channel.id]
		if (null != events) c.messages.items.setAll(events)
		lolhr.chats.put(channel.id, c)
		c
	}

	Chat moveChat(Chat oldChat, Channel channel) {
		if (null != oldChat) chatBox.children.remove(oldChat)
		final c = addChat(channel)
		chatBox.add(c, 0, 0)
		lolhr.chat = c
		c
	}

	void switchToNavigation() {
		infoBox.selectionModel.select(navigationTab)
	}

	void switchToLog() {
		infoBox.selectionModel.select(logTab)
	}
}

@CompileStatic
class LogEntry {
	Color bg
	String message
	LocalDateTime time = LocalDateTime.now()

	static class Updater extends ListCell<LogEntry> {
		void updateItem(LogEntry item, boolean empty) {
			super.updateItem(item, empty)
			if (null == item || empty) {
				setGraphic null
				return
			}
			def ts = new Label(ChatCell.toString(item.time))
			ts.styleClass.add('log-timestamp')
			def m = new Label(' '.concat(item.message))
			m.styleClass.add('log-message')
			def tf = new TextFlow(ts, m)
			tf.styleClass.add('log-entry')
			tf.background = new Background(new BackgroundFill(item.bg, CornerRadii.EMPTY, Insets.EMPTY))
			setGraphic tf
		}
	}
}

@CompileStatic
abstract class ChatEvent {
	abstract LocalDateTime getTimestamp()
}

@CompileStatic
@TupleConstructor
class MessageEvent extends ChatEvent {
	Message message
	LocalDateTime getTimestamp() { MiscUtil.dateToLDT(message.timestamp) }
}

@CompileStatic
@TupleConstructor
class EditEvent extends ChatEvent {
	Map data
	LocalDateTime timestamp = LocalDateTime.now()
}

@CompileStatic
@TupleConstructor
class DeleteEvent extends ChatEvent {
	String id
	LocalDateTime timestamp = LocalDateTime.now()
}

@CompileStatic
class ConsolePane extends GridPane {
	Lolhr lolhr
	TextArea text, output
	Button post, command
	VBox buttonBox

	void writeToOutput(char c) {
		output.appendText(String.valueOf(c))
	}

	ConsolePane(Lolhr lolhr) {
		styleClass.add('console')
		this.lolhr = lolhr
		text = new TextArea()
		text.promptText = "Jeaboru aorjoaeae gpe LOLE"
		text.styleClass.add('text-input')
		text.wrapText = true
		text.requestFocus()
		text.onKeyPressed = new EventHandler<KeyEvent>() {
			void handle(KeyEvent it) {
				final t = text.text.trim()
				if (t.empty) return
				if (it.code == KeyCode.ENTER) {
					if (it.shiftDown) {
						text.insertText(text.caretPosition, "\n")
					} else if (it.controlDown) {
						lolhr.runCommand(t)
					} else {
						final c0 = t.charAt(0)
						boolean s2c
						if (c0 == (char) '/')
							lolhr.runCommand(t.substring(1), true)
						else if ((s2c = c0 == t.charAt(1)) && c0 == (char) '&') {
							lolhr.runCommandsSequential(t.substring(2))
						} else if (s2c && c0 == (char) '|') {
							lolhr.runCommandsParallel(t.substring(2))
						} else if (null != lolhr.implicitCommand)
							lolhr.runCommand(t, false)
						else if (null != lolhr.channel) {
							lolhr.client.discardSendMessage(content: t, lolhr.channel.id)
						} else return
						text.clear()
					}
					it.consume()
				}
			}
		}
		post = new Button("Post")
		post.styleClass.add('post-button')
		post.onAction = new EventHandler<ActionEvent>() {
			void handle(ActionEvent event) {
				final t = text.text.trim()
				if (t.empty) return
				if (null != lolhr.channel) {
					lolhr.client.discardSendMessage(content: t, lolhr.channel.id)
					text.clear()
				}
			}
		}
		command = new Button("Command")
		command.styleClass.add('command-button')
		command.onAction = new EventHandler<ActionEvent>() {
			void handle(ActionEvent event) {
				final t = text.text.trim()
				if (t.empty) return
				lolhr.runCommand(t)
				text.clear()
			}
		}
		buttonBox = new VBox(5, post, command)
		buttonBox.padding = new Insets(3)
		buttonBox.styleClass.add('console-buttons')
		output = new TextArea()
		output.styleClass.add('stdout')
		final stdin = new ArrayDeque<Character>()
		output.onKeyTyped = new EventHandler<KeyEvent>() {
			void handle(KeyEvent it) {
				for (final x : it.text.toCharArray()) stdin.addLast(x)
			}
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
	ListView<ChatEvent> messages
	ListChangeListener<ChatEvent> pageListener
	Channel channel

	Chat(Lolhr lolhr, Channel channel) {
		this.lolhr = lolhr
		this.channel = channel
		styleClass.add('chat')
		styleClass.add('chat-in-channel-name-' + channel.name)
		styleClass.add('chat-in-channel-from-' + channel.name)
		messages = new ListView<>()
		messages.styleClass.add('messages')
		messages.selectionModel = new NoSelectionModel<ChatEvent>()
		messages.cellFactory = new CellFactory()
		columnConstraints.add(new ColumnConstraints(hgrow: Priority.ALWAYS))
		rowConstraints.add(new RowConstraints(vgrow: Priority.ALWAYS))
		add(messages, 0, 0)
	}

	void loadLogs(int num = 50) {
		def itms = messages.items
		final s = itms.size()
		if (s >= num) return
		def x = new ChatEvent[Math.max(s, num)]
		final extra = num - s
		Message lastMsg = null
		def iter = itms.iterator().reverse()
		while (iter.hasNext()) {
			def next = iter.next()
			if (next instanceof MessageEvent) {
				lastMsg = ((MessageEvent) next).message
				break
			}
		}
		final logs = channel.forceRequestLogs(extra, lastMsg)
		for (int i = 0; i < logs.size(); ++i) {
			x[i] = new MessageEvent(logs.get(i))
		}
		System.arraycopy(itms.toArray(), 0, x, extra, s)
		Arrays.sort(x, new Comparator<ChatEvent>() {
			@Override
			int compare(ChatEvent o1, ChatEvent o2) {
				o1.timestamp.compareTo(o2.timestamp)
			}
		})
		lolhr.chatEvents[channel.id] = x.toList()
		itms.setAll(x)
	}

	void removeLimit() { messages.items.removeListener(pageListener) }
	void limit(final int p) {
		messages.items.addListener new ListChangeListener<ChatEvent>() {
			@CompileStatic
			void onChanged(ListChangeListener.Change<? extends ChatEvent> c) {
				final x = c.list.size() - p
				c.list.remove(0, x)
			}
		}
	}

	ListView<ChatEvent> addTemp(List<Message> messages) {
		def temp = new ListView<ChatEvent>()
		temp.cellFactory = new CellFactory()
		def msgs = new MessageEvent[messages.size()]
		for (int i = 0; i < msgs.length; ++i) {
			msgs[i] = new MessageEvent(messages.get(i))
		}
		temp.items.setAll(msgs)
		add(temp, 0, 1)
		temp
	}

	void removeTemp(ListView<ChatEvent> view) {
		children.remove(view)
	}

	void feed(ChatEvent event) {
		Platform.runLater { messages.items.add(event) }
	}

	class CellFactory implements Callback<ListView<ChatEvent>, ListCell<ChatEvent>> {
		ListCell<ChatEvent> call(ListView<ChatEvent> param) {
			new ChatCell(lolhr, param)
		}
	}
}

@CompileStatic
class ChatCell extends ListCell<ChatEvent> {
	Lolhr lolhr
	ListView<ChatEvent> view

	ChatCell(Lolhr lolhr, ListView<ChatEvent> view) {
		this.lolhr = lolhr
		this.view = view
		setWrapText(true)
	}

	private static void dgt(char[] arr, int i, int n) {
		arr[i] = Character.forDigit((int) ((n % 100) / 10), 10)
		arr[i+1] = Character.forDigit(n % 10, 10)
	}

	static String toString(LocalDateTime ts) {
		def tsstr = new char[8]
		tsstr[2] = tsstr[5] = (char) ':'
		dgt(tsstr, 0, ts.hour)
		dgt(tsstr, 3, ts.minute)
		dgt(tsstr, 6, ts.second)
		String.valueOf(tsstr)
	}

	void updateItem(ChatEvent event, boolean empty) {
		super.updateItem(event, empty)
		if (empty || null == event) {
			setGraphic null
			return
		}
		//if (graphic != null) return
		if (event instanceof MessageEvent) {
			def msg = ((MessageEvent) event).message
			setGraphic render(msg)
			setTooltip tooltip(msg)
			setContextMenu contextMenu(msg)
		}
		else if (event instanceof EditEvent) {
			final ts = event.timestamp
			def tsn = new Label(toString(ts) + ' ')
			tsn.textAlignment = TextAlignment.CENTER
			tsn.styleClass.add('message-edit-timestamp')
			def map = new HashMap(((EditEvent) event).data)
			map.remove('channel_id')
			def upm = new Label('updated message ')
			def strid = (String) map.remove('id')
			def snowid = new Snowflake(strid)
			def idn = new Hyperlink(strid)
			idn.onAction = new EventHandler<ActionEvent>() {
				void handle(ActionEvent ev) {
					for (int i = 0; i < view.items.size(); ++i) {
						final e = view.items[i]
						if (e instanceof MessageEvent && ((MessageEvent) e).message.id == snowid)
							view.scrollTo(i)
					}
				}
			}
			idn.styleClass.add('message-edit-jump')
			def builder = new StringBuilder(' ')
			final entries = map.entrySet().toList()
			for (int i = 0; i < entries.size(); ++i) {
				if (i != 0) builder.append(', ')
				final entry = entries[i]
				builder.append(entry.key).append(': ').append(entry.value.inspect())
			}
			def edn = new Label(builder.toString())
			setGraphic new TextFlow(tsn, upm, idn, edn)
		} else if (event instanceof DeleteEvent) {
			final ts = event.timestamp
			def tsn = new Label(toString(ts) + ' ')
			tsn.textAlignment = TextAlignment.CENTER
			tsn.styleClass.add('message-delete-timestamp')
			def upm = new Label('deleted message ')
			def strid = ((DeleteEvent) event).id
			def snowid = new Snowflake(strid)
			def idn = new Hyperlink(strid)
			idn.onAction = new EventHandler<ActionEvent>() {
				void handle(ActionEvent ev) {
					for (int i = 0; i < view.items.size(); ++i) {
						final e = view.items[i]
						if (e instanceof MessageEvent && ((MessageEvent) e).message.id == snowid)
							view.scrollTo(i)
					}
				}
			}
			idn.styleClass.add('message-delete-jump')
			setGraphic new TextFlow(tsn, upm, idn)
		}
	}

	Tooltip tooltip(Message message) {
		def tool = new StringBuilder("author: ")
		tool.append(message.author.unique)
		if (message.tts) tool.append(", tts")
		if (message.pinned) tool.append(", pinned")
		def tip = new Tooltip(tool.toString())
		tip.styleClass.add('message-tooltip')
		tip
	}

	ContextMenu contextMenu(Message message) {
		final isOurs = message.author.id == message.client.id
		def contxt = new ContextMenu()
		def contextHide = new MenuItem('hide')
		contextHide.onAction = new EventHandler<ActionEvent>() {
			void handle(ActionEvent it) {
				hide(message)
			}
		}
		contxt.items.add(contextHide)
		def contextFiddle = new MenuItem('fiddle')
		contextFiddle.onAction = new EventHandler<ActionEvent>() {
			void handle(ActionEvent it) {
				editMode(message, false)
			}
		}
		contxt.items.add(contextFiddle)
		if (isOurs) {
			def contextEdit = new MenuItem('edit')
			contextEdit.onAction = new EventHandler<ActionEvent>() {
				void handle(ActionEvent it) {
					editMode(message)
				}
			}
			contxt.items.add(contextEdit)
		}
		final canManage = message.channel.permissionsFor(message.client).get(Permissions.BitOffsets.MANAGE_MESSAGES)
		if (isOurs || canManage) {
			def contextDelete = new MenuItem('delete')
			contextDelete.onAction = new EventHandler<ActionEvent>() {
				void handle(ActionEvent it) {
					message.delete()
				}
			}
			contxt.items.add(contextDelete)
			def contextDeleteHide = new MenuItem('delete & hide')
			contextDeleteHide.onAction = new EventHandler<ActionEvent>() {
				void handle(ActionEvent it) {
					message.delete()
					hide(message)
				}
			}
			contxt.items.add(contextDeleteHide)
		}
		if (canManage) {
			def contextDelete = new MenuItem('pin')
			contextDelete.onAction = new EventHandler<ActionEvent>() {
				void handle(ActionEvent it) {
					message.pin()
				}
			}
			contxt.items.add(contextDelete)
		}
		contxt.items.add(new SeparatorMenuItem())
		def reactions = new MenuItem('emoji tally')
		reactions.onAction = new EventHandler<ActionEvent>() {
			void handle(ActionEvent it) {
				def popup = new Popup()
				for (reac in message.anyReactions) {
					def reactors = message.client.requestReactors(message.channel,
							message, reac.name)
					def reactf = new TextFlow()
					reactf.styleClass.add('emoji-tally-item')
					def emojitext = new Label(reac.name + ' ')
					emojitext.styleClass.add('emoji-tally-emoji')
					for (int i = 0; i < reactors.size(); ++i) {
						if (i != 0) reactf.children.add(new Text(', '))
						def user = new Label(reactors[i].unique)
						user.styleClass.add('emoji-tally-user')
						reactf.children.add(user)
					}
					popup.content.add(reactf)
				}
				popup.show(lolhr.stage)
			}
		}
		def copyid = new MenuItem('copy id')
		copyid.onAction = new EventHandler<ActionEvent>() {
			void handle(ActionEvent it) {
				Clipboard.systemClipboard.setContent(
						(DataFormat.PLAIN_TEXT): (Object) message.id.toString())
			}
		}
		contxt.items.add(copyid)
		def copytext = new MenuItem('copy text')
		copytext.onAction = new EventHandler<ActionEvent>() {
			void handle(ActionEvent it) {
				Clipboard.systemClipboard.setContent(
						(DataFormat.PLAIN_TEXT): (Object) message.content)
			}
		}
		contxt.items.add(copytext)
		contxt.styleClass.add('message-context-menu')
		contxt
	}

	TextFlow render(Message message) {
		final ts = MiscUtil.dateToLDT(message.timestamp)
		def tsn = new Label(toString(ts) + ' ')
		tsn.textAlignment = TextAlignment.CENTER
		tsn.styleClass.add('message-timestamp')
		def author = message.getAuthor(true) ?: message.author
		def mem = new Label(author.toString() + ' ')
		if (author instanceof Member && !((Member) author).roleIds.empty) {
			final rgb = ((Member) author).colorValue
			if (rgb != 0)
				mem.textFill = Color.rgb((rgb & 0xFF0000) >> 16,
						(rgb & 0x00FF00) >> 8, rgb & 0x0000FF)
		}
		mem.styleClass.add('message-author')
		def content = new Label(message.content)
		content.styleClass.add('message-content')
		final isOurs = message.author.id == message.client.id
		content.onMouseClicked = new EventHandler<MouseEvent>() {
			void handle(MouseEvent it) {
				if (isOurs && it.primaryButtonDown) editMode(message)
			}
		}
		def tf = new TextFlow(tsn, mem, content)
		tf.styleClass.add('message')
		final attachments = message.attachments
		if (!attachments.empty) {
			def att = new TextFlow()
			att.styleClass.add('attachments')
			def tx = new Label(" files: ")
			att.children.add(tx)
			boolean x = false
			for (final attach : attachments) {
				if (x) att.children.add(new Label(", "))
				else x = true
				def hyper = new Hyperlink(attach.url)
				hyper.styleClass.add('attachment-url')
				def siz = new Label(" (${toMB(attach.size)} MB)")
				siz.styleClass.add('attachment-size')
				att.children.addAll(hyper, siz)
			}
			tf.children.add(att)
		}
		final editts = message.editedAt
		if (editts) {
			def node = new Label(" edited get ")
			def nodets = new Label(toString(MiscUtil.dateToLDT(message.editedAt)))
			nodets.styleClass.add('message-edit-timestamp')
			def nodetf = new TextFlow(node, nodets)
			nodetf.styleClass.add('message-edit-text')
			tf.children.add(nodetf)
		}
		tf
	}

	static String toMB(int bytes) {
		(bytes / 1_048_576).round(new MathContext(3, RoundingMode.HALF_UP)).toString()
	}

	void editMode(Message message, boolean actuallyEdit = true) {
		def editArea = new TextArea(message.content)
		editArea.styleClass.add('edit-input')
		editArea.onKeyPressed = new EventHandler<KeyEvent>() {
			void handle(KeyEvent it) {
				final t = editArea.text.trim()
				if (t.empty) return
				if (it.code == KeyCode.ENTER) {
					if (it.shiftDown) {
						editArea.insertText(editArea.caretPosition, "\n")
					} else {
						setGraphic(render(actuallyEdit ? message.edit(t) : message))
					}
					it.consume()
				}
			}
		}
		setGraphic editArea
	}

	void hide(Message message) {
		view.items.remove(message)
	}
}

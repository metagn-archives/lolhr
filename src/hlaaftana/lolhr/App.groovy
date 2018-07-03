package hlaaftana.lolhr

import groovy.transform.CompileStatic
import javafx.application.Application
import javafx.stage.Stage

@CompileStatic
class App extends Application {
	static Lolhr lolhr = new Lolhr()

	void start(Stage stage) {
		lolhr.app = this
		lolhr.start(stage)
	}

	static void main(String[] args) {
		lolhr.begin(args)
		launch(args)
	}
}

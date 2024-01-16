package metagn.lolhr

import groovy.transform.CompileStatic
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.MultipleSelectionModel

@CompileStatic
class NoSelectionModel<T> extends MultipleSelectionModel<T> {
	ObservableList<Integer> getSelectedIndices() {
		FXCollections.emptyObservableList()
	}

	ObservableList<T> getSelectedItems() {
		FXCollections.emptyObservableList()
	}

	void selectIndices(int index, int... indices) {}
	void selectAll() {}
	void selectFirst() {}
	void selectLast() {}
	void clearAndSelect(int index) {}
	void select(int index) {}
	void select(T obj) {}
	void clearSelection(int index) {}
	void clearSelection() {}
	boolean isSelected(int index) { false }
	boolean isEmpty() { true }
	void selectPrevious() {}
	void selectNext() {}
}
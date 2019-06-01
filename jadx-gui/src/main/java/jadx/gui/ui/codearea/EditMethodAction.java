package jadx.gui.ui.codearea;

import java.awt.Point;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;

import com.lucasbaizer.Util;

import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.TextNode;
import jadx.gui.ui.ContentPanel;
import jadx.gui.ui.EditMethodDialog;
import jadx.gui.ui.MainWindow;
import jadx.gui.utils.NLS;

public class EditMethodAction extends AbstractAction implements PopupMenuListener {
	private static final long serialVersionUID = 7572960394642506454L;
	public static final int JAVA = 0;
	public static final int SMALI = 1;

	private final transient ContentPanel contentPanel;
	private final transient CodeArea codeArea;
	private final transient JClass jCls;

	private transient String determinedContent;

	public EditMethodAction(int type, ContentPanel contentPanel, CodeArea codeArea, JClass jCls) {
		super(NLS.str(type == JAVA ? "popup.edit_method_java" : "popup.edit_method_smali"));

		this.contentPanel = contentPanel;
		this.codeArea = codeArea;
		this.jCls = jCls;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (determinedContent == null) {
			return;
		}

		MainWindow mainWindow = contentPanel.getTabbedPane().getMainWindow();

		EditMethodDialog dialog = new EditMethodDialog(mainWindow, new TextNode(""), jCls, codeArea);
		dialog.setCodeAreaContent(this.determinedContent);
		dialog.setVisible(true);

		this.determinedContent = null;
	}

	@Override
	public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
		this.determinedContent = null;

		Point pos = codeArea.getMousePosition();
		if (pos != null) {
			int offset = codeArea.viewToModel(pos);
			if (offset != -1) {
				String text = codeArea.getText();
				String line = "";
				for (int i = offset; i >= 0; i--) {
					if (text.charAt(i) == '\n') {
						if (i != offset) {
							break;
						} else {
							continue;
						}
					}
					line = text.charAt(i) + line;
				}
				if (text.charAt(offset) != '\n') {
					for (int i = offset; i < text.length(); i++) {
						if (text.charAt(i) == '\n') {
							break;
						}
						line += text.charAt(i);
					}
				}
				if (line.startsWith("    ") && (line.startsWith("     ") || line.endsWith("{"))) {
					determinedContent = extractMethod(text, offset);
				}
			}
		}
		setEnabled(determinedContent != null);
	}

	private String extractMethod(String text, int offset) {
		try {
			StringBuilder extraction = new StringBuilder();

			int lines = this.codeArea.getLineCount();

			for (int i = 0; i < lines; i++) {
				int start = this.codeArea.getLineStartOffset(i);
				int end = this.codeArea.getLineEndOffset(i);

				String line = this.codeArea.getText(start, end - start);
				String str = line.trim();
				if (str.isEmpty()) {
					continue;
				}
				if (!line.startsWith("    ")) {
					if (str.startsWith("package ")) {
						str += "\n";
					} else if (str.contains("class ")) {
						str = "\n" + str;
					}

					extraction.append(str + "\n");
				}

				if (line.startsWith("    ") && !line.startsWith("     ") && str.endsWith("{")) {
					int closing = Util.findClosingBracket(text, start + line.lastIndexOf('{'));
					if (offset > start && offset < closing) {
						String method = text.substring(start, closing);
						extraction.append(method);
						extraction.append("}\n}\n");
						return extraction.toString();
					}
				}
			}

			return extraction.toString();
		} catch (BadLocationException e) {
			e.printStackTrace(System.out);
		}

		return null;
	}

	@Override
	public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
	}

	@Override
	public void popupMenuCanceled(PopupMenuEvent e) {
	}
}

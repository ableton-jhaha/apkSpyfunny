package jadx.gui.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import com.lucasbaizer.ApkSpy;
import com.lucasbaizer.ChangeCache;

import jadx.core.codegen.CodeWriter;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.codearea.AbstractCodeArea;
import jadx.gui.ui.codearea.CodeContentPanel;

public class EditMethodDialog extends JDialog {
	private static final long serialVersionUID = -8177172816914615698L;

	private AbstractCodeArea codeArea;

	public EditMethodDialog(MainWindow mainWindow, JNode jnode, JClass cls, AbstractCodeArea selectedCodeArea,
			EditParams params) {
		JPanel content = new JPanel();

		TabbedPane pane = new TabbedPane(mainWindow);
		CodeContentPanel codeArea = new CodeContentPanel(pane, jnode);
		codeArea.setPreferredSize(new Dimension(800, 600));

		JScrollPane scroll = new JScrollPane(codeArea);
		pane.add(scroll);
		content.add(pane);

		JButton save = new JButton("Save");
		save.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String completed = merge(cls, params);

				CodeWriter writer = new CodeWriter();
				writer.add(completed);
				writer = writer.finish();
				cls.getCls().getClassNode().setCode(writer);

				int caret = selectedCodeArea.getCaretPosition();
				selectedCodeArea.setText(completed);

				selectedCodeArea.setCaretPosition(caret);

				ChangeCache.putChange(cls.getFullName(), changed, head, method);

				dispose();
			}
		});
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		JButton compile = new JButton("Compile");
		compile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					ApkSpy.lint(mainWindow.getProject().getFilePath().toString(), cls.getFullName(), cls.getContent(),
							new OutputStream() {
								@Override
								public void write(int b) throws IOException {
									System.out.print((char) b);
								}
							});
				} catch (IOException | InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		});

		JPanel buttons = new JPanel();
		buttons.add(compile);
		buttons.add(save);
		buttons.add(cancel);

		add(buttons, BorderLayout.PAGE_START);
		add(content, BorderLayout.PAGE_END);

		setTitle("Edit Method");
		pack();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.MODELESS);
		setLocationRelativeTo(null);

		this.codeArea = codeArea.getCodeArea();
		this.codeArea.setEditable(true);
		this.codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);

		this.codeArea.requestFocus();
	}

	String merge(JClass cls, EditParams params) {
		StringBuilder original = new StringBuilder(cls.getCls().getClassNode().getCode().getCodeStr());
		String changed = EditMethodDialog.this.codeArea.getText();

		String method = changed.substring(changed.indexOf("    "), changed.lastIndexOf('}') - 1);
		original.delete(params.methodStart, params.methodEnd + 1);
		original.insert(params.methodStart, method);

		String head = changed.substring(0, changed.substring(0, changed.indexOf("class ")).lastIndexOf('\n'));
		original.delete(params.headStart, params.headEnd);
		original.insert(params.headStart, head);

		return original.toString();
	}

	public void setCodeAreaContent(String content) {
		codeArea.setText(content);
	}

	public static class EditParams {
		public int headStart;
		public int headEnd;
		public int methodStart;
		public int methodEnd;

		public EditParams(int headStart, int headEnd, int methodStart, int methodEnd) {
			this.headStart = headStart;
			this.headEnd = headEnd;
			this.methodStart = methodStart;
			this.methodEnd = methodEnd;
		}
	}
}

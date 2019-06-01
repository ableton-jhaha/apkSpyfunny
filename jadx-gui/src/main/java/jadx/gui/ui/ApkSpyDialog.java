package jadx.gui.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import jadx.gui.treemodel.JNode;
import jadx.gui.ui.codearea.AbstractCodeArea;
import jadx.gui.ui.codearea.CodeContentPanel;

public abstract class ApkSpyDialog extends JDialog {
	private static final long serialVersionUID = 9189067693904974918L;

	private transient MainWindow mainWindow;
	private transient AbstractCodeArea codeArea;
	private transient JTextArea output;

	public ApkSpyDialog(MainWindow mainWindow, JNode jnode, String title) {
		super(SwingUtilities.windowForComponent(mainWindow));

		this.mainWindow = mainWindow;

		JPanel content = new JPanel();

		TabbedPane pane = new TabbedPane(mainWindow);
		CodeContentPanel codeArea = new CodeContentPanel(pane, jnode);
		codeArea.setPreferredSize(new Dimension(800, 600));

		JScrollPane scroll = new JScrollPane(codeArea);
		pane.add(scroll);
		content.add(pane);

		this.output = new JTextArea();

		JScrollPane scroll2 = new JScrollPane(output);
		Dimension size = scroll.getPreferredSize();
		size.width /= 2;
		scroll2.setPreferredSize(size);
		content.add(scroll2);

		JButton save = new JButton("Save");
		save.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onSave();
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
				output.setText("");

				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						onCompile();
					}
				});
				thread.start();
			}
		});

		JPanel buttons = new JPanel();
		buttons.add(compile);
		buttons.add(save);
		buttons.add(cancel);

		add(buttons, BorderLayout.PAGE_START);
		add(content, BorderLayout.PAGE_END);

		setTitle(title);
		pack();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.APPLICATION_MODAL);
		setModal(true);
		setLocationRelativeTo(null);

		this.codeArea = codeArea.getCodeArea();
		this.codeArea.setEditable(true);
		this.codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);

		this.codeArea.requestFocus();
	}

	protected abstract void onCompile();

	protected abstract void onSave();

	public void setCodeAreaContent(String content) {
		codeArea.setText(content);
	}

	public AbstractCodeArea getCodeArea() {
		return this.codeArea;
	}

	public MainWindow getMainWindow() {
		return this.mainWindow;
	}

	public JTextArea getOutput() {
		return this.output;
	}
}
